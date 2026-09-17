package thibautperrin.tetrano.Intelligence2;
import java.util.BitSet;

/**
 * Created by Thibaut PERRIN on 11/07/2015.
 * Cette classe vise à apporter des informations rapidement sur les relations entre les numéros
 * d'anneaux et les numéros d'alignements. Elle est statique, et ne connait pas de contexte d'un
 * quelconque jeu en cours. Ainsi elle peut être utilisée de partout, en ayant ses données
 * définies une seule fois.
 */
public class Statique {
    /**
     * tabAlignement est un tableau de 76 tableau de 4 octets.
     * Chaque sous tableau d'indice i représente les 4 numéros d'anneaux de l'alignement i.
     */
    public static byte[][] tabAlignements = initialiseTabAlignements();

    /**
     * tabAnneauxAlig est un tableau de 64*8 octets. tout les 8 octets, représente un anneau.
     * Le premier octet de l'anneau représente le nombre d'alignements auquel cet anneau contains.
     * Les 4 ou 7 suivants représentent les numéros des alignements correspondant.
     */
    private static byte[] tabAnneauxAlig = initialiseTabAnneauxAlig();

    /**
     * tabBool est un BitSet, c'est à dire une suite de bit.
     * Il y a 64*76 bits, et le bit indicé par alig*64+ano représente un boolean qui est true
     * si et seulement si l'anneau ano fait parti de l'alignement alig.
     */
    private static BitSet tabBool = initialiseTabBool();

    /**
     * isAnneauAppAlig permet de savoir en O(1) si un anneau contains à un alignement.
     * @param numeroAnneau : entre 0 et 63
     * @param numeroAlignement : entre 0 et 75
     * @return true si l'anneau est sur l'alignement. false sinon.
     */
    public static boolean isAnneauAppAlig(int numeroAnneau, int numeroAlignement) {
        return tabBool.get(numeroAlignement * 64 + numeroAnneau);
    }

    /**
     * getAlignements permet de récuperer l'ensemble des alignements auquel un anneau appatient.
     * @param numeroAnneau : entre 0 et 63
     * @return un tableau de 4 ou 7 octets représentant les numéros d'alignements.
     */
    public static byte[] getAlignements(int numeroAnneau) {
        int ligne = numeroAnneau * 8;
        int size = tabAnneauxAlig[ligne];
        byte[] res = new byte[size];
        for (int i = 0; i < size; i++) {
            res[i] = tabAnneauxAlig[ligne + 1 + i];
        }
        return res;
    }

    /**
     * Cette méthode permet un calcul rapide et sans allocation de mémoire des "points"
     * d'un anneau.
     * Cette méthode additionne les points apportés par chacun des alignements auquel cet
     * anneau appartient. Pour chaque alignement, elle regarde à quelle ensemble il
     * appartient (grâce au tableau alignements), et en fonction de son ensemble, elle
     * incrémente les points en se basant sur la case du tableau de réglages correspondante.
     * Attention, cette méthode ne tient pas compte de si on est le joueur1 ou le joueur2.
     * Il faut donc adapter le tableau de réglage au joueur qui nous interesse.
     *
     * @param numeroAnneau : le numéro de l'anneau dont on calcule les points
     *                     (On ne fait pas de vérification sur le fait qu'il soit libre: on
     *                     a même pas ici les informations pour le faire)
     * @param alignements : Le tableaux des alignements. C'est la seule information qui est
     *                    dépendante de la partie en cours.
     * @param reglages : Un tableau de réglage. La case i correspond au nombre de points à
     *                 affecter à un alignement appartenant à l'ensemble i. Le tableau de
     *                 réglages doit être adapté au joueur.
     * @return Un octet représentant le nombre de points.
     */
    public static byte getPointsSimple(int numeroAnneau, byte[] alignements, byte[] reglages) {
        byte res = 0;
        int ligne = numeroAnneau * 8;
        int size = tabAnneauxAlig[ligne];
        byte numAlig, alig;
        for (int i = 0; i < size; i++) {
            numAlig = tabAnneauxAlig[ligne + 1 + i];
            alig = alignements[numAlig];
            res += reglages[StatiqueAlig.numEnsemble(alig)];
        }
        return res;
    }

