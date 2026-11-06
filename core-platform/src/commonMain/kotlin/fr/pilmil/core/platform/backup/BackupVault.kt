package fr.pilmil.core.platform.backup

/**
 * Exception racine pour les opérations de sauvegarde et restauration locale.
 */
open class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Levée lorsque le conteneur binaire n'a pas la signature magique, la version attendue
 * ou présente une structure de fichier tronquée / corrompue.
 */
class CorruptedBackupException(message: String, cause: Throwable? = null) : BackupException(message, cause)

/**
 * Levée lorsque la phrase secrète est erronée ou que le tag d'authentification
 * cryptographique AES-GCM échoue (altération des données ou tentative d'usurpation).
 */
class InvalidPassphraseException(
    message: String = "Mot de passe incorrect ou données altérées",
    cause: Throwable? = null,
) : BackupException(message, cause)

/**
 * Contrat du coffre-fort de sauvegarde locale chiffrée (.pilmil).
 * 100% hors-ligne, sans dépendance tierce, avec chiffrement authentifié AES-256-GCM.
 */
interface BackupVault {
    /**
     * Chiffre la charge utile [payloadUtf8] avec la phrase secrète [passphrase].
     * @return Conteneur binaire complet (.pilmil).
     */
    fun encrypt(payloadUtf8: String, passphrase: CharArray): ByteArray

    /**
     * Déchiffre le conteneur binaire [container] avec la phrase secrète [passphrase].
     * @return La charge utile UTF-8 restaurée.
     * @throws CorruptedBackupException en cas d'en-tête ou structure invalide.
     * @throws InvalidPassphraseException si le mot de passe est faux ou le contenu altéré.
     */
    fun decrypt(container: ByteArray, passphrase: CharArray): String
}
