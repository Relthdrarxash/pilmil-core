# Applications Pilmil sur iPhone et mutualisation du code

Document de décision. État au 8 novembre 2026.

## 1. Résumé de la décision

1. Chaque appli est portée sur iPhone avec Kotlin Multiplatform (KMP) et Compose Multiplatform, à l'intérieur de son propre dépôt.
2. Ordre de portage : Sauve (pilote en cours), Sommelle, Lunaison, Sillon, puis Respire sous une forme réduite.
3. Widgets, notifications locales, Siri et Screen Time restent écrits en natif iOS (Swift ou appels Kotlin vers les API Apple).
4. Le code commun entre applis reste un petit noyau stable (`pilmil-core`) : pas de design system unique.
5. `pilmil-core` est intégré par sous-module Git et build composite, appli par appli, au moment de son portage iOS. Avant toute publication, le propriétaire doit trancher la question GPL-3.0 et App Store.

## 2. Approche technique retenue

### KMP et Compose Multiplatform, appli par appli

Les cinq applis sont déjà écrites en Kotlin avec Jetpack Compose. Compose Multiplatform reprend la même API (`androidx.compose.*`) sur iOS. Une grande partie du code se déplace donc d'un dossier `src/main` vers `src/commonMain` sans réécriture.

Le partage se fait d'abord entre Android et iOS à l'intérieur d'une appli. Chaque appli garde son dépôt, sa version, son rythme de publication.

Alternatives écartées :

- **Swift natif (SwiftUI)** : il faudrait réécrire toute la logique métier (protocole TCC-I, règles du cycle, catalogues du potager, cadence du massage cardiaque) et la maintenir deux fois. Deux bases de code divergent vite, surtout pour une petite équipe.
- **Flutter** : réécriture complète en Dart, y compris sur Android. Aucun code existant n'est réutilisé. Une seule équipe Kotlin devrait apprendre un second écosystème.

KMP permet aussi d'écrire du code iOS en Kotlin (`iosMain`) qui appelle directement les API Apple (UIKit, AVFoundation, UserNotifications). On n'écrit du Swift que là où Apple l'impose.

### Ce qui reste forcément natif côté iOS

| Fonction | Android aujourd'hui | iOS | Langage imposé |
|---|---|---|---|
| Widget d'écran d'accueil | `AppWidgetProvider` + `RemoteViews` | WidgetKit | Swift + SwiftUI (extension séparée) |
| Notifications locales, rappels | WorkManager, `NotificationManager` | `UNUserNotificationCenter` | Kotlin `iosMain` possible |
| Raccourcis | `ShortcutManager`, `shortcuts.xml` | App Intents, App Shortcuts (Siri) | Swift |
| Blocage d'applis | `AccessibilityService` | Screen Time API | Swift (extensions) |
| Synthèse vocale | `TextToSpeech` | `AVSpeechSynthesizer` | Kotlin `iosMain` possible |
| Retour haptique | `Vibrator` | `UIImpactFeedbackGenerator` | Kotlin `iosMain` possible |

Les extensions iOS (widget, Screen Time) sont des cibles Xcode distinctes. Elles ne peuvent pas contenir d'interface Compose. Elles lisent les données partagées via un App Group.

## 3. Faisabilité par appli

### Méthode de mesure

Les chiffres viennent des dépôts locaux, branche `ccr-d081b1a7-9nnij4`, sources principales hors tests. Commande utilisée, par module :

```
find <module> -path '*/src/*' -name '*.kt' (hors tests) | wc -l
grep -qE '^import android\.'          # fichier lié à Android
grep -qE '^import (android|androidx)\.' # fichier sans aucune dépendance AndroidX
```

« Sans `android.*` » compte les lignes des fichiers qui n'importent rien de `android.*`. Ces fichiers peuvent importer `androidx.compose` (portable) mais aussi `androidx.room` ou `androidx.lifecycle` (à migrer). C'est donc une borne haute de ce qui se déplace sans effort. « Kotlin pur » compte les fichiers sans aucun import `android.*` ni `androidx.*` : ils passent en `commonMain` tels quels.

