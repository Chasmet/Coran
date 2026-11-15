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
- Package saisi dans Play Console : `com.sync30.moncoran`, **vérification de disponibilité / création effective encore à confirmer**
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

## État au 15 novembre 2026

- FAIT : audit du dépôt et création d'une branche de préparation séparée ; configuration de la variante Play.
- À TESTER : résultat GitHub Actions, AAB signé, certificat, fonctionnement sur appareil.
- NON FAIT : création de la fiche Play, import AAB, déclarations, publication.
- RISQUE : droits des contenus tiers à créditer ; vérifier les conditions en vigueur.
- IMPORTANT : aucune clé privée ni mot de passe dans ce dépôt.
