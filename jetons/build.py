#!/usr/bin/env python3
"""Assemble les fichiers autonomes (hors ligne) à partir de src/.

  python3 build.py

Produit :
  jetons-cabine.html    outil d'impression des jetons (ordinateur)
  registre-cabine.html  registre de dépôt / retrait (téléphone du responsable)
"""
from pathlib import Path

ROOT = Path(__file__).parent
SRC = ROOT / "src"
HEAD = ('<!doctype html>\n<html lang="fr">\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n')


def script(path):
    return "<script>\n" + (SRC / path).read_text(encoding="utf-8").replace("</script", "<\\/script") + "\n</script>"


def build(src, out):
    html = (SRC / src).read_text(encoding="utf-8")
    html = html.replace("<!--CORE-->", script("core.js"))
    html = html.replace("<!--QRLIB-->", script("vendor/qrcode-generator-1.4.4.js"))
    html = html.replace("<!--PDFLIBS-->", script("vendor/html2canvas-1.4.1.min.js") + "\n" + script("vendor/jspdf-2.5.1.umd.min.js"))
    html = html.replace("<!--JSQR-->", script("vendor/jsQR-1.4.0.js"))
    (ROOT / out).write_text(HEAD + html, encoding="utf-8")
    print(f"{out}: {len(HEAD + html) // 1024} Ko")


build("contenu.html", "jetons-cabine.html")
build("registre.html", "registre-cabine.html")