| Appli | Lignes Kotlin | Sans `android.*` | Kotlin pur | Modules les plus portables |
|---|---:|---:|---:|---|
| Sauve | 3 262 | 2 895 (89 %) | 333 | `shared/commonMain` (déjà KMP) |
| Sommelle | 7 576 | 4 435 (58 %) | 2 577 | `core:presentation` 1 839, `core:protocol` 489, `core:model` 153 |
| Lunaison | 13 602 | 9 996 (73 %) | 5 128 | `core:content` 2 790, `core:cycle` 1 085, `core:model` 441 |
| Sillon | 7 371 | 6 113 (82 %) | 3 839 | catalogues (`VegetableCatalog` 1 935, `DiseaseCatalog` 492) |
| Respire | 4 208 | 1 796 (42 %) | 185 | statistiques et écrans de réglages |

Pour Sauve, les chiffres sont ceux du commit `47dd3f3` (2 886 lignes en `commonMain`, 9 en `androidMain`, 367 dans `app`).

### Dépendances Android lourdes relevées

| Appli | Dépendances à remplacer |
|---|---|
| Sauve | WorkManager, widget SOS (`RemoteViews`), raccourcis, `TextToSpeech`, SMS par `smsto:` |
| Sommelle | Room 2.6.1, WorkManager, widget, `FileProvider`, export PDF par `android.graphics.pdf`, `TextToSpeech` |
| Lunaison | Room 2.6.1, WorkManager, widget, NFC HCE (`LunaisonHceService`, 84 lignes), CameraX + ZXing pour le QR (`QrCode.kt`, 555 lignes), SMS |
| Sillon | WorkManager, LiteRT (`PhotoClassifier`), CameraX / `FileProvider`, `SharedPreferences` |
| Respire | `AccessibilityService` (`PauseService` 235 lignes, `ShortFormBlocker` 216 lignes), WorkManager, widget |

### Tableau de synthèse

| Ordre | Appli | Effort estimé | Bloquants | Remarques |
|---:|---|---|---|---|
| 1 | Sauve | 2 à 3 semaines-personne | Widget SOS en WidgetKit, raccourcis en App Shortcuts | Pilote en cours. Peu d'état persistant. |
| 2 | Sommelle | 5 à 7 semaines-personne | Migration Room vers Room KMP (2.7 ou plus), export PDF à réécrire pour iOS | Logique TCC-I déjà en Kotlin pur. |
| 3 | Lunaison | 6 à 8 semaines-personne | Room KMP, lecture QR par caméra, partage NFC | Plus gros volume de code, mais 73 % sans `android.*`. |
| 4 | Sillon | 4 à 6 semaines-personne | Caméra, modèle LiteRT | Diagnostic photo optionnel : peut sortir après la v1 iOS. |
| 5 | Respire | 4 à 6 semaines-personne, plus le délai Apple | Fonction centrale impossible sur iOS (voir ci-dessous) | Produit différent sur iPhone. À décider après les quatre autres. |

Total indicatif : 21 à 30 semaines-personne. Ces estimations supposent une personne qui connaît déjà le code Android. Elles incluent tests sur appareil et préparation de la fiche App Store.

### Sauve

Le pilote est en cours sur la branche `ccr-d081b1a7-9nnij4` du dépôt sauve (commit « Module shared multiplateforme »). Sauve y passe en Kotlin Multiplatform avec Compose Multiplatform 1.8.2 et Kotlin 2.1.21. Le module `shared` contient les écrans communs, les contenus, le métronome de massage cardiaque et l'interface `SauvePlatform`.

La partie iOS est en cours d'écriture : `AVSpeechSynthesizer`, `UIImpactFeedbackGenerator`, `UNUserNotificationCenter`, coque iOS générée par XcodeGen et compilée sur un runner macOS GitHub Actions. Elle n'est pas encore validée. Ce document ne suppose pas qu'elle fonctionne.

Sur iPhone, l'appel au 15 ou au 112 passe par une URL `tel:`. L'envoi de SMS passe par `MFMessageComposeViewController` : l'utilisateur doit appuyer sur Envoyer, iOS n'autorise pas l'envoi silencieux.

### Sommelle

Le protocole TCC-I et la présentation (`core:model`, `core:protocol`, `core:presentation`) sont déjà en Kotlin pur : 2 481 lignes qui passent en `commonMain` sans modification.

