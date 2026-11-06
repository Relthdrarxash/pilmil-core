# Pilmil Core (KMP — Kotlin Multiplatform)

**pilmil-core** est la bibliothèque d'infrastructure partagée (Android & iOS) de la suite d'applications d'intérêt général **Pilmil Studio** (*Sauve*, *Sommelle*, *Lunaison*, *Respire*, *Sillon*).

100 % hors-ligne, zéro permission réseau, zéro télémétrie, sous licence GPL-3.0.

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

## Portabilité Android + iOS

- **Zéro couplage Android dans `commonMain`** : Pas de `Context`, pas de `SharedPreferences`.
- **Ponts natifs unifiés** : Interfaces communes et implémentations adaptées aux SDKs natifs (`androidMain` & `iosMain`).
- **Compose Multiplatform (CMP)** : Composants UI réutilisables sur Android et iOS.
