# Mon Coran – Lire & Écouter | Préparation Google Play

**Éditeur : Sync 30 Numérique** · **Prix : 0 € définitivement** · **Publicités : non** · **Achats intégrés : non**.

## Applications indépendantes

| Canal | Module | applicationId | Distribution |
| --- | --- | --- | --- |
| Personnel | `app` | `com.chk.coran` | APK signé GitHub Releases, mise à jour via SkillMajAuto |
| Google Play | `playApp` | `com.sync30.moncoran` | AAB signé et mises à jour exclusivement par Google Play |
| Test Play | `playApp` debug | `com.sync30.moncoran.dev` | APK QA distinct |

La version personnelle **ne doit pas être désinstallée**. Les données et favoris des deux packages sont séparés. Aucun transfert automatique des données n'est garanti.

## Branches et compilation

Source : `Chasmet/Coran`. Branche de préparation : `feature/play-sync30-coran-20261115`.
Branche principale distribuée : `main`.

- `./gradlew :app:assembleDebug` : APK personnel debug.
- `./gradlew :playApp:assembleDebug` : APK de test Google Play (différent).
- `./gradlew :playApp:bundleRelease` : AAB (sans signature valide si les secrets sont absents).
- Le workflow `.github/workflows/google-play.yml` déclenche la compilation de test sur la branche et utilise le signataire Coran existant via OIDC pour la release signée **uniquement sur main**.

**Ne jamais importer un AAB construit sur la branche sans signature confirmée.** La première release Play nécessite la comparaison de l'empreinte du certificat d'upload à la console, l'inspection du manifeste AAB, et la vérification du versionCode.

## Identité Play à verrouiller avant le premier import

