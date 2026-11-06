# Pilmil Core (KMP — Kotlin Multiplatform)

**pilmil-core** est le socle commun multiplateforme (Android & iOS) de la suite d'applications d'intérêt général **Pilmil Studio** (*Sauve*, *Sommelle*, *Lunaison*, *Respire*, *Sillon*).

100 % hors-ligne, zéro permission réseau, zéro télémétrie, sous licence GPL-3.0.

---

## 🏛️ Architecture Modulaire

```
pilmil-core/
├── core-model/           # Logique métier pure (KMP commonMain/commonTest)
│   ├── emergency/        # Moteur de réanimation & cadence RCP (Sauve)
│   ├── cycle/            # Algorithmes du cycle menstruel & métriques 5D (Lunaison)
│   ├── sleep/            # Algorithmes de restriction du sommeil TCC-I (Sommelle)
│   └── agronomy/         # Calculs J_gel & allélopathie potagère (Sillon)
│
├── core-platform/        # Abstraction des services système (expect/actual & interfaces)
│   ├── haptic/           # HapticFeedback (Android Vibrator vs iOS UIKit Taptic Engine)
│   ├── audio/            # Guidage vocal local & signaux sonores
│   └── storage/          # Stockage chiffré sécurisé sans cloud (Keystore vs Keychain)
│
└── core-designsystem/    # Design System Compose Multiplatform (Android & iOS)
    ├── tokens/           # Tokens de couleurs (OLED pure black, contrastes AA/AAA)
    └── components/       # Boutons, cartes, sélecteurs unifiés
```

---

## 📱 Portabilité Android + iOS

- **Zéro couplage Android dans `commonMain`** : Pas de `Context`, pas de `SharedPreferences`, pas de `android.util.Log`. Utilisation stricte de `kotlinx-datetime` pour la manipulation temporelle et `kotlinx-coroutines` pour l'asynchronisme.
- **Compose Multiplatform (CMP)** : Interface utilisateur réutilisable sur Android et iOS.
- **Export Framework iOS** : Génération de frameworks statiques (`PilmilCoreModel.framework`, etc.) consommables par Swift / Xcode.

---

## 🧪 Tests Unitaires Multiplateformes

Tous les algorithmes critiques disposent d'une suite de tests dans `commonTest`, exécutables sur JVM et sur simulateur/runtime iOS natif.
