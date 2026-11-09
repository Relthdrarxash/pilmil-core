#!/usr/bin/env python3
"""Générateur de visuels marketing des applis Pilmil Studio.

Lit <appli>/docs/marketing/manifest.json, compose des pages HTML/CSS (téléphone dessiné en CSS autour
des captures réelles) et les rend en PNG avec Chrome sans fenêtre. Bibliothèque standard seulement.
Voir README.md.
"""
import argparse
import html
import json
import shutil
import struct
import subprocess
import sys
import tempfile
import zlib
from pathlib import Path

CHROME = "/usr/bin/google-chrome"
LARGEUR_MIN_CAPTURE = 900   # en dessous, on refuse d'agrandir
LARGEUR_ECRAN_CSS = 724     # largeur de l'écran du téléphone de base (760 - 2 x 18)
BASE_W = 760                # largeur du téléphone de base, mis à l'échelle par transform
BASE_H = 1605               # 724 * 2340 / 1080 = 1569 d'écran + 2 x 18 de cadre


class ErreurManifeste(Exception):
    pass


# ---------------------------------------------------------------- utilitaires

def dimensions_png(chemin: Path):
    with open(chemin, "rb") as f:
        tete = f.read(24)
    if tete[:8] != b"\x89PNG\r\n\x1a\n":
        raise ErreurManifeste(f"{chemin} n'est pas un PNG")
    return struct.unpack(">II", tete[16:24])


def couleur_haut_gauche(chemin: Path) -> str:
    """Couleur du pixel (0, 0) d'un PNG 8 bits RVB/RVBA. Sur la première ligne, tous les filtres PNG
    redonnent la valeur brute du premier pixel, donc pas besoin de décoder l'image."""
    donnees = chemin.read_bytes()
    type_couleur = donnees[25]
    if donnees[24] != 8 or type_couleur not in (2, 6):
        return "#ffffff"
    pos, idat = 8, b""
    while pos < len(donnees):
        n, genre = struct.unpack(">I4s", donnees[pos:pos + 8])
        if genre == b"IDAT":
            idat += donnees[pos + 8:pos + 8 + n]
            if len(idat) > 64:
                break
        pos += 12 + n
    debut = zlib.decompressobj().decompress(idat, 8)
    return "#%02x%02x%02x" % tuple(debut[1:4])


def hex_rgb(h: str):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def luminance(rgb):
    def lin(c):
        c /= 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contraste(a, b):
    la, lb = luminance(a), luminance(b)
    clair, sombre = max(la, lb), min(la, lb)
    return (clair + 0.05) / (sombre + 0.05)


def lueur(brand) -> str:
    """Teinte des formes de fond : bg1 éclaircie et teintée d'un peu d'accent (10 %), jamais l'accent pur,
    pour éviter le gris sale d'un ambre posé sur du bleu. Rendue avec mix-blend-mode: screen."""
    clair = melange((255, 255, 255), hex_rgb(brand["bg1"]), brand.get("glowLight", 0.16))
    r, g, b = melange(hex_rgb(brand["accent"]), clair, 0.10)
    return f"rgba({r},{g},{b},{brand.get('glowOpacity', 0.55)})"


def melange(fg, bg, alpha):
    return tuple(round(fg[i] * alpha + bg[i] * (1 - alpha)) for i in range(3))


def verifier_contraste(brand):
    """Avertit si le texte (titre, puis sous-titre à 75 %) passe sous 4,5:1 sur l'un des fonds."""
    texte = hex_rgb(brand["onBg"])
    for nom in ("bg1", "bg2"):
        fond = hex_rgb(brand[nom])
        for libelle, couleur in (("titre", texte), ("sous-titre (75 %)", melange(texte, fond, 0.75))):
            c = contraste(couleur, fond)
            if c < 4.5:
                print(f"AVERTISSEMENT contraste : {libelle} sur {nom} = {c:.2f}:1 (< 4,5:1)", file=sys.stderr)
            else:
                print(f"  contraste {libelle} sur {nom} : {c:.1f}:1 ok")


def url(chemin: Path) -> str:
    return chemin.resolve().as_uri()


# ---------------------------------------------------------------- composants