- Nom choisi et confirmé par capture Play Console : Mon Coran – Lire & Écouter
- La fiche **Mon Coran – Lire & Écouter** existe dans Play Console (tableau de bord et fiche française en brouillon vérifiés le 15/11/2026). Le module Play utilise `com.sync30.moncoran` ; vérifier que l'identifiant est accepté à l'import du premier AAB.
- App : oui ; Free : oui ; Billing : non ; ads : non
- API cible : 36 ; Android min 24 pour la version Play (à confirmer contre l'artefact)
- Catégorie suggérée : Livres et références
- Éditeur public : Sync 30 Numérique (compte Organisation)
- Contact : vérifier sur l'application Play le site et l'email professionnels réellement actifs avant de les enregistrer
- Ne pas reprendre mécaniquement les anciens liens PNTR ou Render : ils divergent dans les dossiers datés

## Checklist avant une soumission

1. Tests GitHub Actions verts, vérification du paquet, de la signature et du versionCode.
2. Test réel sur téléphone : 3 langues, 114 sourates, lecture, favoris, cache, lecteur audio, téléchargement, arrêt/reprise, hors réseau.
3. Tester les permissions et notification du service audio Android 14–16.
4. Confirmer les droits des traductions et récitations et afficher les crédits. Source Al Quran Cloud et les auteurs respectifs.
5. Politique de confidentialité **propre au Coran**, accessible en HTTPS.
6. Déclaration Data safety basée sur le trafic réseau réel vers `api.alquran.cloud` et CDN, sans réponse inventée.
7. Assets véritables : icône 512x512, bannière 1024x500 et captures originales en français/anglais/arabe.
8. Fiche Play enregistrée, IARC, public cible, pays, statut commercial (trader) et déclarations correctes.
9. AAB uploadé, release soumise, statut « en cours d'examen », puis « Production Active » séparément confirmés.
10. Vérifier installation depuis Play, données et mises à jour.

## État vérifié au 15 novembre 2026 (18 h, France)

- **FAIT** : application « Mon Coran – Lire & Écouter » retrouvée sur la console Google Play. La fiche principale en français est en **brouillon** et comporte déjà le nom, la description courte et la description longue (respectivement 26/30, 73/80 et 1237/4000 caractères dans la console).
- **FAIT** : code Play séparé du canal personnel, avec package `com.sync30.moncoran`, API cible 36, 0 publicité, 0 achat intégré et mises à jour externes désactivées.
- **TESTÉ** : workflow de préparation GitHub Actions **exécution n° 12**, statut **success**, branche `feature/play-sync30-coran-20261115`, commit `092838fcd8afdab3affd37711e747ede504582db` ; archive APK QA `coran-play-test-12`. La compilation de contrôle de l'AAB n'est **pas** une validation de signature.
- **FAIT (inventaire local CHK)** : `Categories/Images/application/application religieuse/application Coran/image visuel et logo/Play_Store_Prepa` contient `01_Logo_Mon_Coran_512x512.png` (512×512), `02_Banniere_Mon_Coran_1024x500.png` (1024×500) et les captures `tmp_03_Accueil.jpg`, `tmp_04_Francais.jpg`, `tmp_05_Anglais.jpg`, `tmp_06_Arabe.jpg`. La capture Accueil mesure 935×1920, soit un ratio de plus de 2:1 : vérifier les quatre captures avant import (préférer 1080×1920 sans déformer l'interface).
- **BLOQUÉ / À FAIRE** : l'ancienne tentative de signature distante sur la branche a renvoyé HTTP 403 (exécution n° 11) ; le workflow a été corrigé pour ne signer qu'à partir de `main`. **Aucun AAB signé Play n'a été vérifié ou importé.**
- **À FAIRE** : valider les droits des traductions et récitations, publier la politique de confidentialité propre à l'application sur une URL HTTPS, vérifier le contenu du formulaire Data safety et finaliser les déclarations de Console.
- **À FAIRE** : téléverser l'icône, la bannière et les captures conformes, enregistrer la fiche, contrôler la signature AAB, tester en interne, puis seulement envisager la soumission.
- **STATUT** : la PR `#1` reste en **brouillon** ; la branche `main`, le package personnel et son canal de mises à jour ne sont pas modifiés par cette préparation. Google Play **non publié** ; aucune revue Google en cours n'a été constatée.
- **SÉCURITÉ** : aucun certificat privé, jeton ou mot de passe ajouté aux notes ou au dépôt.

## Suivi complémentaire du 15 novembre 2026 — 19 h 45

- **FAIT ET VÉRIFIÉ** : dans Play Console pour « Mon Coran – Lire & Écouter », le formulaire **Ads / Publicités** a été enregistré sur **« No, my app does not contain ads »**. Vérification après rechargement : radio « No » encore sélectionné. Retour au tableau de bord : **« 1 of 11 complete »**, rubrique Ads cochée.
- **NON VALIDÉ** : formulaire **Sign in details** : option « No » (aucune partie restreinte, pas de compte ni de paiement) sélectionnée à l'écran mais non persistante au rechargement ; tableau de bord resté à 1/11. Ne pas annoncer cette tâche terminée.
- **BLOQUÉ** : navigation vers le formulaire « Government apps » sans résultat confirmé ; navigateur Android partagé avec d'autres onglets et actions concurrents. Ne pas rejouer de clic incertain sans inspection de la page.
- **FAIT, NON DÉPLOYÉ** : politique `coran-confidentialite.html` créée dans `Chasmet/sync30`, commit `12a46035faeb9099d4766e596a2d29e7808d7e62`. L'URL `https://sync30.onrender.com/coran-confidentialite.html` a répondu HTTP 404 au contrôle. L'accès Render demande explicitement une sélection d'espace de travail confirmée par le propriétaire ; aucun service Render n'a été modifié. Ne PAS inscrire une URL en HTTP 404 dans Play Console.
- **TESTÉ** : Google Play CI n° 16, commit `2d83e528af96ac11a506454b3a92c152c49b1d21`, terminé success avec APK de test. Pas d'AAB signé release ; signature réservée à main.
- **RESTE** : confirmer l'espace Render, rendre la politique accessible publiquement en HTTPS, finaliser 10 tâches Play Console, vérifier licences de contenus audio/traductions, obtenir et valider l'AAB release signé, tester la piste interne, soumettre puis vérifier le statut exact. Branche `main` et package personnel `com.chk.coran` préservés.


## Reprise du 15 novembre 2026 — contrôle après 21 h (heure de France)

- **TESTÉ — Play Console : 5/11 tâches cochées.** Après navigation sur le tableau de bord de *Mon Coran – Lire & Écouter* (ID application 4972931573060769119), les tâches Sign in details, Ads, Government apps, Financial features et **Health** apparaissent cochées. Health était auparavant incertaine : la sauvegarde est maintenant confirmée par le tableau de bord.
- **TESTÉ — Store settings :** type d'application **App** et catégorie **Books & Reference** affichés après chargement de la page réelle ; la catégorie est persistée, inutile de la refaire. Les champs email, téléphone, site des coordonnées de la fiche apparaissent encore vides ; aucun enregistrement de contact confirmé.
- **RESTE :** Privacy policy, Content rating, Target audience, Data safety, Store settings (coordonnées), Store listing (confirmation finale). La déclaration de transparence des visuels IA doit aussi être relue.
- **FAIT — Code :** ajout d'un bouton « Politique complète » dans la fenêtre Confidentialité des réglages de la variante Play. Il ouvre l'URL prévue https://sync30.onrender.com/coran-confidentialite.html. Commit `aceeb24087c576d78035e0d347a9c1cc2527c084`; fichier relu sur la branche. Le paquet personnel existant n'a subi aucune installation/désinstallation.
- **TEST DOCUMENTAIRE — droits :** conditions d'Al Quran Cloud consultées (https://alquran.cloud/terms-and-conditions). Le texte coranique est utilisable avec fidélité et attribution ; les traductions et les récitations restent sous droits de leurs auteurs, et les enregistrements sont autorisés pour des usages personnels/éducatifs sous les conditions du fournisseur. Les crédits nominaux sont déjà visibles dans les réglages et la proposition de fiche. Le test sur APK final et le contrôle des éditions/audio restent nécessaires.
- **CONFIDENTIALITÉ / RENDER :** fichier HTML présent sur GitHub `Chasmet/sync30` branche main ; URL publique non revalidée en HTTP 200 durant cette reprise. Ne pas renseigner une URL non vérifiée sur Play. L'API Render a révélé un espace de travail « My Workspace » mais demande une confirmation préalable de sélection avant de consulter ou modifier les services ; aucune mutation Render effectuée.
- **SIGNATURE :** workflow de CI de préparation vérifié ; signature release strictement réservée à main. Aucun nouveau certificat ni AAB signé contrôlé, aucun envoi à Play et aucune publication.
- **NOTES CHK :** CHK Agent Browser a retourné « Navigateur hors ligne » à la lecture des notes. La présente section GitHub conserve les observations en attendant leur synchronisation vers les notes CHK. Ne pas annoncer que les notes Android ont été actualisées.


## État confirmé 16 novembre 2026 — Console Google Play

- **FAIT — Play Console** : les 11 formalités initiales sont enregistrées. Transition vérifiée : Target audience coché à 10/11, puis sauvegarde de Data safety et disparition des formalités initiales du tableau de bord. Pistes de test déverrouillées ; ceci ne signifie pas publication.
- **Data safety enregistrée** : App interactions (Collected + Shared), données nécessaires aux fonctionnalités, requêtes HTTPS, aucun compte, demandes de suppression via https://sync30.onrender.com/coran-confidentialite.html. Trafic réel et pratiques des tiers à vérifier.
- **Fiche Play** : catégorie Books & Reference, contact professionnel et politique HTTPS enregistrés ; français, icône, bannière et 4 captures ; IARC et autres déclarations cochés. Produit Free, sans publicité, achat ni abonnement.
- **Dernier contrôle CI connu** : run GitHub Actions n°18, commit 97fb83911bcfc39f7678565704e3a688c3ffd2b2, artefact coran-play-test-18 = APK QA uniquement. Ne pas confondre bundle de contrôle et AAB release signé.
- **TEST INTERNE** : 0/3 tâches terminées au tableau de bord : tester(s), nouvelle release puis confirmation. Aucun AAB signé téléversé.
- **SIGNATURE** : .github/workflows/google-play.yml ne signe que sur branche main avec signataire OIDC existant et vérification SHA-256. Ne pas fusionner la PR préparatoire avant validation des licences et tests. AAB release signé non produit/vérifié.
- **LICENCES** : Al Quran Cloud autorise les textes fidèles avec attribution mais les traductions/récitations appartiennent à leurs titulaires. Vérifier droits pour publication par entreprise individuelle, même sans publicité et gratuite.
- **PERSONNELLE** : préserver com.chk.coran et ses données ; com.sync30.moncoran (public) et com.sync30.moncoran.dev (test) restent distincts.
- **NON FAIT** : test réel AAB signé, piste interne active, soumission en revue, publication en production.
