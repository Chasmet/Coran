# Coran — application Android

Application mobile pour lire le Saint Coran en **arabe**, **français** et **anglais**, écouter des enregistrements authentiques français, anglais et arabes et mémoriser les favoris et la progression.

## Fonctions

- 114 sourates, recherche rapide
- Texte arabe Uthmani et traductions Hamidullah (FR) / Saheeh International (EN)
- Écran de démarrage à 3 onglets 🇫🇷 / 🇬🇧 / 🇸🇦 puis interface dédiée à chaque langue
- Audio véritable par verset : Youssouf Leclerc (FR), Ibrahim Walk (EN), Mishary Alafasy (AR)
- Écoute, pause, barre de progression, passage automatique facultatif au verset suivant
- Téléchargement audio individuel par langue pour écoute hors connexion
- Sourates consultées mises en cache local pour lecture hors connexion
- Favoris et dernière position de lecture persistants
- Mode sombre système, taille du texte, sélection des langues
- Mise à jour intégrée via l'API publique GitHub `releases/latest` (canal de distribution directe)

## Compiler

JDK 17, Android SDK 34, Gradle 8.9 : `./gradlew assembleDebug`.

GitHub Actions compile automatiquement à chaque push sur `main` avec le wrapper versionné.

## Publication officielle

Le workflow `.github/workflows/android.yml` obtient la signature permanente de Coran auprès du service Render existant, via l’identité GitHub Actions OIDC. Il vérifie le certificat, l’identifiant `com.chk.coran` et le numéro de version, puis publie `coran-release.apk` dans GitHub Releases avec son empreinte SHA-256. L’application consulte les Releases publiques sans compte ni token.

L'application vérifie le propriétaire du dépôt et l'empreinte SHA256 annoncée par la Release, puis Android contrôle la signature et demande l'accord d'installation. Le package `com.chk.coran` reste inchangé pour conserver les données.

**Note Play Store :** ce canal APK GitHub est destiné à la distribution directe. Une variante Google Play devra utiliser Google Play In-App Updates conformément aux règles de la boutique.

## Contenu et crédits

Service : [Al Quran Cloud](https://alquran.cloud/api) et [CDN Islamic Network](https://alquran.cloud/cdn). Le Coran arabe, les traductions et les récitations doivent être présentés fidèlement. Traductions et audio conservent leurs droits respectifs. Les textes restent attribués aux éditions fournies par l'API. Cette application ne modifie ni le texte coranique ni les traductions.

## Confidentialité

Pas de compte utilisateur. Pas de publicité ni d'analyse. Les favoris, les préférences et les sourates téléchargées restent localement sur l'appareil. Les demandes réseau nécessaires concernent Al Quran Cloud et GitHub Releases. Les éditions audio et leurs traductions peuvent être d'auteurs différents. Aucun fichier audio d'une langue n'est affiché comme provenant d'un autre récitant.

## Navigation

Lancement : trois drapeaux / choix de langue. Le répertoire des sourates, les versets et les boutons de lecture fonctionnent dans l'onglet correspondant. Un onglet permet de changer de langue sans perdre les favoris. Paramètres : langues complémentaires, taille, enchaînement audio, mise à jour.


## SkillMajAuto — procédure GitHub signée

SkillMajAuto est une méthode intégrée au code de Coran, et non une application à installer.

* À l'ouverture, vérification GitHub Releases et planification WorkManager.
* Toutes les 12 heures environ (sans garantie Android), nouveau contrôle réseau et notification de disponibilité.
* Les réglages permettent de désactiver la surveillance et de déclencher une recherche immédiate.
* Téléchargement avec DownloadManager, contrôle SHA256, nom du package `com.chk.coran`, versionCode supérieur et certificat Android identique avant installation.
* Demande d'autorisation Android avant l'installation, sans désinstaller ni effacer les favoris ou les sourates.
* L'APK de test est maintenant isolé avec `com.chk.coran.dev` : il ne remplace pas un APK officiel et ne peut pas être converti en Release par simple renommage.
* GitHub Actions conserve un APK de test dans Artifacts et publie le canal officiel avec la même signature permanente.

Note : les anciens APK debug diffusés sous `com.chk.coran` ne peuvent pas être remplacés par une Release avec une signature différente. Une installation initiale officielle avec la clé persistante est nécessaire.

## Écoute de sourates complètes (version 1.0.10 et ultérieures)

Le nouveau deuxième onglet **🎧 Écouter** est accessible depuis la liste des sourates et dispose des mêmes 3 langues que l'écran de lecture : français (Youssouf Leclerc), anglais (Ibrahim Walk), arabe (Mishary Alafasy).

- Appuyer sur **▶** pour démarrer une sourate entière depuis le début : les versets MP3 sont enchaînés dans l'ordre.
- Appuyer sur **↓** pour télécharger *tous les versets* de la sourate dans la langue sélectionnée. Une confirmation « Téléchargé · hors connexion » apparaît à la fin.
- Même téléphone verrouillé : le lecteur Android `SurahPlaybackService` utilise un service de premier plan `mediaPlayback`, une `MediaSession`, une notification Android (pause, reprise, précédent, suivant, arrêt), un verrouillage CPU limité et la gestion des interruptions par appel/écouteurs.
- Le téléchargement est confié à WorkManager + notification en `dataSync` et peut se poursuivre si l'interface est fermée ; il est relançable après un échec réseau.
- Les fichiers MP3 sont enregistrés **localement par langue et numéro de verset**, réutilisant le cache existant : aucune duplication de la base de sourates, aucune suppression de favoris. L'audio nécessite Internet au premier téléchargement, puis fonctionne sans connexion.
- L'écoute en cours est affichée en bas du nouvel écran. La fermeture de l'écran ne coupe pas la lecture. L'arrêt explicite sur **■** libère le service.
- Sur Android 13+, l'autorisation de notification est demandée pour les commandes visibles. Sur Android 14, les types de service `mediaPlayback` et `dataSync` sont déclarés dans le manifeste.

Les APK de test `com.chk.coran.dev` restent séparés de l'application stable et ne doivent pas être présentés comme des APK à signature pérenne.