Room existe en version KMP depuis la 2.7 (base SQLite fournie par `androidx.sqlite:sqlite-bundled`). Sommelle est en 2.6.1 : il faut monter de version et passer le schéma en `commonMain`. DataStore existe aussi en KMP. Les rappels WorkManager deviennent des notifications planifiées `UNUserNotificationCenter`. L'export PDF doit être réécrit côté iOS (`UIGraphicsPDFRenderer`).

### Lunaison

Les règles du cycle et les contenus sont en Kotlin pur (5 128 lignes). Room KMP et DataStore KMP couvrent le stockage.

Partage avec le partenaire :

- **QR** : à garder. iOS lit les QR nativement (`AVCaptureMetadataOutput`). La génération peut rester en Kotlin ou utiliser `CIQRCodeGenerator`. ZXing (Java) ne compile pas pour iOS.
- **SMS** : à garder, via `MFMessageComposeViewController` ou une URL `sms:`.
- **NFC HCE** : à abandonner sur iPhone. Core NFC permet de lire des étiquettes et certaines cartes. L'émulation de carte (HCE) n'est ouverte que depuis iOS 18.1, dans certains pays, pour des cas d'usage précis (paiement, accès, transport), avec un accord commercial avec Apple. Ce cadre ne convient pas à un échange entre deux téléphones.

### Sillon

Les catalogues de légumes et de maladies sont des données Kotlin pures (2 427 lignes). La prise de photo passe par `PHPickerViewController` ou `UIImagePickerController`. Le diagnostic photo LiteRT est optionnel : LiteRT existe sur iOS, mais l'intégration demande un pont Swift ou une conversion du modèle en Core ML. Recommandation : publier la v1 iOS sans diagnostic photo.

### Respire

Respire Android repose sur un service d'accessibilité. Il détecte l'ouverture d'une appli, affiche une pause, et bloque les Reels et Shorts en lisant l'arbre de l'écran. Rien de cela n'est possible sur iOS.