CSS_COMMUN = """
* { box-sizing: border-box; margin: 0; padding: 0; }
html, body { width: %(W)dpx; height: %(H)dpx; overflow: hidden; }
@font-face { font-family: "Marque"; src: url("%(font_titre)s"); font-weight: 800; }
@font-face { font-family: "Marque"; src: url("%(font_corps)s"); font-weight: 400; }
@font-face { font-family: "Marque"; src: url("%(font_corps_gras)s"); font-weight: 700; }
body { position: relative; font-family: "Marque", sans-serif; color: %(onBg)s;
  background: linear-gradient(160deg, %(bg1)s 0%%, %(bg2)s 100%%); }
.blob { position: absolute; border-radius: 50%%; mix-blend-mode: screen;
  background: radial-gradient(circle at 50%% 50%%, %(lueur)s 0%%, rgba(0,0,0,0) 70%%); }
.grain { position: absolute; inset: 0; opacity: .04; mix-blend-mode: overlay; pointer-events: none;
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='300' height='300'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='2' stitchTiles='stitch'/><feColorMatrix type='saturate' values='0'/></filter><rect width='100%%' height='100%%' filter='url(%%23n)'/></svg>"); }
.titre { font-weight: 800; line-height: 1.08; letter-spacing: -.01em; text-wrap: balance; overflow-wrap: normal; }
.sous { font-weight: 400; line-height: 1.3; opacity: .75; text-wrap: balance; }
.puces { display: flex; flex-wrap: wrap; gap: 14px; }
.puce { font-weight: 700; padding: .42em 1em; border-radius: 999px; background: rgba(255,255,255,.12);
  border: 1px solid rgba(255,255,255,.25); backdrop-filter: blur(6px); white-space: nowrap; }
.tel { position: absolute; width: 760px; height: 1605px; border-radius: 92px; background: #0c0d12;
  box-shadow: 0 70px 140px rgba(0,0,0,.38), inset 0 0 0 2px rgba(255,255,255,.09); }
.tel::before, .tel::after { content: ""; position: absolute; width: 8px; border-radius: 4px; background: #1a1c25; }
.tel::before { left: -6px; top: 330px; height: 150px; box-shadow: 0 190px 0 #1a1c25; }
.tel::after { right: -6px; top: 420px; height: 200px; }
.ecran { position: absolute; inset: 18px; border-radius: 76px; overflow: hidden; background: #000;
  box-shadow: 0 0 0 1px rgba(255,255,255,.14); }
/* Les captures n'ont pas de barre d'état : on réserve 52 px en haut (couleur du bord de la capture) pour la caméra. */
.ecran img { position: absolute; left: 0; top: 52px; width: 100%%; height: calc(100%% - 52px); object-fit: cover; object-position: top; display: block; }
.ecran::after { content: ""; position: absolute; inset: 0;
  background: linear-gradient(115deg, rgba(255,255,255,.06) 0%%, rgba(255,255,255,0) 45%%); }
.cam { position: absolute; left: 50%%; top: 40px; width: 26px; height: 26px; margin-left: -13px; border-radius: 50%%;
  background: radial-gradient(circle at 40%% 40%%, #1b2030 0, #05060a 60%%); z-index: 3;
  box-shadow: 0 0 0 3px rgba(5,6,10,.85); }
.voile { position: absolute; inset: 0; border-radius: 92px; background: #000; z-index: 4; pointer-events: none; }
.logo { border-radius: 22%%; box-shadow: 0 10px 30px rgba(0,0,0,.3); display: block; }
"""

# Réduit la taille de police d'un bloc jusqu'à ce qu'il tienne dans sa boîte (hauteur et largeur).
JS_AJUSTE = """
document.fonts.ready.then(function () {
  document.querySelectorAll('[data-fit]').forEach(function (el) {
    var max = parseFloat(el.dataset.fit), min = parseFloat(el.dataset.min || 20);
    var taille = parseFloat(getComputedStyle(el).fontSize);
    while (taille > min && (el.scrollHeight > max + 1 || el.scrollWidth > el.clientWidth + 1)) {
      taille -= 2; el.style.fontSize = taille + 'px';
    }
  });
  document.body.dataset.pret = '1';
});
"""


