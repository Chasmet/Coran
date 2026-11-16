# Mon Coran – Lire & Écouter — Audit de préparation « Sécurité des données » (Google Play)

**Date :** 15 novembre 2026 · **Éditeur :** Sync 30 Numérique · **Dépôt :** `Chasmet/Coran` · **Branche auditée :** `feature/play-sync30-coran-20261115`.

> Document technique de contrôle, **pas une déclaration Google Play finale**. Ne pas cocher automatiquement « Aucune donnée collectée » avant contrôle du trafic réel et des politiques des fournisseurs tiers. Le formulaire Google Play est encore à compléter.

## Identité et modèle de distribution

- Module Google Play : `playApp`; package `com.sync30.moncoran`; minSdk 24; targetSdk 36.
- Application personnelle séparée : `app`, package `com.chk.coran`; **ne pas modifier ses données ni son package**.
- Aucune librairie publicitaire, de facturation ou de mesure d'audience ajoutée par le fichier `playApp/build.gradle` (dépendances : `androidx.core:core:1.13.1` et `androidx.work:work-runtime:2.9.1`).
- `ENABLE_GITHUB_UPDATE=false` dans les builds Play : l'installation d'APK GitHub est désactivée, et la version Play n'a pas de permission `REQUEST_INSTALL_PACKAGES`.
- Politique produit : gratuite au téléchargement, sans publicités, achats intégrés, abonnements ni compte.

## Permissions du manifeste public

| Permission | Usage d'après le code | Point de contrôle |
| --- | --- | --- |
| `INTERNET` | Récupérer textes, traductions, métadonnées et fichiers audio | Contrôler les hôtes réellement contactés |
| `ACCESS_NETWORK_STATE` | Vérifier l'état réseau pour les tâches | Pas d'identifiant personnel envoyé déduit de cette permission seule |
| `POST_NOTIFICATIONS` | Notifications d'audio/téléchargement selon version Android | Tester la demande et le refus de permission |
| `WAKE_LOCK` | Lecture et téléchargements en tâche de fond | Vérifier la gestion de batterie |
| `FOREGROUND_SERVICE` | Service visible pendant lecture et téléchargement | Déclarer type exact en Console |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Audio de récitations | Vérifier justification et notification |
| `FOREGROUND_SERVICE_DATA_SYNC` | Téléchargement WorkManager | Vérifier les politiques API 36 pertinentes |

Android manifeste : `playApp/src/main/AndroidManifest.xml`; `usesCleartextTraffic=false`.

## Circulation de données constatée dans le code

1. `QuranRepository.load()` utilise l'URL HTTPS `https://api.alquran.cloud/v1/surah/{numero}/editions/quran-uthmani,fr.hamidullah,en.sahih` pour les textes non stockés.
2. `AudioRepository.audioUrl()` interroge l'API HTTPS `https://api.alquran.cloud/v1/surah/{numero}/{edition_audio}`.
3. `AudioRepository.ensureAudio()` télécharge les MP3 depuis les URLs HTTPS des métadonnées, contrôlées par liste de domaines `cdn.islamic.network`, `cdn.islamic.ru`, `cdn.alislam.ru`, dans le chemin `/quran/audio/`.
4. Ces requêtes exposent au fournisseur et au réseau au minimum l'adresse IP et des informations HTTP techniques, ainsi que l'identifiant de sourate/édition demandé ; la portée des journaux, finalités, durée et statut RGPD des fournisseurs ne sont **pas audités par le seul code de l'application**.
5. Les favoris, position de lecture et préférences sont enregistrés localement. Le code examiné ne montre pas d'envoi direct de ces préférences à un serveur Sync 30.
6. Le cache JSON des sourates et les MP3 sont enregistrés dans le stockage privé de l'application. Le manifeste autorise `android:allowBackup=true` et les règles de sauvegarde incluent `sharedpref` ainsi que `files/surahs/` ; les sauvegardes Android peuvent dépendre des réglages de l'utilisateur et de Google.
7. Les URL GitHub sont présentes dans le code partagé, mais le contrôle d'updates GitHub dépend de `BuildConfig.ENABLE_GITHUB_UPDATE`, désactivé pour `playApp`. Tester en exécution réelle qu'aucune requête GitHub n'a lieu dans la version publique.

## Ce qui peut être déclaré après vérification

- L'application **n'exige aucun compte** et ne prévoit aucun SDK de publicité ou analytics personnalisé.
- Les téléchargements vers Al Quran Cloud et ses CDN passent par HTTPS selon le code ; vérifier avec capture réseau sur appareil.
- Le texte et les favoris restent locaux, sous réserve de la sauvegarde Android et de l'examen complet du code compilé.
- Pour les champs Google Play relatifs à « collected », « shared », « identifiers » et « third-party processing », consulter les définitions en vigueur ainsi que la confidentialité d'Al Quran Cloud : l'adresse IP et les journaux de connexion peuvent influencer les réponses.
- Pour l'effacement, documenter l'effacement des données locales dans Paramètres Android et les éventuelles sauvegardes système ; ne pas promettre de suppression des journaux d'un prestataire non contrôlé.

## Blocages de validation

- La politique dédiée `Chasmet/sync30/coran-confidentialite.html` est créée sur GitHub mais son URL de production `https://sync30.onrender.com/coran-confidentialite.html` a renvoyé **HTTP 404** au contrôle ; ne pas la saisir dans la Console avant HTTP 200 vérifié.
- Contrôle réseau réel en version Play release/debug sur téléphone : **non effectué** dans cette intervention.
- Licences et crédits des traductions et des enregistrements audio : confirmation par titulaire de droits **encore nécessaire**.
- Formulaire Google Play **Data safety non envoyé** ; les réponses doivent être basées sur l'artefact final testé et les pratiques de tous les SDKs/fournisseurs.

## Tests à exécuter avant soumission

1. Compiler la variante publique, inspecter manifest fusionné et dépendances de l'AAB signé.
2. Lancer 3 langues, favoris, audio, téléchargement, mode hors-ligne et notifications sur appareil.
3. Observer les requêtes HTTPS au premier lancement, à la lecture, et en arrière-plan ; contrôler l'absence de trafic indésirable.
4. Vérifier les règles de sauvegarde Android et le comportement à l'effacement des données.
5. Renseigner la Data safety puis relire le récapitulatif, sans extrapoler depuis le nom de l'application.

**Statut : PRÉPARÉ / CONTRÔLE DES SOURCES — tests réels, déclarations et publication RESTANTS.**
