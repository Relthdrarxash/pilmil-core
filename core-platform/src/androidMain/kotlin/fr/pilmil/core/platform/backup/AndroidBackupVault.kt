package fr.pilmil.core.platform.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implémentation Android & JVM du conteneur de sauvegarde chiffrée .pilmil.
 *
 * Spécification du format binaire (.pilmil v1) :
 * - Octets 0..5   : "PILMIL" (ASCII, 6 octets)
 * - Octet 6       : Version (0x01)
 * - Octets 7..22  : Sel cryptographique KDF (16 octets aléatoires)
 * - Octets 23..26 : Itérations PBKDF2 (4 octets, entier grand-boutiste, standard 600 000 ; les anciens conteneurs à 100 000 restent lisibles)
 * - Octets 27..38 : Vecteur d'initialisation GCM (12 octets aléatoires)
 * - Octets 39..N  : Charge utile chiffrée AES-256-GCM + tag d'authentification 128 bits
 *
 * La charge utile est compressée en GZIP avant chiffrement pour minimiser la taille
 * et réduire l'entropie en texte clair.
 */
class AndroidBackupVault(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val maxDecompressedSize: Int = MAX_DECOMPRESSED_SIZE,
) : BackupVault {

    companion object {
        val MAGIC: ByteArray = "PILMIL".toByteArray(Charsets.US_ASCII)
        const val VERSION: Byte = 1
        const val DEFAULT_ITERATIONS: Int = 600_000
        const val SALT_LENGTH: Int = 16
        const val IV_LENGTH: Int = 12
        const val KEY_LENGTH_BITS: Int = 256
        const val GCM_TAG_LENGTH_BITS: Int = 128
        const val HEADER_LENGTH: Int = 6 + 1 + SALT_LENGTH + 4 + IV_LENGTH
        /** Plafond de la charge utile décompressée (protection contre les bombes GZIP). */
        const val MAX_DECOMPRESSED_SIZE: Int = 50 * 1024 * 1024
        const val MIN_ITERATIONS: Int = 1_000
        const val MAX_ITERATIONS: Int = 2_000_000
        const val CHUNK_SIZE: Int = 8 * 1024
        const val MIN_CONTAINER_SIZE: Int = HEADER_LENGTH + (GCM_TAG_LENGTH_BITS / 8)
    }

    override fun encrypt(payloadUtf8: String, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "La phrase secrète ne peut pas être vide" }

        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }

        // 1. Dérivation de la clé AES-256
        val secretKey = deriveKeyOrFail(passphrase, salt, iterations)

        // 2. Compression préalable de la charge utile (GZIP)
        val compressed = compressGzip(payloadUtf8.toByteArray(Charsets.UTF_8))

        // 3. Chiffrement AES-256-GCM avec AAD liant l'en-tête (Magic + Version)
        val ciphertext = try {
            cipher(Cipher.ENCRYPT_MODE, secretKey, iv).doFinal(compressed)
        } catch (e: GeneralSecurityException) {
            throw BackupException("Chiffrement de la sauvegarde impossible", e)
        }

        // 4. Assemblage du conteneur binaire (.pilmil)
        val output = ByteBuffer.allocate(HEADER_LENGTH + ciphertext.size)
        output.put(MAGIC)
        output.put(VERSION)
        output.put(salt)
        output.putInt(iterations)
        output.put(iv)
        output.put(ciphertext)

        return output.array()
    }

    override fun decrypt(container: ByteArray, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "La phrase secrète ne peut pas être vide" }

        if (container.size < MIN_CONTAINER_SIZE) {
            throw CorruptedBackupException("Taille de fichier insuffisante pour un conteneur .pilmil valide (${container.size} octets)")
        }

        val buffer = ByteBuffer.wrap(container)

        // 1. Vérification de la signature magique
        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) {
            throw CorruptedBackupException("Signature magique invalide : le fichier n'est pas une sauvegarde .pilmil reconnue")
        }

        // 2. Vérification de la version
        val version = buffer.get()
        if (version != VERSION) {
            throw CorruptedBackupException("Version de sauvegarde non supportée : $version (version attendue : $VERSION)")
        }

        // 3. Extraction du sel, du nombre d'itérations et du nonce IV
        val salt = ByteArray(SALT_LENGTH).also { buffer.get(it) }
        val iter = buffer.int
        if (iter !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw CorruptedBackupException("Nombre d'itérations KDF suspect ou invalide : $iter (attendu entre $MIN_ITERATIONS et $MAX_ITERATIONS)")
        }
        val iv = ByteArray(IV_LENGTH).also { buffer.get(it) }

        // 4. Extraction du texte chiffré
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

        // 5. Dérivation et déchiffrement AES-256-GCM
        val secretKey = deriveKeyOrFail(passphrase, salt, iter)
        val cipher = try {
            cipher(Cipher.DECRYPT_MODE, secretKey, iv)
        } catch (e: GeneralSecurityException) {
            throw BackupException("Initialisation du déchiffrement impossible", e)
        }

        val compressed = try {
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw InvalidPassphraseException("Échec d'authentification : mot de passe incorrect ou sauvegarde altérée", e)
        }

        // 6. Décompression GZIP
        val decompressed = try {
            decompressGzip(compressed)
        } catch (e: IOException) {
            throw CorruptedBackupException("Décompression de la charge utile impossible", e)
        }

        return String(decompressed, Charsets.UTF_8)
    }

    /** Chiffre initialisé avec l'AAD (Magic + Version) qui lie l'en-tête à l'authentification. */
    private fun cipher(mode: Int, key: SecretKeySpec, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").also {
            it.init(mode, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            it.updateAAD(byteArrayOf(*MAGIC, VERSION))
        }

    /** Convertit toute erreur inattendue de dérivation en [BackupException] du module. */
    private fun deriveKeyOrFail(passphrase: CharArray, salt: ByteArray, iter: Int): SecretKeySpec =
        try {
            deriveKey(passphrase, salt, iter)
        } catch (e: Exception) {
            throw BackupException("Dérivation de la clé impossible", e)
        }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iter: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, iter, KEY_LENGTH_BITS)
        try {
            val rawKey = factory.generateSecret(spec).encoded
            return SecretKeySpec(rawKey, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun compressGzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun decompressGzip(compressed: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_SIZE)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                if (out.size() + read > maxDecompressedSize) {
                    throw CorruptedBackupException("Charge utile décompressée supérieure à $maxDecompressedSize octets")
                }
                out.write(chunk, 0, read)
            }
        }
        return out.toByteArray()
    }
}