def telephone(capture: Path, cx, haut, echelle=1.0, rotation=0.0, assombri=0.0, ordre=1, defile=0):
    """Un téléphone centré horizontalement sur cx, dont le bord haut (avant rotation) est à `haut`."""
    gauche = cx - BASE_W / 2
    haut = haut - (1 - echelle) * 0.4 * BASE_H  # l'échelle se fait autour de 40 % de la hauteur : on compense
    voile = f'<div class="voile" style="opacity:{assombri:.2f}"></div>' if assombri else ""
    return (
        f'<div class="tel" style="left:{gauche:.0f}px;top:{haut:.0f}px;z-index:{ordre};'
        f'transform-origin:50% 40%;transform:rotate({rotation}deg) scale({echelle})">'
        f'<div class="ecran" style="background:{couleur_haut_gauche(capture)}"><img src="{url(capture)}" alt="" style="top:{52 - defile * LARGEUR_ECRAN_CSS / 1080:.0f}px"></div><div class="cam"></div>{voile}</div>'
    )


def fond(W, H, brand):
    """Deux grandes formes douces d'accent et un fin grain, par-dessus le dégradé du body."""
    if W > H:  # formats larges : les formes passent derrière les téléphones, pas derrière le texte
        formes = (
            f'<div class="blob" style="width:{H * 1.3:.0f}px;height:{H * 1.3:.0f}px;right:{-H * .2:.0f}px;top:{-H * .35:.0f}px"></div>'
            f'<div class="blob" style="width:{H * 1.1:.0f}px;height:{H * 1.1:.0f}px;left:{W * .38:.0f}px;bottom:{-H * .55:.0f}px"></div>'
        )
    else:
        g = max(W, H)
        formes = (
            f'<div class="blob" style="width:{g * .75:.0f}px;height:{g * .75:.0f}px;right:{-g * .22:.0f}px;top:{-g * .18:.0f}px"></div>'
            f'<div class="blob" style="width:{g * .95:.0f}px;height:{g * .95:.0f}px;left:{-g * .35:.0f}px;bottom:{-g * .45:.0f}px"></div>'
        )
    return formes + '<div class="grain"></div>'


def puces_html(puces, taille, centre=False):
    if not puces:
        return ""
    cases = "".join(f'<span class="puce" style="font-size:{taille}px">{html.escape(p)}</span>' for p in puces)
    style = ' style="justify-content:center"' if centre else ""
    return f'<div class="puces"{style}>{cases}</div>'


def page(W, H, brand, corps, racine: Path):
    police = brand["font"]
    f_titre = racine / police["file"]
    f_corps = racine / police.get("body_file", police["file"])
    f_gras = racine / police.get("bold_file", police.get("body_file", police["file"]))
    css = CSS_COMMUN % {
        "W": W, "H": H, "onBg": brand["onBg"], "bg1": brand["bg1"], "bg2": brand["bg2"], "accent": brand["accent"],
        "lueur": lueur(brand),
        "font_titre": url(f_titre), "font_corps": url(f_corps), "font_corps_gras": url(f_gras),
    }
    return (f'<!doctype html><html><head><meta charset="utf-8"><style>{css}</style></head>'
            f'<body>{fond(W, H, brand)}{corps}<script>{JS_AJUSTE}</script></body></html>')


# ---------------------------------------------------------------- mises en page

def vue_store(brand, ecran, racine):
    """1080 x 1920 : texte en haut, téléphone qui déborde en bas, second téléphone optionnel derrière."""
    W, H = 1080, 1920
    marge = 80
    tilt = float(ecran.get("tilt", -3))
    corps = ""
    if ecran.get("layout") == "duo" and ecran.get("second"):
        # Principal à ~700 px, poussé du côté opposé ; le second, derrière à 82 %, incliné à l'opposé et
        # assombri de 12 %, dépasse franchement (environ 35 % de sa largeur visible).
        sens = 1 if tilt < 0 else -1  # côté du second
        corps += telephone(racine / ecran["second"], 540 + sens * 390, 730, 0.82, -tilt * 1.5 if tilt else 5 * sens, 0.12, 1)
        corps += telephone(racine / ecran["capture"], 540 - sens * 45, 700, 0.92, tilt, 0, 2, ecran.get("scroll", 0))
    else:
        corps += telephone(racine / ecran["capture"], 540, 700, 1.0, tilt, 0, 2, ecran.get("scroll", 0))
    texte = (
        f'<div style="position:absolute;left:{marge}px;right:{marge}px;top:96px;height:560px;display:flex;'
        f'flex-direction:column;justify-content:center;align-items:center;text-align:center;gap:26px;z-index:5">'
        f'<div class="titre" data-fit="300" data-min="52" style="font-size:88px;width:100%">{html.escape(ecran["title"])}</div>'
        f'<div class="sous" data-fit="112" data-min="26" style="font-size:38px;width:100%">{html.escape(ecran["subtitle"])}</div>'
        f'{puces_html(ecran.get("chips"), 30, centre=True)}'
        '</div>'
    )
    return page(W, H, brand, corps + texte, racine), W, H