- **Aucune interception d'ouverture d'appli.** iOS n'expose ni l'appli au premier plan ni le contenu des autres applis. Il n'existe pas d'équivalent au service d'accessibilité.
- **Screen Time API.** Trois frameworks : FamilyControls (autorisation et choix des applis par l'utilisateur), ManagedSettings (application d'un blocage, le « bouclier »), DeviceActivity (plages horaires et seuils d'usage). L'appli doit obtenir de Apple l'entitlement Family Controls pour la distribution. La demande se fait par formulaire et le délai de réponse est variable.
- **Écran de blocage peu personnalisable.** Le bouclier est un écran système. `ShieldConfiguration` ne permet de changer que l'icône, les textes, les couleurs et deux boutons. Pas d'exercice de respiration interactif, pas de compte à rebours animé.
- **Blocage des Reels et Shorts impossible.** Screen Time bloque une appli entière ou un domaine web entier. Il ne voit pas l'intérieur d'Instagram ou de YouTube.
- **Alternative sans entitlement.** L'appli Raccourcis propose une automatisation « Quand j'ouvre l'appli X » qui peut ouvrir Respire. L'utilisateur doit la créer lui-même. Respire peut fournir une action App Intents et un guide pas à pas.

Conclusion : sur iPhone, Respire serait une appli de pause respiratoire plus une limite Screen Time sur des applis entières. C'est un produit différent. Il passe en dernier, et seulement après réponse d'Apple sur l'entitlement.

## 4. Mutualisation entre applis

### Constat

`pilmil-core` contient aujourd'hui :

- `core-platform` : `HapticFeedback` (Android et iOS), `BackupVault` (sauvegarde chiffrée AES-GCM, implémentation Android uniquement).
- `core-designsystem` : `PilmilTokens` (couleurs, espacements) en `commonMain`, `PilmilSwipeableCard` en `androidMain` seulement.

Aucune appli ne l'utilise. Vérification : `grep -ril pilmil` dans les `settings.gradle.kts` et `build.gradle.kts` des cinq applis ne trouve que l'identifiant d'application `fr.pilmil.sillon`. Aucune dépendance, aucun `includeBuild`. `pilmil-core` n'a pas de CI, ni Android ni iOS.

En revanche, du code est déjà dupliqué. Les liens vers les autres applis de la suite (F-Droid, Play) sont écrits à la main dans au moins quatre dépôts (`sauve/.../MainActivity.kt`, `sommelle/.../WeekScreen.kt`, `lunaison/.../Common.kt`, `respire/.../MainActivity.kt`).

### Ce qui vaut le coup d'être mutualisé

Un petit noyau stable, qui change rarement :

- les tokens de base (espacements, noir OLED, contrastes minimaux) ;
- la liste des applis de la suite, avec nom, description courte et liens Store, pour la promotion croisée ;
- une aide aux notifications locales (canal Android, autorisation iOS, planification) ;
- `BackupVault`, une fois l'implémentation iOS écrite et testée ;
- l'abstraction haptique.

### Ce qui ne vaut pas le coup

- **Un design system unique.** Chaque appli a son identité (couleurs, typographie, ton). Les thèmes de Sommelle et de Lunaison sont différents et doivent le rester. Un composant commun imposerait des compromis visuels et lierait les versions des applis entre elles.
- **La logique métier.** Elle reste dans chaque dépôt, comme le prévoit déjà le README de `pilmil-core`.
- **Les écrans.** Un écran partagé entre deux applis devient vite un écran à options.

### Modes de partage comparés

| Critère | Sous-module Git + `includeBuild` | Publication Maven (GitHub Packages) | Copie du code |
|---|---|---|---|
| Build F-Droid | Oui, F-Droid clone les sous-modules | Non : GitHub Packages exige un jeton, même en lecture | Oui |
| Contributeur externe | Clone avec `--recurse-submodules`, rien d'autre | Doit créer un jeton GitHub | Rien à faire |
| Version figée | Oui, par le commit du sous-module | Oui, par numéro de version | Oui, mais sans traçabilité |
| Correctif propagé | Mise à jour du pointeur dans chaque appli | Montée de version dans chaque appli | Copie manuelle, oublis fréquents |
| Coût de mise en place | Faible | Moyen (publication, signature, jeton) | Nul |

Maven Central éviterait le problème du jeton, mais demande signature, compte Sonatype et cycle de publication. C'est disproportionné pour cinq consommateurs.

### Recommandation

Sous-module Git dans chaque appli (par exemple `pilmil-core/`) et `includeBuild("pilmil-core")` dans `settings.gradle.kts`. Gradle substitue alors la dépendance par le code source, sans publication.

On l'introduit appli par appli, au moment de son portage iOS, pas avant. Une appli Android seule n'a aucun besoin de `pilmil-core` aujourd'hui. Chaque élément entre dans `pilmil-core` seulement quand une deuxième appli en a besoin.

## 5. Prérequis et décisions du propriétaire

### Compte et machine

- **Compte Apple Developer** : 99 USD par an. Indispensable pour TestFlight et l'App Store, et pour demander l'entitlement Family Controls.
- **Mac ou CI macOS** : la compilation iOS exige Xcode, donc macOS. Les runners macOS de GitHub Actions sont gratuits pour les dépôts publics. Un Mac reste utile pour déboguer sur appareil.

### Licence GPL-3.0 et App Store

C'est la décision la plus importante. Les conditions de l'App Store ajoutent des restrictions d'usage (DRM, limites de copie, conditions d'utilisation Apple) que la GPL-3.0 interdit d'ajouter (article 10). L'incompatibilité est reconnue par la FSF. En 2011, VLC a été retiré de l'App Store après une plainte d'un de ses contributeurs. VLC est revenu en 2013 après passage du code concerné sous LGPL/MPL, avec l'accord des auteurs.

Solutions :

