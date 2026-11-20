# Jetons de la cabine de charge

Deux fichiers autonomes, utilisables **sans internet** :

| Fichier | Où l'ouvrir | À quoi il sert |
|---|---|---|
| `jetons-cabine.html` | Ordinateur (Chrome, Edge, Firefox) | Générer et imprimer les planches de jetons (8 par feuille A4), chacun avec numéro, code, motif, QR code et talon. |
| `registre-cabine.html` | Téléphone du responsable (Chrome sur Android conseillé) | Scanner les jetons au dépôt et au retrait, enregistrer le téléphone et le code de retrait du client, marquer les jetons « rendus ». |

Les deux outils doivent utiliser **le même mot secret**.

## Ce que le système bloque

| Fraude | Ce qui la bloque |
|---|---|
| Jeton inventé | Le code et le QR dépendent du mot secret : le registre répond « Jeton faux ». |
| Copie d'un jeton déjà utilisé | Le registre répond « Déjà rendu : copie probable ». |
| Copie parfaite présentée avant le vrai client | Le code de retrait (4 chiffres) n'est imprimé nulle part ; sinon, déverrouillage du téléphone devant le responsable. |
| Photocopie | Cachet sec en relief ou signature au stylo, couleur et série du lot, micro-texte. |

## Important

- Le registre est stocké dans le navigateur du téléphone. Faites **une sauvegarde chaque soir** (onglet Réglages) et gardez-la ailleurs (clé USB, ordinateur).
- Avant la mise en service, faites un essai : enregistrez un dépôt test, fermez le navigateur, rouvrez le fichier et vérifiez que le dépôt est toujours là.

## Modifier les outils

Les sources sont dans `src/`. Après modification, reconstruisez les deux fichiers :

```
python3 build.py
```

Bibliothèques intégrées : qrcode-generator 1.4.4 (MIT, Kazuhiko Arase) et jsQR 1.4.0 (Apache 2.0, voir `src/vendor/jsQR-LICENSE.txt`).
