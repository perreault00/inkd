# INKD 0.1
Lecteur EPUB Android expérimental, avec effet de rafraîchissement E-Ink simulé.

## Construire sur GitHub sans PC
1. Crée un dépôt GitHub **public** nommé `INKD`.
2. Téléverse les fichiers du projet en conservant leurs dossiers (ou utilise github.dev depuis ton navigateur pour ajouter les fichiers).
3. Ouvre **Actions > Build APK > Run workflow**.
4. Quand le workflow est terminé, ouvre son exécution et télécharge l'artifact **INKD-APK**.
5. Décompresse le ZIP téléchargé et installe `app-debug.apk`.

## Limites v0.1
- EPUB 2/3 XHTML simples, texte uniquement : images, CSS du livre, notes, tableaux complexes et DRM non pris en charge.
- La progression est sauvegardée par chapitre, pas par page.
- Le flash est un effet visuel, pas une transformation physique de l'écran AMOLED.
- Le projet n'est pas encore testé sur un téléphone réel.
