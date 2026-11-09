# Visuels marketing

Génère les visuels des applis (captures Play Store, image de présentation, bannière README, image de partage)
à partir de captures d'écran réelles. Python 3 (bibliothèque standard) et Chrome sans fenêtre
(`/usr/bin/google-chrome`), rien d'autre.

## Usage

```bash
# 1. Captures en pleine résolution (1080 x 2340), via le module Paparazzi de l'appli
./capture.sh ../../sommelle            # écrit <appli>/docs/marketing/captures/<fr|en>/*.png

# 2. Visuels fr et en
./build_all.sh ../../sommelle          # écrit <appli>/docs/marketing/out/<fr|en>/
python3 generate.py --app-dir ../../sommelle --lang fr --only store_01   # un seul fichier
```

Par langue : `store_NN_<slug>.png` (1080x1920), `feature_graphic.png` (1024x500), `readme_banner.png` (1600x720),
`social.png` (1200x630). Le générateur refuse une capture de moins de 900 px de large (il n'agrandit jamais)
et avertit si le contraste texte/fond passe sous 4,5:1.

## Manifeste `<appli>/docs/marketing/manifest.json`

```json
{
  "brand": {
    "name": "Appli", "bg1": "#2F3A6B", "bg2": "#171E3F", "accent": "#E8A33D", "onBg": "#F6F3EE",
    "icon": "fastlane/metadata/android/fr-FR/images/icon.png",
    "font": {"file": "docs/fonts/x-800.woff2", "weight": 800, "body_file": "docs/fonts/x-400.woff2", "bold_file": "docs/fonts/x-700.woff2"}
  },
  "langs": {
    "fr": {
      "tagline": "Accroche courte", "social_line": "Phrase pour l'image de partage",
      "chips": ["Sans compte"],
      "screens": [
        {"slug": "accueil", "capture": "docs/marketing/captures/fr/accueil.png", "layout": "solo|duo",
         "second": "docs/marketing/captures/fr/autre.png", "tilt": -3, "title": "...", "subtitle": "...", "chips": ["..."]}
      ]
    }
  }
}
```

Tous les chemins sont relatifs au dépôt de l'appli. Optionnels par langue : `feature_capture` (téléphone du feature graphic,
sinon la 1re capture) et `banner_captures` (3 captures pour l'éventail, sinon les 3 premiers écrans). `glowLight` (0,16), `glowOpacity` (0,55) dans `brand`
règlent les lueurs de fond (teinte de bg1 éclaircie, jamais l accent pur). `scroll` (par écran, en px de capture) fait défiler la capture vers le haut pour montrer une zone plus basse. Les titres sont réduits automatiquement pour ne jamais déborder.

## Ajouter une appli

1. Ajouter dans son module `screenshots` une classe de test `MarketingScreens*Test` (voir celles de Sommelle et Lunaison :
   `DeviceConfig.PIXEL_5` et `useDeviceResolution = true` pour éviter la réduction à 1000 px de haut), puis lancer `capture.sh`.
2. Écrire `docs/marketing/manifest.json` (palette et police tirées du thème de l'appli, 5 à 6 écrans qui racontent un parcours).
3. Lancer `build_all.sh`, ouvrir les PNG et vérifier textes, téléphones et contraste.
