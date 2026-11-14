# Pilmil Core (KMP — Kotlin Multiplatform)

**pilmil-core** est la bibliothèque d'infrastructure partagée (Android & iOS) de la suite d'applications d'intérêt général **Pilmil Studio** (*Sauve*, *Sommelle*, *Lunaison*, *Respire*, *Sillon*).

100 % hors-ligne, zéro permission réseau, zéro télémétrie, sous licence MPL-2.0.

---

## Philosophie & Découpage

Pour éviter tout couplage indésirable et préserver la légèreté des applications, **les règles métier et modèles spécifiques restent strictement dans leurs dépôts respectifs** (ex: les règles FIGO dans *Lunaison*, le protocole TCC-I dans *Sommelle*, la cadence cardiaque dans *Sauve*).

**`pilmil-core` ne contient exclusivement que les briques transversales partagées par l'ensemble des applications :**

```
pilmil-core/
├── core-platform/        # Abstractions matérielles (expect/actual & interfaces KMP)
│   ├── haptic/           # HapticFeedback (Android Vibrator vs iOS Taptic Engine)
│   ├── audio/            # Guidage sonore local sans réseau
│   └── storage/          # Chiffrement & trousseaux sécurisés (Keystore vs Keychain)
│
└── core-designsystem/    # Design System Compose Multiplatform (Android & iOS)
    ├── tokens/           # Couleurs (OLED Black), Espacements, Typographies
    └── components/       # PilmilSwipeableCard, composants interactifs à 120 Hz
```

---

## Sauvegarde chiffrée (.pilmil v1)

`AndroidBackupVault` chiffre une sauvegarde par phrase secrète (PBKDF2 + AES-256-GCM) dans un conteneur `.pilmil`. Format, paramètres et limites : voir la KDoc de `AndroidBackupVault`.

## Permissions Android

`core-platform` déclare `android.permission.VIBRATE` dans son `AndroidManifest.xml` (fusionné dans l'application consommatrice).

## Portabilité Android + iOS

- **Zéro couplage Android dans `commonMain`** : Pas de `Context`, pas de `SharedPreferences`.
- **Ponts natifs unifiés** : Interfaces communes et implémentations adaptées aux SDKs natifs (`androidMain` & `iosMain`).
- **Compose Multiplatform (CMP)** : Composants UI réutilisables sur Android et iOS.
- **iPhone et mutualisation** : approche, faisabilité par appli et mode de partage de `pilmil-core`, dans [docs/IPHONE_ET_MUTUALISATION.md](docs/IPHONE_ET_MUTUALISATION.md).

## Licence

Distribuée sous licence [Mozilla Public License 2.0](LICENSE) (MPL-2.0) : vous pouvez l'utiliser, l'étudier, la modifier et la redistribuer ; les fichiers que vous modifiez doivent rester publiés sous la même licence, avec leur code source. La MPL-2.0 est compatible avec l'App Store : aucune exception n'est nécessaire.
