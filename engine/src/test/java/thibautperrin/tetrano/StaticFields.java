package thibautperrin.tetrano;

import java.util.UUID;

/**
 * Created by Thibaut PERRIN on 02/10/2015.
 * This class regroups the different constants of the app.
 */
public final class StaticFields {

    // In bundles and preferences:
    public static final String LABEL_FROM_SAVED_GAME = "fromSavedGame";
    public static final String LABEL_GAME_CODE = "gameCode";
    public static final String LABEL_PLAYER1_COLOR = "player1Color";
    public static final String LABEL_PLAYER2_COLOR = "player2Color";
    public static final String LABEL_PLAYER1_NAME = "player1Name";
    public static final String LABEL_PLAYER2_NAME = "player2Namme";
    public static final String LABEL_LEVEL = "level";
    public static final String LABEL_USER_NAME = "userName";
    public static final String LABEL_ADVERSARY_NAME = "adversaryName";
    public static final String LABEL_USER_BEGINS = "userBegins";
    public static final String LABEL_USER_COLOR = "userColor";
    public static final String LABEL_ADVERSARY_COLOR = "adversaryColor";
    public static final String LABEL_RINGS_PLAYED = "ringsPlayed";
    public static final String LABEL_PARCELABLE = "parcelable";
    public static final String LABEL_GAME_NAME = "GameName";
    public static final String LABEL_DIST_MAC_ADDRESS = "distMac";
    public static final String LABEL_DIST_DEVICE_NAME = "distDeviceName";

    public static final String LABEL_NUMBER_GAME_IN_2_PLAYERS_MODE = "nbGames2P";
    public static final String LABEL_NUMBER_GAMES_AGAINST_COMPUTER = "nbGamesComp";
    public static final String LABEL_NUMBER_GAME_WINNED_AGAINST_COMPUTER = "nbWonGamesComp";
    public static final String LABEL_NUMBER_GAMES_ONLINE = "nbGamesOnline";
    public static final String LABEL_NUMBER_GAMES_BLUETOOTH = "nbGamesBlt";

    // Games codes:
    public static final int GAME_2_PLAYERS = 1;
    public static final int GAME_AGAINST_COMPUTER = 2;
    public static final int GAME_BLUETOOTH = 3;
    public static final int GAME_ONLINE = 4;

    // States for Bluetooth and internet services:
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTING = 1; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 2;  // now connected to a remote device
    public static final int STATE_LISTEN = 3;     // now listening for incoming connections

    // Connections details (internet and bluetooth) and others constants:
    public static final String SERVER_IP_ADDRESS = "88.187.4.43";
    public static final int SERVER_PORT_NUMBER = 4;
    public static final String BLUETOOTH_SECURE = "BluetoothSecure";
    public static final UUID BLUETOOTH_UUID = UUID.fromString("e78c3610-c02b-11e4-bb52-0800200c9a66");
    public static final String PUBLISHER_KEY_ADBUDDIZ = "3eb2f6e0-54a2-4d7a-ae5f-d04ba84cfc6c";
    public static final int NUMBER_CHALLENGE_LEVELS = 245;
    public static final int NUMBER_BYTES_CHALLENGE_FILE = 14 * NUMBER_CHALLENGE_LEVELS;


    // Specific to SurfaceGraphView Handler:
    public static final int BLINK_HIDE = 10;
    public static final int BLINK_DISPLAY = 11;
    public static final int MOVE_RING = 12;
    public static final int MOVE_RING_NUMBER = 13;
    public static final int DISPLAY_4_RINGS = 14;
    public static final int PLAY_RING = 15;
    public static final int CHANGE_COLOR = 16;
    public static final int CHANGE_RING_SELECTED = 17;
    public static final int RESTART_GAME_FROM = 18;
    public static final int MOVE_CHOICE_ACCEPTED = 19;

    // Specific to IA thread Handler:
    public static final int LABEL_RING_CHOSEN = 20;
    public static final int LABEL_EXCEPTION_THROWN = 21;
    public static final int LABEL_INFO_BLOCK_FORK = 22;
    public static final int LABEL_INFO_FORK = 23;

    // Specific to the Bluetooth Handler:
    public static final int SERVICE_STATE_CHANGE = 31;
    public static final int SERVICE_READ = 32;
    public static final int SERVICE_WRITE = 33;
    public static final int SERVICE_CONNEXION_FAILED = 34;
    public static final int SERVICE_CONNEXION_LOST = 35;
    public static final int SERVICE_CONNECTE1 = 36;
    public static final int SERVICE_CONNECTE2 = 37;

