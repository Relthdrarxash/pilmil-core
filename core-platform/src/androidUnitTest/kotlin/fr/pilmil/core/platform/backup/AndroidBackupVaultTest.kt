package fr.pilmil.core.platform.backup

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidBackupVaultTest {

    // 2 000 itérations pour une exécution ultra-rapide des tests unitaires
    private val vault = AndroidBackupVault(iterations = 2_000)
    private val passphrase = "PhraseSecrèteForte&Unique2026!".toCharArray()

    @Test
    fun testEncryptAndDecryptRoundTrip() {
        val payload = """{"app":"lunaison","version":1,"records":[{"cycle":28,"flow":"light"}]}"""
        val container = vault.encrypt(payload, passphrase)

        // Vérification de l'en-tête
        assertTrue(container.size >= AndroidBackupVault.MIN_CONTAINER_SIZE)
        val magic = container.copyOfRange(0, 6).toString(Charsets.US_ASCII)
        assertEquals("PILMIL", magic)
        assertEquals(1.toByte(), container[6])

        // Déchiffrement réussi
        val decrypted = vault.decrypt(container, passphrase)
        assertEquals(payload, decrypted)
    }

    @Test
    fun testDecryptionFailsWithWrongPassphrase() {
        val payload = """{"app":"sommelle","diaryEntries":7}"""
        val container = vault.encrypt(payload, passphrase)

        val wrongPassphrase = "MotDePasseIncorrect!".toCharArray()
        assertFailsWith<InvalidPassphraseException> {
            vault.decrypt(container, wrongPassphrase)
        }
    }

    @Test
    fun testDecryptionFailsWithTamperedCiphertext() {
        val payload = """{"sensitiveHealthData":"confidential"}"""
        val container = vault.encrypt(payload, passphrase)

        // Altération d'un seul octet dans la charge utile chiffrée
        container[container.size - 5] = (container[container.size - 5].toInt() xor 0xFF).toByte()

        assertFailsWith<InvalidPassphraseException> {
            vault.decrypt(container, passphrase)
        }
    }

    @Test
    fun testDecryptionFailsWithInvalidMagic() {
        val payload = "data"
        val container = vault.encrypt(payload, passphrase)

        container[0] = 'B'.code.toByte()
        container[1] = 'A'.code.toByte()
        container[2] = 'D'.code.toByte()

        assertFailsWith<CorruptedBackupException> {
            vault.decrypt(container, passphrase)
        }
    }

    @Test
    fun testDecryptionFailsWithUnsupportedVersion() {
        val payload = "data"
        val container = vault.encrypt(payload, passphrase)

        container[6] = 99.toByte()

        assertFailsWith<CorruptedBackupException> {
            vault.decrypt(container, passphrase)
        }
    }

    @Test
    fun testDecryptionFailsWithTruncatedContainer() {
        val truncated = ByteArray(30) { 0 }
        assertFailsWith<CorruptedBackupException> {
            vault.decrypt(truncated, passphrase)
        }
    }

    @Test
    fun testEmptyPassphraseThrowsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> {
            vault.encrypt("data", charArrayOf())
        }
        assertFailsWith<IllegalArgumentException> {
            vault.decrypt(ByteArray(60), charArrayOf())
        }
    }

    @Test
    fun testLargePayloadCompressionEfficiency() {
        // Répétition de données typiques JSON pour tester la compression GZIP
        val largePayload = (1..100).joinToString(",", prefix = "[", postfix = "]") {
            """{"id":$it,"timestamp":"2026-11-06T20:00:00Z","metric":"heart_rate_variability","value":65.4}"""
        }
        val uncompressedSize = largePayload.toByteArray(Charsets.UTF_8).size
        val container = vault.encrypt(largePayload, passphrase)

        // Le conteneur complet (en-tête + chiffré) doit être significativement plus petit que le JSON brut
        assertTrue(container.size < uncompressedSize, "Le conteneur (${container.size} octets) devrait être plus petit que le JSON brut ($uncompressedSize octets)")

        val restored = vault.decrypt(container, passphrase)
        assertEquals(largePayload, restored)
    }

    // Fabrique indépendamment un conteneur v1 (si [gzip] = false, [raw] est chiffré tel quel au lieu d'être compressé)
    private fun buildContainer(raw: ByteArray, iterations: Int, gzip: Boolean = true): ByteArray {
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 100).toByte() }
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(passphrase, salt, iterations, 256)).encoded
        val plain = if (gzip) {
            ByteArrayOutputStream().also { bos -> GZIPOutputStream(bos).use { it.write(raw) } }.toByteArray()
        } else raw
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(byteArrayOf(*AndroidBackupVault.MAGIC, AndroidBackupVault.VERSION))
        val ct = cipher.doFinal(plain)
        return ByteBuffer.allocate(AndroidBackupVault.HEADER_LENGTH + ct.size)
            .put(AndroidBackupVault.MAGIC).put(AndroidBackupVault.VERSION).put(salt)
            .putInt(iterations).put(iv).put(ct).array()
    }

    @Test
    fun testHeaderTruncatedJustBelowMinimum() {
        val container = vault.encrypt("data", passphrase)
        assertFailsWith<CorruptedBackupException> {
            vault.decrypt(container.copyOf(AndroidBackupVault.MIN_CONTAINER_SIZE - 1), passphrase)
        }
    }

    @Test
    fun testIterationsOutOfBounds() {
        for (bad in listOf(0, 999, 2_000_001, -1)) {
            val container = vault.encrypt("data", passphrase)
            ByteBuffer.wrap(container).putInt(23, bad)
            assertFailsWith<CorruptedBackupException>("itérations = $bad") {
                vault.decrypt(container, passphrase)
            }
        }
    }

    @Test
    fun testTamperedHeaderFieldsAreRejected() {
        // Sel, itérations (valides) et IV altérés : l'authentification échoue
        for (index in listOf(7, 26, 27)) {
            val container = vault.encrypt("data", passphrase)
            container[index] = (container[index].toInt() xor 0x01).toByte()
            assertFailsWith<InvalidPassphraseException>("octet $index") {
                vault.decrypt(container, passphrase)
            }
        }
    }

    @Test
    fun testInvalidGzipPayloadIsCorrupted() {
        val container = buildContainer("pas du gzip".toByteArray(), iterations = 2_000, gzip = false)
        assertFailsWith<CorruptedBackupException> {
            vault.decrypt(container, passphrase)
        }
    }

    @Test
    fun testOversizedDecompressedPayloadIsRejected() {
        val limited = AndroidBackupVault(iterations = 2_000, maxDecompressedSize = 1024)
        val container = buildContainer(ByteArray(4 * 1024), iterations = 2_000)
        assertFailsWith<CorruptedBackupException> {
            limited.decrypt(container, passphrase)
        }
    }

    @Test
    fun testEmptyPayloadRoundTrip() {
        assertEquals("", vault.decrypt(vault.encrypt("", passphrase), passphrase))
    }

    @Test
    fun testDecryptsLegacyContainerAt100kIterations() {
        val payload = """{"legacy":true}"""
        val container = buildContainer(payload.toByteArray(), iterations = 100_000)
        // Le coffre courant lit les itérations dans l'en-tête, quelle que soit sa valeur par défaut
        assertEquals(payload, AndroidBackupVault().decrypt(container, passphrase))
    }

    @Test
    fun testDefaultIterationsAreStoredInHeader() {
        val container = AndroidBackupVault().encrypt("data", passphrase)
        assertEquals(AndroidBackupVault.DEFAULT_ITERATIONS, ByteBuffer.wrap(container).getInt(23))
    }
}