    /**
     * Cette méthode permet de déterminer sans allocation de mémoire si un anneau appartient à au
     * moins un alignement d'un ensemble donné.
     * Elle est utilisée par exemple pour déterminer si un anneau appartient à un alignement de
     * 2 anneaux d'un certain joueur, lorsqu'on ne veut pas jouer un anneau qui va forcer
     * l'adversaire à bloquer.
     * @param numeroAnneau entre 0 et 63: le numéro de l'anneau qui nous interesse
     * @param alignements Le tableau des alignements. C'est la seule information qui représente
     *                    la partie en cours.
     * @param sorteAlig Le numéro de l'ensemble d'alignement.
     * @return true si l'anneau appartient à au moins un de ces alignements. False sinon.
     */
    public static boolean isAnneauAppartientTelSorteAlig(int numeroAnneau, byte[] alignements, byte sorteAlig) {
        int ligne = numeroAnneau * 8;
        int size = tabAnneauxAlig[ligne];
        byte numAlig, alig;
        for (int i = 0; i < size; i++) {
            numAlig = tabAnneauxAlig[ligne + 1 + i];
            alig = alignements[numAlig];
            if (alig == sorteAlig) {
                return true;
            }
        }
        return false;
    }

    /**
     * Etant donné un numero d'anneau et un numero de symétrie, cette fonction
     * renvoit le numéro de l'anneau symétrique à l'anneau de départ, par la
     * symétrie considérée.
     * Le numéro de symétrie peut s'écrire: 24 * b3 + 12 * b2 + 6 * b1 + t
     * où : t (entre 0 et 5) représente les inversions de coordonnées,
     * b1 (entre 0 et 1) représente le symétrique de la nouvelle coordonnée de x
     * b2 (entre 0 et 1) représente le symétrique de la nouvelle coordonnée de y
     * b3 (entre 0 et 1) représente le symétrique de la nouvelle coordonnée de z
     *
     * @param numeroAnneau Un byte entre 0 et 63.
     * @param numeroSymetrie Un byte entre 0 et 47.
     * @return Un numero d'anneau, c'est à dire un byte entre 0 et 63.
     */
    public static byte getAnneauSymetrique(byte numeroAnneau, byte numeroSymetrie) {
        byte x, y, z, inter;
        x = (byte) (numeroAnneau % 4);
        y = (byte) (numeroAnneau / 4 % 4);
        z = (byte) (numeroAnneau / 16);
        switch (numeroSymetrie % 6) {
            // case 0: pas de changement.
            case 1:
                // x=x
                inter = y;
                y = z;
                z = inter;
                break;
            case 2:
                inter = x;
                x = y;
                y = inter; // z=z
                break;
            case 3:
                inter = x;
                x = y;
                y = z;
                z = inter;
                break;
            case 4:
                inter = x;
                x = z;
                z = y;
                y = inter;
                break;
            case 5:
                // y=y
                inter = x;
                x = z;
                z = inter;
                break;
        }
        if (numeroSymetrie / 6 % 2 == 1) {
            x = (byte) (3 - x);
        }
        if (numeroSymetrie / 12 % 2 == 1) {
            y = (byte) (3 - y);
        }
        if (numeroSymetrie / 24 % 2 == 1) {
            z = (byte) (3 - z);
        }
        return (byte) (x + 4 * y + 16 * z);
    }

    /**
     * Cette méthode permet de trouver la symétrie inverse du numero d'anneau.
     * En réalité, la symétrie telle qu'on l'entant ici n'est pas réflexive. On
     * devrait plutot parler de similitude.
     * @param numeroAnneau entre 0 et 63
     * @param numeroSymetrie entre 0 et 47
     * @return un numero d'anneau entre 0 et 63.
     */
    public static byte getAnneauSymetrieInverse(byte numeroAnneau, byte numeroSymetrie) {
        byte x, y, z, inter;
        x = (byte) (numeroAnneau % 4);
        y = (byte) (numeroAnneau / 4 % 4);
        z = (byte) (numeroAnneau / 16);
        if (numeroSymetrie / 6 % 2 == 1) {
            x = (byte) (3 - x);
        }
        if (numeroSymetrie / 12 % 2 == 1) {
            y = (byte) (3 - y);
        }
        if (numeroSymetrie / 24 % 2 == 1) {
            z = (byte) (3 - z);
        }
        switch (numeroSymetrie % 6) {
            // case 0: pas de changement.
            case 1:
                // x=x
                inter = y;
                y = z;
                z = inter;
                break;
            case 2:
                inter = x;
                x = y;
                y = inter; // z=z
                break;
            case 3:
                inter = x;
                x = z;
                z = y;
                y = inter;
                break;
            case 4:
                inter = x;
                x = y;
                y = z;
                z = inter;
                break;
            case 5:
                // y=y
                inter = x;
                x = z;
                z = inter;
                break;
        }
        return (byte) (x + 4 * y + 16 * z);
    }

