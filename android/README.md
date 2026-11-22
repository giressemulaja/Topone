# Applications Android GM Corporate

Un seul projet Android produit deux applications installables (APK), qui fonctionnent **sans internet** :

| APK | Nom sur le téléphone | Identifiant |
|---|---|---|
| `jetons-GM-Corporate-1.0.0.apk` | GM Jetons | `com.gmcorporate.jetons` |
| `registre-GM-Corporate-1.0.0.apk` | GM Registre | `com.gmcorporate.registre` |

Android 7.0 ou plus récent. Les APK ne s'installent pas sur iPhone (iOS n'accepte pas les APK).

## Sécurité à trois niveaux

1. **Activation** (native) : à la première ouverture sur chaque téléphone, le code d'activation GM Corporate est demandé. Il n'est pas stocké en clair (empreinte SHA-256 dans `ActivationActivity.java`). L'activation est liée au téléphone et n'est jamais sauvegardée ni transférée : une réinstallation ou un autre téléphone redemande le code. 5 erreurs bloquent l'activation quelques minutes.
2. **Connexion** : identifiant et mot de passe, comme dans les fichiers HTML.
3. **Mot secret de la cabine** pour les codes des jetons.

## Ce que fait la partie native

- Pages servies depuis une origine sécurisée interne (`WebViewAssetLoader`) : caméra, stockage et photos fonctionnent comme dans Chrome.
- Caméra : scan du QR et photo du téléphone (permission demandée au premier usage).
- PDF et sauvegardes : enregistrés dans *Téléchargements/GM Corporate*, puis proposés à l'ouverture (PDF) ou au partage (sauvegarde vers WhatsApp, Drive…).
- Impression native Android (A4, couleur).
- Bouton Retour : met l'application en arrière-plan sans rien perdre.

## Construire les APK

```
python3 ../jetons/build.py           # régénère les pages et les copie dans app/src/<app>/assets/www/
./gradlew assembleRelease            # APK dans app/build/outputs/apk/<app>/release/
```

Prérequis : JDK 17, SDK Android (plateforme 35, build-tools 35.0.0), `local.properties` avec `sdk.dir=...`.

## Clé de signature (très important)

Les APK sont signés avec `keystore/gm-corporate.jks`, décrit par `keystore.properties`. Ces deux fichiers **ne sont pas dans le dépôt** (secrets). Gardez-les précieusement, en plusieurs copies : **sans cette clé, il sera impossible de publier une mise à jour** que les téléphones accepteront par-dessus la version installée.

---

© 2026 GM Corporate. Tous droits réservés.
Développeur : Giresse Mulaja · Tél. +243 810 000 421 · giressemulaja8@gmail.com
