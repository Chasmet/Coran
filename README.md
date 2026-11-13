# Coran — application Android

Application mobile pour lire le Saint Coran en **arabe**, **français** et **anglais**, écouter la récitation de Mishary Alafasy et mémoriser les favoris et la progression.

## Fonctions

- 114 sourates, recherche rapide
- Texte arabe Uthmani et traductions Hamidullah (FR) / Saheeh International (EN)
- Lecture audio verset par verset, passage automatique au suivant
- Sourates consultées mises en cache local pour lecture hors connexion
- Favoris et dernière position de lecture persistants
- Mode sombre système, taille du texte, sélection des langues
- Mise à jour intégrée via l'API publique GitHub `releases/latest` (canal de distribution directe)

## Compiler

JDK 17, Android SDK 34, Gradle 8.9 : `./gradlew assembleDebug`.

GitHub Actions compile automatiquement à chaque push sur `main`. La première exécution génère et versionne le JAR officiel du wrapper Gradle s'il n'existe pas encore.

## Première publication signée (obligatoire pour SkillMajAuto)

Configurer **une seule fois** les quatre secrets GitHub Actions du dépôt :

- `CORAN_KEYSTORE_BASE64` : contenu du même keystore JKS encodé en base64 sur une seule ligne.
- `CORAN_KEYSTORE_PASSWORD`
- `CORAN_KEY_ALIAS`
- `CORAN_KEY_PASSWORD`

Garder le keystore original hors du dépôt et le sauvegarder de façon sécurisée : toutes les mises à jour doivent utiliser ce même certificat. À partir de là chaque build produit une vraie GitHub Release avec `coran-release.apk` et `SHA256`. Sans les secrets, un APK de débogage est produit comme Artifact mais **aucune Release installable de mise à jour n'est publiée**.

L'application vérifie le propriétaire du dépôt et l'empreinte SHA256 annoncée par la Release, puis Android contrôle la signature et demande l'accord d'installation. Le package `com.chk.coran` reste inchangé pour conserver les données.

**Note Play Store :** ce canal APK GitHub est destiné à la distribution directe. Une variante Google Play devra utiliser Google Play In-App Updates conformément aux règles de la boutique.

## Contenu et crédits

Service : [Al Quran Cloud](https://alquran.cloud/api) et [CDN Islamic Network](https://alquran.cloud/cdn). Le Coran arabe, les traductions et les récitations doivent être présentés fidèlement. Traductions et audio conservent leurs droits respectifs. Les textes restent attribués aux éditions fournies par l'API. Cette application ne modifie ni le texte coranique ni les traductions.

## Confidentialité

Pas de compte utilisateur. Pas de publicité ni d'analyse. Les favoris, les préférences et les sourates téléchargées restent localement sur l'appareil. Les demandes réseau nécessaires concernent Al Quran Cloud et GitHub Releases.
