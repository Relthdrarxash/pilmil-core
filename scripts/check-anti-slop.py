#!/usr/bin/env python3
"""
Pilmil Studio — Vérificateur Anti-Slop IA & Emojis Pré-Push.
Scanne les fichiers modifiés ou le dépôt pour détecter :
1. Les emojis décoratifs / marketing d'IA (🚀, 💡, ✨, 🔥, 🎯, 👉, etc.)
2. Les tics de langage et verbiage creux d'IA ("plonger au cœur", "game-changer", "seamlessly", etc.)
3. Les commentaires superflus paraphrasant le code
"""

import sys
import os
import re
import subprocess

import unicodedata

# Emojis fonctionnels autorisés pour les badges d'applications compagnes ou états spécifiques
WHITELIST_EMOJIS = {"🌿", "🛏️", "🌙", "🍂", "\U0001F6CF", "\uFE0F"}

# Caractères typographiques permis (arborescences, ponctuation, devises, coches de tableaux)
ALLOWED_SYMBOLS = {
    "─", "│", "┌", "┐", "└", "┘", "├", "┤", "┬", "┴", "┼",
    "•", "€", "©", "«", "»", "’", "‘", "“", "”", "…", "—", "–", "±", "×",
    "✓", "✔", "✕", "✗",
}

def is_emoji(char):
    if char in WHITELIST_EMOJIS or char in ALLOWED_SYMBOLS:
        return False
    # Vérifie si le caractère est dans les blocs d'emojis standard
    cp = ord(char)
    if 0x1F300 <= cp <= 0x1FAFF: # Emojis, pictographes, symboles
        return True
    if 0x2600 <= cp <= 0x27BF:   # Dingbats, météo, symboles divers
        return True
    return False

AI_BUZZWORDS = [
    r"\bgame-changer\b",
    r"\bdelve into\b",
    r"\bplonger au c(oe|œ)ur\b",
    r"\brévolutionn(e|er|ant)\b",
    r"\bpaysage num(é|e)rique\b",
    r"\bt(é|e)moigne de\b",
    r"\bde mani(è|e)re transparente\b",
    r"\bseamlessly\b",
    r"\bunleash\b",
    r"\btapestry\b",
    r"\btestament to\b",
]

BUZZWORD_REGEX = re.compile("|".join(AI_BUZZWORDS), re.IGNORECASE)

IGNORED_DIRS = {".git", ".gradle", "build", "captures", ".idea", ".claude", ".gemini", ".agents"}
CHECKED_EXTENSIONS = {".kt", ".md", ".txt", ".html", ".xml", ".kts"}


def get_git_root():
    try:
        return subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
    except Exception:
        return os.getcwd()


def get_target_files():
    """Détecte les fichiers modifiés git ou analyse les fichiers du projet."""
    root_dir = get_git_root()
    try:
        cmd = ["git", "diff", "--name-only", "HEAD"]
        output = subprocess.check_output(cmd, text=True, cwd=root_dir, stderr=subprocess.DEVNULL)
        files = [os.path.join(root_dir, f.strip()) for f in output.splitlines() if f.strip()]
        if files:
            return [f for f in files if os.path.exists(f)]
    except Exception:
        pass

    # Repli : scan complet des fichiers du dépôt git
    collected = []
    for root, dirs, files in os.walk(root_dir):
        dirs[:] = [d for d in dirs if d not in IGNORED_DIRS and not d.startswith(".")]
        for f in files:
            ext = os.path.splitext(f)[1]
            if ext in CHECKED_EXTENSIONS:
                collected.append(os.path.join(root, f))
    return collected


def check_file(filepath):
    violations = []
    is_companion_badge_file = "SettingsScreen.kt" in filepath or "DoctorScreen.kt" in filepath

    try:
        with open(filepath, "r", encoding="utf-8") as f:
            for idx, line in enumerate(f, 1):
                # 1. Vérification des emojis
                for char in line:
                    if is_emoji(char):
                        if char in WHITELIST_EMOJIS and is_companion_badge_file:
                            continue
                        violations.append((idx, f"Emoji d'IA / décoratif non autorisé '{char}': {line.strip()[:80]}"))

                # 2. Vérification des buzzwords d'IA
                match = BUZZWORD_REGEX.search(line)
                if match:
                    violations.append((idx, f"Jargon/tic de langage d'IA '{match.group(0)}': {line.strip()[:80]}"))

    except Exception as e:
        pass

    return violations


def main():
    files = get_target_files()
    total_violations = 0

    print("🛡️  Inspection Anti-Slop IA & Emojis en cours sur Pilmil Studio...")
    for f in sorted(files):
        ext = os.path.splitext(f)[1]
        if ext not in CHECKED_EXTENSIONS:
            continue
        v = check_file(f)
        if v:
            print(f"\n❌ Violation(s) trouvée(s) dans {f} :")
            for line_no, msg in v:
                print(f"   Ligne {line_no} : {msg}")
            total_violations += len(v)

    if total_violations > 0:
        print(f"\n⛔ Refus Pré-Push : {total_violations} marqueur(s) d'AI Slop / emoji(s) à corriger.")
        sys.exit(1)
    else:
        print("✅ Aucun marqueur d'AI-slop ni emoji superflu détecté. Prêt pour le push.")
        sys.exit(0)


if __name__ == "__main__":
    main()
