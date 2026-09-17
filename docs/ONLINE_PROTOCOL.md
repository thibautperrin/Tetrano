# Protocole du jeu en ligne (2015) — référence pour la future réimplémentation

> Extrait de l'ancien code avant suppression (commit "table rase"). Le serveur d'origine
> (`88.187.4.43:4`) n'existe très probablement plus : ceci documente uniquement le **protocole
> applicatif** tel qu'il existait, pas une infrastructure à faire revivre telle quelle.
>
> Sources d'origine : `StaticFields.java`, `packageConnexions/packageJeuEnLigne/*.java`,
> `packageGestionJeu/GestionJeuEnLigne.java`.

## Constantes de connexion (2015)

```java
public static final String SERVER_IP_ADDRESS = "88.187.4.43";
public static final int SERVER_PORT_NUMBER = 4;
```

## États de connexion (partagés avec le Bluetooth)

```java
public static final int STATE_NONE = 0;       // rien en cours
public static final int STATE_CONNECTING = 1; // connexion sortante en cours
public static final int STATE_CONNECTED = 2;  // connecté à un pair distant
public static final int STATE_LISTEN = 3;     // écoute de connexions entrantes
```

## Octets de message échangés (valeurs `byte`)

Un octet `[0 ; 63]` signifie toujours : « je joue l'anneau numéro x ».

### Handler local (`MESSAGE_SERVICE_*`)

```java
public static final int MESSAGE_SERVICE_STATE_CHANGE = 41;
public static final int MESSAGE_SERVICE_READ = 42;
public static final int MESSAGE_SERVICE_CONNECTION_FAILED_INITIATION = 43;
public static final int MESSAGE_SERVICE_CONNECTION_FAILED_NOT_IN_INITIATION = 44;
```

### Utilisateur → serveur

```java
public static final byte INTERNET_ASK_VERSION = 70;
public static final byte INTERNET_CLOSE_CONNECTION = 71;
```

### Serveur → utilisateur

```java
public static final byte INTERNET_RECEIVE_CONNECTION_LOST = 90;
public static final byte INTERNET_RECEIVE_VERSION = 91;
public static final byte INTERNET_RECEIVE_SO_I_BEGIN = 92;
public static final byte INTERNET_RECEIVE_SO_HE_BEGINS = 93;
```

### Utilisateur → serveur → adversaire (relayés tels quels)

```java
public static final byte INTERNET_TRANSFER_NAME = 100;
public static final byte INTERNET_TRANSFER_ASK_CANCEL = 101;
public static final byte INTERNET_TRANSFER_ACCEPT_CANCEL = 102;
public static final byte INTERNET_TRANSFER_ASK_RESTART = 103;
public static final byte INTERNET_TRANSFER_ACCEPT_RESTART = 104;
public static final byte INTERNET_TRANSFER_REJECT_RESTART = 105;
```

## Déroulement observé (`GestionJeuEnLigne`, `InternetService`)

1. Connexion TCP au serveur, envoi de `INTERNET_ASK_VERSION`.
2. Le serveur répond `INTERNET_RECEIVE_VERSION` (avec la version courante — le client affichait
   `Sentence_Version_Internet` s'il détectait une version plus récente que la sienne) puis met en
   relation avec un adversaire et indique qui commence
   (`INTERNET_RECEIVE_SO_I_BEGIN` / `INTERNET_RECEIVE_SO_HE_BEGINS`).
3. Chaque coup est un octet `[0;63]` envoyé au serveur, qui le relaie tel quel à l'adversaire.
4. Les noms sont échangés via `INTERNET_TRANSFER_NAME` (payload : nom en UTF-8, longueur non
   spécifiée dans les constantes — voir `GestionMessageInternet` si le code source est ré-exhumé
   du tag `v4.1-legacy`).
5. Annulation d'un coup : `INTERNET_TRANSFER_ASK_CANCEL` puis réponse
   `INTERNET_TRANSFER_ACCEPT_CANCEL` (pas de refus explicite prévu côté "cancel").
6. Redémarrage de partie : `INTERNET_TRANSFER_ASK_RESTART`, réponse
   `INTERNET_TRANSFER_ACCEPT_RESTART` ou `INTERNET_TRANSFER_REJECT_RESTART`.
7. Perte de connexion signalée par `INTERNET_RECEIVE_CONNECTION_LOST`.
8. Fermeture propre : `INTERNET_CLOSE_CONNECTION`.

## Ce qui manque pour une réimplémentation complète

Le format exact des payloads (framing des messages, encodage de `INTERNET_TRANSFER_NAME`, gestion
des déconnexions/reconnexions, matchmaking) est dans les classes Java qui seront supprimées par le
commit "table rase" ; elles restent consultables via :

```bash
git show v4.1-legacy:app/src/main/java/thibautperrin/tetrano/packageConnexions/packageJeuEnLigne/GestionMessageInternet.java
git show v4.1-legacy:app/src/main/java/thibautperrin/tetrano/packageConnexions/packageJeuEnLigne/InternetHandler.java
git show v4.1-legacy:app/src/main/java/thibautperrin/tetrano/packageConnexions/packageJeuEnLigne/InternetService.java
git show v4.1-legacy:app/src/main/java/thibautperrin/tetrano/packageGestionJeu/GestionJeuEnLigne.java
```

## Recommandation pour la réimplémentation (2026+)

Ne pas réutiliser ce protocole texte/byte brut sur socket TCP nu tel quel. Privilégier au moment de
la réimplémentation : WebSocket ou gRPC bidirectionnel, messages structurés (protobuf ou JSON),
authentification, et un vrai serveur de matchmaking hébergé. Ce document sert uniquement à ne pas
perdre la sémantique métier (quels événements existent, dans quel ordre).
