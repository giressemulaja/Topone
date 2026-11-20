#!/usr/bin/env python3
"""Assemble les fichiers autonomes (hors ligne) à partir de src/.

  python3 build.py

Produit :
  jetons-cabine.html    outil d'impression des jetons (ordinateur)
  registre-cabine.html  registre de dépôt / retrait (téléphone du responsable)
"""
import base64
from pathlib import Path

ROOT = Path(__file__).parent
SRC = ROOT / "src"
EARLY = ('<script>document.documentElement.classList.add("locked")</script>'
         '<style>html.locked body > :not(.gate) { display: none !important; }</style>')
HEAD = ('<!doctype html>\n<html lang="fr">\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n')


def script(path):
    return "<script>\n" + (SRC / path).read_text(encoding="utf-8").replace("</script", "<\\/script") + "\n</script>"


def build(src, out):
    html = (SRC / src).read_text(encoding="utf-8")
    html = html.replace("<!--CORE-->", script("core.js"))
    html = html.replace("<!--AUTH-->", script("auth.js"))
    # Cache le contenu dès le chargement, avant même que l'écran de connexion soit prêt
    html = html.replace("</title>", "</title>\n" + EARLY, 1)
    html = html.replace("<!--QRLIB-->", script("vendor/qrcode-generator-1.4.4.js"))
    html = html.replace("<!--PDFLIBS-->", script("vendor/html2canvas-1.4.1.min.js") + "\n" + script("vendor/jspdf-2.5.1.umd.min.js"))
    html = html.replace("<!--JSQR-->", script("vendor/jsQR-1.4.0.js"))
    logo = "data:image/png;base64," + base64.b64encode((SRC / "gm-logo-128.png").read_bytes()).decode()
    html = html.replace("%%GM_LOGO%%", logo)
    (ROOT / out).write_text(HEAD + html, encoding="utf-8")
    print(f"{out}: {len(HEAD + html) // 1024} Ko")


build("contenu.html", "jetons-cabine.html")
build("registre.html", "registre-cabine.html")

# Copie dans le projet Android (une application par outil)
ANDROID = ROOT.parent / "android" / "app" / "src"
if ANDROID.exists():
    for out, flavor in (("jetons-cabine.html", "jetons"), ("registre-cabine.html", "registre")):
        dest = ANDROID / flavor / "assets" / "www" / "index.html"
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes((ROOT / out).read_bytes())
        print(f"-> android/{flavor}/assets/www/index.html")