def bloc_marque(brand, racine, langue, tagline, taille_nom, taille_acc, logo, largeur, haut_max, extra=""):
    icone = racine / brand["icon"]
    return (
        f'<div style="display:flex;flex-direction:column;gap:{logo * .32:.0f}px;width:{largeur}px;z-index:5">'
        f'<div style="display:flex;align-items:center;gap:{logo * .3:.0f}px">'
        f'<img class="logo" src="{url(icone)}" style="width:{logo}px;height:{logo}px" alt="">'
        f'<div class="titre" data-fit="{taille_nom * 1.3:.0f}" data-min="30" style="font-size:{taille_nom}px">{html.escape(brand["name"])}</div></div>'
        f'<div class="sous" data-fit="{haut_max}" data-min="22" style="font-size:{taille_acc}px;opacity:.85">{html.escape(tagline)}</div>'
        f'{extra}</div>'
    )


def vue_horizontale(brand, lang, racine, W, H, telephones, largeur_texte, nom, acc, logo, ligne_sup=None):
    """Feature graphic, bannière et image de partage : marque à gauche, téléphones coupés en bas à droite."""
    marge = round(W * 0.06)
    texte = (
        f'<div style="position:absolute;left:{marge}px;top:0;height:{H}px;display:flex;align-items:center">'
        + bloc_marque(brand, racine, lang, ligne_sup or lang["tagline"], nom, acc, logo, largeur_texte, int(H * .4),
                      extra=puces_html(lang.get("chips"), max(18, round(acc * .62))) if lang.get("chips") else "")
        + '</div>'
    )
    return page(W, H, brand, "".join(telephones) + texte, racine), W, H


def vue_feature(brand, lang, racine):
    cap = racine / (lang.get("feature_capture") or lang["screens"][0]["capture"])
    tel = [telephone(cap, 810, 62, 0.47, -3, 0, 2)]
    return vue_horizontale(brand, lang, racine, 1024, 500, tel, 540, 62, 34, 88)


def vue_banniere(brand, lang, racine):
    caps = [racine / c for c in (lang.get("banner_captures") or [s["capture"] for s in lang["screens"][:3]])]
    caps = (caps * 3)[:3]
    cx = 1190
    tel = [
        telephone(caps[1], cx - 245, 175, 0.46, -8, 0.22, 1),
        telephone(caps[2], cx + 245, 175, 0.46, 8, 0.22, 1),
        telephone(caps[0], cx, 78, 0.58, 0, 0, 3),
    ]
    return vue_horizontale(brand, lang, racine, 1600, 720, tel, 640, 84, 46, 104)


def vue_social(brand, lang, racine):
    caps = [racine / c for c in (lang.get("banner_captures") or [s["capture"] for s in lang["screens"][:2]])]
    caps = (caps * 2)[:2]
    tel = [
        telephone(caps[1], 1010, 150, 0.46, 5, 0.12, 1),
        telephone(caps[0], 880, 88, 0.5, -4, 0, 2),
    ]
    lg = dict(lang, chips=None)
    return vue_horizontale(brand, lg, racine, 1200, 630, tel, 560, 66, 30, 84, ligne_sup=lang["social_line"])


# ---------------------------------------------------------------- rendu

