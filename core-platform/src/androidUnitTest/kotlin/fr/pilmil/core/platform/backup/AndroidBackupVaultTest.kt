package fr.pilmil.core.platform.backup

import org.junit.Test
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
}
