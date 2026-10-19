# Multi-Skin

Mod Fabric pour Minecraft **1.21.1** qui ajoute des skins personnalisés, la
gestion des parties du corps et le réglage de la taille des joueurs.

## Skins

Le menu des skins s'ouvre avec **F12**. Il permet de créer, modifier, dupliquer
et supprimer des skins, de les organiser dans des dossiers, et de choisir leur
modèle (`slim` ou `steve`), leur taille et les parties du corps à masquer. Les
images peuvent être chargées depuis une URL ou un fichier local.

La bibliothèque locale est stockée dans `multi-skin/skins/` dans le dossier du
jeu. La taille d'un skin peut aller de **50 à 300 cm** ; la valeur par défaut
est **185 cm**.

Les commandes de skin s'appliquent au joueur qui les exécute :

```text
/skin slim <url> [taille_en_cm]
/skin steve <url> [taille_en_cm]
/skin reset
```

Les opérateurs peuvent aussi appliquer ou réinitialiser les skins d'autres
joueurs :

```text
/skinadmin <players> slim <url> [taille_en_cm]
/skinadmin <players> steve <url> [taille_en_cm]
/skinadmin <players> reset
```

## Parties du corps

Les opérateurs peuvent masquer ou rétablir les bras et les jambes des joueurs.
Les parties choisies dans un skin sont appliquées avec ce skin.

```text
/bodypart remove <players> arm right
/bodypart add <players> leg left
/bodypart reset <players>
```

## Taille

La taille modifie l'échelle du joueur. Les effets de la taille sur la vitesse de
déplacement et la portée se règlent par les opérateurs avec :

```text
/multiskinconfig get
/multiskinconfig size speed <coefficient>
/multiskinconfig size reach <coefficient>
/multiskinconfig reload
```

Les coefficients sont compris entre `0.0` et `2.0`. La configuration est
enregistrée dans `config/multi-skin/config.json`.

## Installation

Nécessite Minecraft `1.21.1`, Fabric Loader, Fabric API, Java 21 ou supérieur,
et Pehkui `3.8.3` ou supérieur. Placez le fichier `.jar` du mod et ses
dépendances dans le dossier `mods` du profil Fabric.