- **Auteur unique** : ajouter au fichier `LICENSE` une exception de licence (permission additionnelle au sens de l'article 7 de la GPL-3.0) autorisant la distribution par l'App Store. Autre voie : double licence (GPL-3.0 plus MPL-2.0 par exemple).
- **Contributeurs externes** : il faut l'accord écrit de chacun pour le code qu'il a écrit, ou réécrire ce code.

Constat sur l'historique Git (historique complet, clones approfondis avec `git fetch --unshallow`, toutes branches) :

| Dépôt | Auteurs des commits |
|---|---|
| sauve | Relthdrarxash 2, d07pwn3d 3, Claude 3 |
| sommelle | Relthdrarxash 51, d07pwn3d 19, Claude 2, github-actions[bot] 14, dependabot[bot] 3 |
| lunaison | Relthdrarxash 49, d07pwn3d 41, Claude 3, github-actions[bot] 23 |
| respire | Relthdrarxash 41, d07pwn3d 16, Claude 4 |
| sillon | Relthdrarxash 4, Claude 2 |
| pilmil-core | Relthdrarxash 6 |

Lecture :

- `Relthdrarxash` utilise l'adresse noreply GitHub du propriétaire du dépôt.
- `d07pwn3d <d07pwn3d@pop-os.home>` est une identité Git locale (nom de machine Pop!_OS), sans compte GitHub associé dans les métadonnées. Elle semble correspondre au même propriétaire sur un autre poste. **À confirmer par le propriétaire.** Si ce n'est pas lui, il faut l'accord de cette personne.
- `Claude` : commits produits par un assistant IA pour le compte du propriétaire. Pas un tiers ayant des droits à céder, mais la question des droits sur du code généré mérite d'être notée dans la décision.
- `github-actions[bot]` : captures d'écran régénérées. `dependabot[bot]` : montées de version d'actions GitHub dans les workflows. Pas d'apport créatif.
- Aucun contributeur humain externe n'apparaît.

Si l'identité `d07pwn3d` est confirmée, le propriétaire est auteur unique et peut ajouter l'exception de licence seul, dépôt par dépôt. À faire avant la première soumission à Apple, et avant d'accepter toute contribution externe.

### Confidentialité

L'App Store demande des « étiquettes de confidentialité ». Les applis ne collectent rien : déclarer « Data Not Collected ». Il faut aussi un fichier `PrivacyInfo.xcprivacy` (manifeste de confidentialité) qui déclare les API sensibles utilisées, comme `UserDefaults`, et une URL de politique de confidentialité.

### Revue Apple pour les applis santé

Sauve, Sommelle et Lunaison touchent à la santé. La revue Apple (règle 1.4, sécurité physique, et règle 5.1.3, santé) peut demander :

- des avertissements clairs (Sauve ne remplace pas un appel aux secours ; Sommelle et Lunaison ne sont pas des dispositifs médicaux) ;
- les sources des contenus médicaux ;
- pour Lunaison, l'absence de toute promesse contraceptive non validée.

Prévoir un ou deux allers-retours de revue par appli.

## 6. Plan par étapes

| Étape | Contenu | Critère de fin vérifiable |
|---:|---|---|
| 0 | Décisions du propriétaire | Compte Apple Developer actif. Identité `d07pwn3d` confirmée. Exception de licence ajoutée au `LICENSE` de sauve. |
| 1 | Pilote Sauve : compilation | Workflow iOS vert sur GitHub Actions (framework `Shared` et coque XcodeGen). Tests `commonTest` verts sur Android et iOS. |
| 2 | Pilote Sauve : appareil | Build sur TestFlight. Métronome, synthèse vocale et haptique vérifiés sur un iPhone réel. |
| 3 | Pilote Sauve : publication | Widget SOS en WidgetKit et App Shortcuts. Appli acceptée sur l'App Store. |
| 4 | `pilmil-core` utilisable | CI de `pilmil-core` (Android et iOS) verte. `BackupVault` iOS écrit et testé. Liste des applis de la suite en `commonMain`. |
| 5 | Sommelle | Room 2.7 ou plus en `commonMain`. Sous-module `pilmil-core` et `includeBuild` en place. Build F-Droid inchangé. TestFlight. |
| 6 | Lunaison | Room KMP, partage QR et SMS fonctionnels sur iPhone. HCE désactivée côté iOS. TestFlight. |
| 7 | Sillon | Catalogues en `commonMain`. v1 iOS sans diagnostic photo sur TestFlight. |
| 8 | Respire | Réponse d'Apple sur l'entitlement Family Controls. Décision écrite : version Screen Time, version Raccourcis, ou pas de version iPhone. |

Chaque étape d'appli inclut une vérification : la version Android publiée sur F-Droid se construit toujours sans jeton ni service propriétaire.
