# Intégration CanBox Raise (VW-RZ-08) — procédure en lecture seule

## Ce qu'une appli peut réellement voir
```
Bus CAN de la Polo ─► CanBox Raise ─(UART)─► MCU ─► service propriétaire du fabricant (Android)
                                                        │
                                  ce que le fabricant expose aux autres applis (ou rien) :
                                  diffusions (broadcasts), service lié, fournisseur de contenu, journaux
```
Sans root, une appli ne peut ni lire le port série de la MCU ni le bus CAN. Elle ne peut exploiter
que ce que le fabricant publie. Cette interface n'est pas documentée : elle doit être observée sur
l'autoradio. L'appli n'invente aucune action, clé ou donnée : seules les diffusions réellement reçues sont utilisables.

## Procédure (Réglages › Données véhicule)
1. **Générer le rapport**, puis **Enregistrer…** (sélecteur Android) ou, si l'autoradio n'a pas de sélecteur,
   **Enregistrer dans le dossier de l'appli** : `Android/data/fr.polo.cockpit/files/rapports/`.
2. **Lancer la capture (60 s)** : pendant le compte à rebours, ouvrir/fermer une portière et le coffre,
   allumer/éteindre les feux, serrer/desserrer le frein à main.
3. **Générer le rapport** à nouveau : la section 8 liste les diffusions réellement reçues, avec leur contenu.
4. Si des diffusions sont apparues : saisir les **associations** (section 3), une par ligne :
   `ACTION;EXTRA;CHAMP[;FACTEUR ou bit:N]`
   Exemple (fictif, à remplacer par ce que la capture a montré) : `com.exemple.CAR;speed;vitesse;1`
   Les valeurs associées s'affichent avec l'étiquette « CanBox ».
5. Si rien n'est apparu : transmettre
   - le rapport (.txt),
   - le ZIP **Exporter les applis CAN** (code des applis du fabricant, pour analyse hors ligne),
   - éventuellement le rapport refait après avoir accordé les journaux depuis un PC :
     `adb shell pm grant fr.polo.cockpit android.permission.READ_LOGS`.

## Limites matérielles possibles
- Le fabricant peut ne rien exposer (communication interne protégée par une permission système) : dans ce cas,
  aucune appli tierce sans root ne peut lire les données CanBox. L'OBD2 reste alors la seule source réelle.
- Les diffusions protégées par une permission système ne sont pas reçues par une appli ordinaire.

## OBD2 (alternative fiable)
ELM327 **Bluetooth classique** ou **Wi-Fi** sur la prise diagnostic. Le test de connexion doit réussir avant
d'activer la lecture. Mode 01 uniquement (lecture). Ne fournit pas les portières/coffre/frein à main.
Si le Bluetooth de l'autoradio est géré par le module du fabricant (appareil invisible pour Android), utiliser un ELM327 Wi-Fi.

## Ce que l'appli ne fera jamais
Envoyer des trames CAN ou des commandes à la MCU, effacer des défauts, coder des calculateurs, modifier un paramètre du véhicule.
