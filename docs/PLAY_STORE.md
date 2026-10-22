# Publication sur le Play Store

Tout ce qu'il faut pour la fiche de `thibautperrin.tetrano` (versionCode 19, versionName 5.0).

## Fichiers prêts à l'upload

`fastlane/metadata/android/<langue>/` (format fastlane, lisible aussi par F-Droid), pour
`en-US`, `fr-FR`, `de-DE`, `es-ES`, `it-IT`, `ru-RU` :

| Fichier | Champ de la Play Console | Limite |
|---|---|---|
| `title.txt` | Nom de l'application | 30 caractères |
| `short_description.txt` | Description courte | 80 caractères |
| `full_description.txt` | Description complète | 4 000 caractères |
| `changelogs/19.txt` | Notes de version (« Nouveautés ») de la 5.0 | 500 caractères |
| `images/icon.png` | Icône de l'application, 512 × 512 | |
| `images/featureGraphic.png` | Image de présentation, 1 024 × 500 | |
| `images/phoneScreenshots/*.png` | Captures d'écran du téléphone, 1 080 × 1 920 | 2 à 8 |

Les captures sont refaites par `python tools/store/screenshots.py <langue>` (bannière : `tools/store/feature_graphic.py`) (voir l'en-tête du script).

## Réponses aux formulaires « Contenu de l'application »

- **Règles de confidentialité** : `https://github.com/thibautperrin/Tetrano/blob/main/PRIVACY.md`
- **Annonces** : l'application ne contient pas d'annonces.
- **Accès à l'application** : toutes les fonctionnalités sont accessibles sans restriction
  (pas de compte, pas de connexion).
- **Classification du contenu** (questionnaire IARC) : catégorie « Jeu » ; aucune violence, aucune
  peur, aucun contenu sexuel, aucun langage grossier, aucune substance, aucun jeu d'argent, aucune
  interaction entre utilisateurs, aucun partage de position, aucun achat numérique. Résultat
  attendu : tout public (PEGI 3 / ESRB Everyone).
- **Public cible** : 13 ans et plus (le jeu convient à tous, mais ne cibler que les adultes et
  adolescents évite les exigences du programme « Familles »).
- **Sécurité des données** : l'application ne collecte aucune donnée et n'en partage aucune.
  - Collecte : non. Partage : non.
  - L'application n'a pas la permission Internet : rien ne sort du téléphone. La sauvegarde
    Android (Auto Backup) est une fonction du système, sous le compte Google de l'utilisateur ; elle
    n'est pas une collecte par le développeur.
- **Application d'actualités / gouvernementale / financière / santé** : non.
- **Catégorie** : Jeux › Jeux de société. Tags suggérés : Jeu de société, Stratégie, Réflexion.
- **Coordonnées** : adresse e-mail de contact (obligatoire) ; site web :
  `https://github.com/thibautperrin/Tetrano`.

## Publication

1. Générer l'AAB signé : `./gradlew bundleRelease`, puis le signer avec la clé d'upload (la clé
   n'est pas dans ce dépôt).
2. Le déposer en **test interne**, lire le rapport de pré-lancement (plantages, accessibilité).
3. L'installer depuis le Play Store par-dessus la 4.1 sur un téléphone de test : parties
   enregistrées, niveau du Challenge et réglages doivent être conservés.
4. Passer en production avec un déploiement progressif (par exemple 20 %, puis 100 %).