    private static byte[][] initialiseTabAlignements() {
        System.out.println("initialiseTabAlignements");
        return new byte[][]{
                {0, 4, 8, 12},
                {0, 16, 32, 48},
                {0, 1, 2, 3},
                {16, 20, 24, 28},
                {4, 20, 36, 52},
                {16, 17, 18, 19},
                {32, 36, 40, 44},
                {8, 24, 40, 56},
                {32, 33, 34, 35},
                {48, 52, 56, 60},
                {12, 28, 44, 60},
                {48, 49, 50, 51},
                {1, 5, 9, 13},
                {1, 17, 33, 49},
                {4, 5, 6, 7},
                {17, 21, 25, 29},
                {5, 21, 37, 53},
                {20, 21, 22, 23},
                {33, 37, 41, 45},
                {9, 25, 41, 57},
                {36, 37, 38, 39},
                {49, 53, 57, 61},
                {13, 29, 45, 61},
                {52, 53, 54, 55},
                {2, 6, 10, 14},
                {2, 18, 34, 50},
                {8, 9, 10, 11},
                {18, 22, 26, 30},
                {6, 22, 38, 54},
                {24, 25, 26, 27},
                {34, 38, 42, 46},
                {10, 26, 42, 58},
                {40, 41, 42, 43},
                {50, 54, 58, 62},
                {14, 30, 46, 62},
                {56, 57, 58, 59},
                {3, 7, 11, 15},
                {3, 19, 35, 51},
                {12, 13, 14, 15},
                {19, 23, 27, 31},
                {7, 23, 39, 55},
                {28, 29, 30, 31},
                {35, 39, 43, 47},
                {11, 27, 43, 59},
                {44, 45, 46, 47},
                {51, 55, 59, 63},
                {15, 31, 47, 63},
                {60, 61, 62, 63},
                {0, 20, 40, 60},
                {0, 5, 10, 15},
                {0, 17, 34, 51},
                {48, 36, 24, 12},
                {12, 9, 6, 3},
                {3, 18, 33, 48},
                {1, 21, 41, 61},
                {16, 21, 26, 31},
                {4, 21, 38, 55},
                {49, 37, 25, 13},
                {28, 25, 22, 19},
                {7, 22, 37, 52},
                {2, 22, 42, 62},
                {32, 37, 42, 47},
                {8, 25, 42, 59},
                {50, 38, 26, 14},
                {44, 41, 38, 35},
                {11, 26, 41, 56},
                {3, 23, 43, 63},
                {48, 53, 58, 63},
                {12, 29, 46, 63},
                {51, 39, 27, 15},
                {60, 57, 54, 51},
                {15, 30, 45, 60},
                {0, 21, 42, 63},
                {3, 22, 41, 60},
                {12, 25, 38, 51},
                {15, 26, 37, 48}};
    }

    private static byte[] initialiseTabAnneauxAlig() {
        System.out.println("initialiseTabAnneauxAlig");
        byte[] tab = new byte[76 * 8];
        for (byte alig = 0; alig < 76; alig++) {
            for (byte j = 0; j < 4; j++) {
                int anneau = tabAlignements[alig][j];
                tab[anneau * 8]++;
                tab[anneau * 8 + tab[anneau * 8]] = alig;
            }
        }
        return tab;
    }

    private static BitSet initialiseTabBool() {
        System.out.println("initialiseTabBool");
        BitSet bitSet = new BitSet(76 * 64);
        for (int alig = 0; alig < 76; alig++) {
            for (int j = 0; j < 4; j++) {
                int anneau = tabAlignements[alig][j];
                bitSet.set(alig * 64 + anneau);
            }
        }
        return bitSet;
    }
}
