# POLO DIGITAL COCKPIT

Interface multimédia personnalisée pour autoradio Android 12 (AC8257, 1024 × 600), installée dans une
Volkswagen Polo V (6R) 1.2 60 ch, 5 portes. Kotlin + Jetpack Compose + Filament (3D).

> Version 0.2.0 — correctifs de fiabilité : export du rapport compatible Android 8+, sonde CanBox
> (capture guidée + associations en lecture seule), OBD2 avec test de connexion (Bluetooth et Wi-Fi).
>
> **Important :** l'autoradio annonce « Android 12 », mais l'erreur `MediaStore$Downloads` prouve qu'il exécute
> en réalité Android 9 ou moins (API ≤ 28). L'appli est désormais contrôlée contre l'API 26 (Android 8.0).
> La version réelle s'affiche dans Réglages › Système › Diagnostic.

---

## 1. Analyse de l'autoradio

| Élément | Constat | Conséquence pour l'appli |
|---|---|---|
| « Android 12 » affiché | Système réel ≤ Android 9 (API ≤ 28), prouvé par l'absence de `MediaStore.Downloads` | `minSdk 26` ; code Android compilé contre l'API 26 |
| Plateforme ac8257 | SoC Autochips (groupe MediaTek), GPU OpenGL ES 3.x | Rendu 3D via Filament (OpenGL ES 3.0) |
| « Cortex-A7 8 cœurs, 6 Go / 128 Go » | Les infos affichées par ces autoradios sont souvent approximatives | APK 32 **et** 64 bits ; budget visé < 250 Mo de RAM ; rendu 3D uniquement quand l'image change |
| Écran 1024 × 600 | Densité variable selon le firmware (160 ou 240 dpi) | Mise en page en `dp` avec poids ; tout tient sans défilement sur l'accueil |
| MCU JCST + CanBox Raise VW-RZ-08 | La CanBox parle à la MCU en série ; la MCU relaie à Android via un service **propriétaire**, sans API publique | Architecture à sources ; sonde passive pour identifier le service ; OBD2 en solution alternative |
| Projection | ZLink/TLink/AutoKit (à confirmer) | L'appli **ouvre** l'appli de projection existante, sans toucher à CarPlay/Android Auto |

## 2. Architecture

```
fr.polo.cockpit
├── PoloApp                 conteneur de dépendances (pas de framework : APK léger)
├── MainActivity            plein écran paysage, écran toujours allumé
├── data/SettingsStore      préférences locales (DataStore) : couleurs, widgets, raccourcis…
├── vehicle/
│   ├── VehicleModels       Reading<T> = valeur + ORIGINE (CanBox / OBD2 / Estimé / SIMULATION / Indisponible)
│   ├── VehicleSource       interface commune à toutes les sources (lecture seule)
│   ├── DemoSource          simulation clairement étiquetée
│   ├── ObdElm327Source     OBD2 Bluetooth, liste blanche « mode 01 » uniquement
│   ├── CanBoxProbe         rapport de compatibilité + écoute de diffusions (passif)
│   └── VehicleRepository   fusion par priorité : CanBox > OBD2 > démo
├── apps/AppLauncher        raccourcis, détection CarPlay, mode écran d'accueil, désinstallation
├── weather/                Open-Meteo (gratuit, sans clé) — masqué hors ligne
├── render/CarRenderer      Filament : GLB, orbite tactile, zoom, vues, portes, coffre, phares, clignotants, teinte
└── ui/                     Compose : Accueil, Véhicule, Ma Polo (3D), Applis, Réglages
```

Règle d'or : **chaque valeur affichée porte une étiquette d'origine**. Une donnée simulée n'est jamais
présentée comme réelle ; une valeur calculée est marquée « Estimé ».

## 3. Compiler l'APK

### Option A — GitHub Actions (rien à installer, gratuit)
1. Créez un dépôt GitHub (privé si vous voulez) et envoyez-y ce dossier.
2. L'onglet **Actions** lance automatiquement « Compiler l'APK » (ou bouton *Run workflow*).
3. À la fin (~6 min), téléchargez l'artefact **PoloDigitalCockpit-apk** : il contient `PoloDigitalCockpit.apk`.

### Option B — Android Studio (PC)
1. Installez Android Studio (version Koala 2024.1 ou plus récente).
2. *File › Open* → ce dossier. Laissez la synchronisation Gradle se terminer.
3. *Build › Build App Bundle(s) / APK(s) › Build APK(s)*.
4. L'APK est dans `app/build/outputs/apk/debug/` (ou `release/` via `./gradlew assembleRelease`).

### Option C — ligne de commande
```bash
# JDK 17 + Android SDK (ANDROID_HOME défini)
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

L'APK est signé avec la clé fournie dans `keystore/` (usage personnel) : les mises à jour s'installent
par-dessus la version précédente sans perdre les réglages.

## 4. Installer depuis une clé USB

1. Clé USB en FAT32 ; copiez `PoloDigitalCockpit.apk` à la racine.
2. Sur l'autoradio : *Paramètres › Sécurité* (ou *Paramètres usine / Autres*) → autorisez les
   **sources inconnues** pour le gestionnaire de fichiers.
3. Branchez la clé, ouvrez le gestionnaire de fichiers (*Fichiers*, *File Manager*, *ES*…) → USB → touchez
   l'APK → **Installer**.
4. Lancez « Polo Digital Cockpit ». Au premier lancement, le mode démonstration est actif (bandeau orange).
5. Facultatif : *Réglages › Système › Proposer Polo Cockpit comme écran d'accueil*. Le bouton
   **Origine** (barre de gauche) ramène à tout moment à l'interface d'origine.

## 5. Désinstaller
*Réglages › Système › Désinstaller*, ou *Paramètres Android › Applications › Polo Digital Cockpit ›
Désinstaller*. Rien n'est modifié dans le système : l'autoradio revient exactement à son état d'origine.

## 6. Données véhicule — ce qui est réellement possible

| Donnée | CanBox Raise (après identification) | OBD2 ELM327 | Remarque |
|---|---|---|---|
| Vitesse, régime | souvent | oui | |
| Temp. liquide de refroidissement | parfois | oui | |
| Niveau carburant | parfois | selon calculateur (PID 2F) | |
| Autonomie | parfois | **estimée** | réservoir 45 L ÷ conso moyenne |
| Conso instantanée / moyenne | parfois | **estimée** | via débit d'air ou pression d'admission |
| Portières, coffre | oui en général (popup d'origine) | non | données « confort », pas sur la prise OBD standard |
| Frein à main, feux | selon CanBox | non | |
| Alertes | selon CanBox | non | |

Voir `docs/CANBUS.md` pour la procédure d'identification.

## 7. Sécurité et règles respectées
- Aucune modification du firmware, de la MCU ou des paramètres CAN ; aucun root.
- Aucune trame n'est envoyée sur le bus CAN. L'OBD2 est limité au mode 01 (lecture) par une liste blanche dans le code.
- Données simulées toujours étiquetées « SIMULATION ».
- Aucune dépendance à un service payant (Open-Meteo est gratuit et sans compte).
- Évitez de manipuler l'interface en roulant.