    // Specific to the Internet service Handler:
    public static final int MESSAGE_SERVICE_STATE_CHANGE = 41;
    public static final int MESSAGE_SERVICE_READ = 42;
    public static final int MESSAGE_SERVICE_CONNECTION_FAILED_INITIATION = 43;
    public static final int MESSAGE_SERVICE_CONNECTION_FAILED_NOT_IN_INITIATION = 44;

    // Messages exchanged in Bluetooth:
    // [|0 ; 63 |] --> "Je joue l'anneau numéro x"
    public static final byte BLUETOOTH_ASK_CANCEL = 64;
    public static final byte BLUETOOTH_ACCEPT_CANCEL = 65;
    public static final byte BLUETOOTH_ASK_RESTART = 66;
    public static final byte BLUETOOTH_SEND_NAME = 67;
    public static final byte BLUETOOTH_ACCEPT_RESTART = 68;
    public static final byte BLUETOOTH_DISCARD_REJECT = 69;

    // Messages exchanged with the internet server:
    // [|0 ; 63 |] --> "Je joue l'anneau numéro x"
    // user --> server:
    public static final byte INTERNET_ASK_VERSION = 70;
    public static final byte INTERNET_CLOSE_CONNECTION = 71;
    // server --> user:
    public static final byte INTERNET_RECEIVE_CONNECTION_LOST = 90;
    public static final byte INTERNET_RECEIVE_VERSION = 91;
    public static final byte INTERNET_RECEIVE_SO_I_BEGIN = 92;
    public static final byte INTERNET_RECEIVE_SO_HE_BEGINS = 93;
    // user --> server --> adversary:
    public static final byte INTERNET_TRANSFER_NAME = 100;
    public static final byte INTERNET_TRANSFER_ASK_CANCEL = 101;
    public static final byte INTERNET_TRANSFER_ACCEPT_CANCEL = 102;
    public static final byte INTERNET_TRANSFER_ASK_RESTART = 103;
    public static final byte INTERNET_TRANSFER_ACCEPT_RESTART = 104;
    public static final byte INTERNET_TRANSFER_REJECT_RESTART = 105;


    // Request code for intents:
    public static final int REQUEST_ENABLE_BLUETOOTH = 111;
    public static final int REQUEST_CODE_SETTINGS = 112;
    public static final int REQUEST_CODE_WATCH_SAVEED_GAME = 113;
    public static final int REQUEST_CODE_FROM_SAVED_GAME = 114;
    public static final int REQUEST_PLAY = 115;
    public static final int REQUEST_CODE_PLAY_AGAINST_COMPUTER = 116;
    public static final int REQUEST_CODE_PLAY_2_PLAYERS = 117;

    // Result code for intents:
    public static final int RESULT_CODE_LEAVE = 120;

    // Colors:
    public static final int COLOR_VERT_CLAIR = 0xff44ff44;

    // File names:
    public static final String FILE_PREFERENCES = "preferences";
    public static final String GAME_LOG_FILE = "gameLog";
    public static final String SAVED_GAMES_DATABASE_FILE = "savedGames.db";
    public static final String BLUETOOTH_DEVICES_DATABASE_FILE = "bluetoothDevices.db";
    public static final String FILE_CHALLENGE_VARS = "challengeVars.bin";


    // Measures in drawings:
    // epaisseur des 4 traits du cadre:
    public static final float thicknessBorderCoeff = 0.01262f;

    // epaisseur des 6 traits de separation des cases:
    public static final float thicknessSeparationLinesCoeff = 0.009464f;

    // épaisseur du trait du cercle des anneaux:
    public static float thicknessRingsCoeff = 0.014f;

    // Distance entre deux traits de separations (mesure de bord a bord et non pas de milieu a milieu):
    public static final float distanceLinesCoeff = 0.236593f;

    // Rayon de l'anneau 0:
    public static final float shortestRadiusCoeff = 0.01893f;

    // Difference de longueur de rayon entre deux anneaux de Taille consecutive:
    public static final float diffRadiusCoeff = 0.02839f;

    // Position de la coordonnee X (ou Y) de l'anneau en haut a gauche:
    public static final float pos0Coeff = thicknessBorderCoeff + distanceLinesCoeff / 2;

    // Distance entre les centre de deux anneaux:
    public static final float distanceRingsCentersCoeff = distanceLinesCoeff + thicknessSeparationLinesCoeff;

}
