# Jetons de la cabine de charge

Deux fichiers autonomes, utilisables **sans internet** :

| Fichier | Où l'ouvrir | À quoi il sert |
|---|---|---|
| `jetons-cabine.html` | Ordinateur (Chrome, Edge, Firefox) | Générer les planches de jetons (8 par feuille A4), chacun avec numéro, code, motif, QR code et talon, puis les télécharger en PDF ou les imprimer. |
| `registre-cabine.html` | Téléphone du responsable (Chrome sur Android conseillé) | Scanner les jetons au dépôt et au retrait, photographier le téléphone (photo réaffichée au retrait), enregistrer le code de retrait du client, marquer les jetons « rendus ». |

Les deux outils doivent utiliser **le même mot secret**.

## Ce que le système bloque

| Fraude | Ce qui la bloque |
|---|---|
| Jeton inventé | Le code et le QR dépendent du mot secret : le registre répond « Jeton faux ». |
| Copie d'un jeton déjà utilisé | Le registre répond « Déjà rendu : copie probable ». |
| Copie parfaite présentée avant le vrai client | Le code de retrait (4 chiffres) n'est imprimé nulle part ; sinon, déverrouillage du téléphone devant le responsable. |
| Photocopie | Cachet sec en relief ou signature au stylo, couleur et série du lot, micro-texte. |

## Important

- Le registre et les photos sont stockés dans le navigateur du téléphone (photos des téléphones rendus effacées après 30 jours). Faites **une sauvegarde chaque soir** (onglet Réglages) et gardez-la ailleurs (clé USB, ordinateur).
- Avant la mise en service, faites un essai : enregistrez un dépôt test, fermez le navigateur, rouvrez le fichier et vérifiez que le dépôt est toujours là.

## Modifier les outils

Les sources sont dans `src/`. Après modification, reconstruisez les deux fichiers :

```
python3 build.py
```

Bibliothèques intégrées (licences dans `src/vendor/`) : qrcode-generator 1.4.4 (MIT), jsQR 1.4.0 (Apache 2.0), html2canvas 1.4.1 (MIT), jsPDF 2.5.1 (MIT).