def rendre(html_txt, W, H, sortie: Path, tmp: Path):
    source = tmp / (sortie.stem + ".html")
    source.write_text(html_txt, encoding="utf-8")
    cmd = [
        CHROME, "--headless=new", f"--screenshot={sortie}", f"--window-size={W},{H}", "--hide-scrollbars",
        "--force-device-scale-factor=1", "--allow-file-access-from-files", "--disable-gpu",
        "--virtual-time-budget=4000", f"--user-data-dir={tmp / 'profil'}", source.as_uri(),
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if not sortie.exists():
        raise RuntimeError(f"Chrome n'a pas produit {sortie.name}\n{res.stderr[-600:]}")
    dims = dimensions_png(sortie)
    if dims != (W, H):
        raise RuntimeError(f"{sortie.name} : {dims[0]}x{dims[1]} au lieu de {W}x{H}")
    print(f"  {sortie.name} {W}x{H}")


def valider(manifeste, racine: Path):
    for cle in ("brand", "langs"):
        if cle not in manifeste:
            raise ErreurManifeste(f"manifeste : clé « {cle} » absente")
    brand = manifeste["brand"]
    for cle in ("name", "bg1", "bg2", "accent", "onBg", "icon", "font"):
        if cle not in brand:
            raise ErreurManifeste(f"manifeste : brand.{cle} absent")
    for chemin in (brand["icon"], brand["font"]["file"]):
        if not (racine / chemin).is_file():
            raise ErreurManifeste(f"fichier introuvable : {racine / chemin}")


def verifier_captures(lang, code, racine: Path):
    vues = set()
    for s in lang["screens"]:
        for cle in ("capture", "second"):
            if s.get(cle):
                vues.add(s[cle])
    vues.update(lang.get("banner_captures", []))
    if lang.get("feature_capture"):
        vues.add(lang["feature_capture"])
    for rel in sorted(vues):
        p = racine / rel
        if not p.is_file():
            raise ErreurManifeste(f"[{code}] capture introuvable : {p}")
        w, h = dimensions_png(p)
        if w < LARGEUR_MIN_CAPTURE:
            raise ErreurManifeste(
                f"[{code}] capture trop petite : {p} fait {w}x{h} (minimum {LARGEUR_MIN_CAPTURE} px de large, "
                "le générateur n'agrandit jamais une capture). Régénérez-la en pleine résolution.")
        if abs(h / w - 2340 / 1080) > 0.08:
            print(f"AVERTISSEMENT [{code}] {rel} : ratio {w}x{h} différent de 1080x2340, l'image sera recadrée",
                  file=sys.stderr)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--app-dir", required=True, type=Path, help="dépôt de l'appli (contient docs/marketing/manifest.json)")
    ap.add_argument("--lang", action="append", help="langue à produire (défaut : toutes celles du manifeste)")
    ap.add_argument("--only", help="ne produire que les fichiers dont le nom contient cette chaîne")
    args = ap.parse_args()

    racine = args.app_dir.resolve()
    chemin_manifeste = racine / "docs" / "marketing" / "manifest.json"
    if not chemin_manifeste.is_file():
        sys.exit(f"manifeste introuvable : {chemin_manifeste}")
    if not Path(CHROME).exists():
        sys.exit(f"Chrome introuvable : {CHROME}")
    try:
        manifeste = json.loads(chemin_manifeste.read_text(encoding="utf-8"))
        valider(manifeste, racine)
        brand = manifeste["brand"]
        print(f"{brand['name']}")
        verifier_contraste(brand)
        langues = args.lang or list(manifeste["langs"])
        for code in langues:
            if code not in manifeste["langs"]:
                raise ErreurManifeste(f"langue absente du manifeste : {code}")
            verifier_captures(manifeste["langs"][code], code, racine)
    except ErreurManifeste as e:
        sys.exit(f"ERREUR : {e}")

    with tempfile.TemporaryDirectory(prefix="pilmil-marketing-") as t:
        tmp = Path(t)
        for code in langues:
            lang = manifeste["langs"][code]
            sortie = racine / "docs" / "marketing" / "out" / code
            sortie.mkdir(parents=True, exist_ok=True)
            print(f"[{code}]")
            travaux = []
            for i, ecran in enumerate(lang["screens"], 1):
                travaux.append((f"store_{i:02d}_{ecran['slug']}.png", lambda e=ecran: vue_store(brand, e, racine)))
            travaux += [
                ("feature_graphic.png", lambda: vue_feature(brand, lang, racine)),
                ("readme_banner.png", lambda: vue_banniere(brand, lang, racine)),
                ("social.png", lambda: vue_social(brand, lang, racine)),
            ]
            for nom, construire in travaux:
                if args.only and args.only not in nom:
                    continue
                contenu, W, H = construire()
                rendre(contenu, W, H, sortie / nom, tmp)
    print("Terminé.")


if __name__ == "__main__":
    main()
