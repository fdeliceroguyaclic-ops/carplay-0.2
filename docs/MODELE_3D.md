# Modèle 3D de la Polo V (6R) — obtention et préparation

L'appli **n'embarque aucun modèle de Polo 6R** : je n'en connais pas de libre de droits dont la licence soit
vérifiée. En attendant, une silhouette 2D originale est affichée. Tout fichier GLB importé est copié dans le
stockage interne de l'appli (rien n'est envoyé en ligne).

## 1. Où trouver un modèle (en respectant les licences)
- **Sketchfab** : cherchez « Volkswagen Polo 6R » ou « Polo V 2010 », filtre *Downloadable*.
  Vérifiez la licence (CC-BY = gratuit avec mention de l'auteur ; CC-NC = usage non commercial, OK pour un usage personnel).
  Vérifiez bien qu'il s'agit d'une **6R 5 portes (2009-2014)**, pas d'une 6C restylée (2014+, boucliers différents) ni d'une Polo VI.
- **CGTrader / TurboSquid** : modèles payants plus détaillés (achat ponctuel, facultatif).
- **Créer le sien** : Blender (gratuit) à partir de plans (*blueprints*) de Polo 6R 5 portes.

## 2. Préparation dans Blender (gratuit)
1. Importez le modèle, supprimez ce qui est inutile (moteur, dessous de caisse détaillé, intérieur si non voulu).
2. **Allégez** : modificateur *Decimate* → viser **80 000 à 150 000 triangles** au total.
   Textures en **2048 px maximum** (1024 conseillé), format JPG/PNG.
3. Orientation : l'avant du véhicule vers **-Y dans Blender** (on voit la calandre en vue de face, pavé num. 1),
   ce qui donne +Z en glTF ; roues posées au sol.
   L'appli centre et met à l'échelle automatiquement (≈ 3,97 m).
4. **Séparez** en objets distincts : 4 portières, hayon, et origine (pivot) de chaque objet **sur sa charnière**.
5. **Nommez** (le nom de l'objet OU du matériau suffit) :

| Élément | Nom contenant | Effet dans l'appli |
|---|---|---|
| Peinture carrosserie | `paint`, `body` ou `carrosserie` | changement de couleur |
| Phares | `headlight` ou `phare` | allumage (émissif) |
| Clignotant gauche / droit | `blinker_l` / `blinker_r` (ou `clignotant_g` / `clignotant_d`) | clignotement |

6. **Animations** (une *Action* par ouvrant, image 0 = fermé, dernière image = ouvert) :
   `door_fl` (avant gauche), `door_fr`, `door_rl`, `door_rr`, `trunk` (hayon).
   Dans l'éditeur NLA, poussez chaque action en piste (*Push Down*) pour qu'elles soient toutes exportées.
7. *Fichier › Exporter › glTF 2.0* : format **glTF Binary (.glb)**, cocher *Animation* et *Tous les actions*,
   *Compression* désactivée (Draco non pris en charge par le moteur).
8. Testez le fichier sur https://gltf-viewer.donmccurdy.com puis importez-le : onglet **Ma Polo › Importer un modèle**.

## 3. Embarquer le modèle dans l'APK
Copiez `polo.glb` dans `app/src/main/assets/models/` puis recompilez. Un modèle importé depuis l'appli reste prioritaire.

## 4. Vue intérieure
La caméra intérieure est placée à la place du conducteur (conduite à gauche) d'après des cotes moyennes.
Si le modèle n'a pas d'habitacle, la vue montrera l'intérieur vide de la carrosserie.
