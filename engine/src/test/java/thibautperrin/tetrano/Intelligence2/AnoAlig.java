package thibautperrin.tetrano.Intelligence2;

import java.util.Arrays;

/**
 * Cette classe contient les données de jeu de base d'une partie de tetrano.
 * Created by Thibaut PERRIN on 11/07/2015.
 */
public class AnoAlig {
    public byte[] anneaux;
    public byte[] alignements;
    public Ensemble[] ensembles;
    private byte nombreAnneauxJoues;

    public AnoAlig() {
        anneaux = new byte[64];
        alignements = new byte[76];
        ensembles = new Ensemble[10];
        ensembles[0] = new Ensemble((byte) 76, true);
        for (int i = 1; i < 10; i++) {
            ensembles[i] = new Ensemble((byte) 76, false);
        }
        nombreAnneauxJoues = 0;
    }

    /**
     * Ce constructeur permet de construire un AnoAlig à partir d'un autre.
     * On obtient un clone parfait.
     * @param anoAligACopier : l'anoAlig à copier.
     */
    public AnoAlig(AnoAlig anoAligACopier) {
        anneaux = new byte[64];
        alignements = new byte[76];
        ensembles = new Ensemble[10];
        System.arraycopy(anoAligACopier.anneaux, 0, anneaux, 0, 64);
        System.arraycopy(anoAligACopier.alignements, 0, alignements, 0, 76);
        for (int i=0; i<10; i++) {
            ensembles[i] = new Ensemble(anoAligACopier.ensembles[i]);
        }
        nombreAnneauxJoues = anoAligACopier.nombreAnneauxJoues;
    }

    /**
     * Cette méthode permet de reconstruire un AnoAlig à partir d'une signature.
     * Elle part du principe que c'est le joueur 1 qui commence, mais en mettant
     * le boolean à true, c'est le joueur 2 qui aura commencé à jouer.
     * @param signature : byte[13] représentant le jeu
     * @param inverserJoueurs : boolean false en géneral. Mettre true si on veut
     *                        que le joueur 2 soit celui qui ait commencé.
     */
    public AnoAlig(byte[] signature, boolean inverserJoueurs) {
        this(signature, (byte) 0, inverserJoueurs);
    }

    public AnoAlig(byte[] signature, byte symetrieNumber, boolean inverserJoueurs) {
        anneaux = new byte[64];
        alignements = new byte[76];
        ensembles = new Ensemble[10];
        ensembles[0] = new Ensemble((byte) 76, true);
        for (int i = 1; i < 10; i++) {
            ensembles[i] = new Ensemble((byte) 76, false);
        }
        nombreAnneauxJoues = 0;
        byte joueur;
        for (byte i = 0; i < 13; i++) { // On parcourt les cases du tableau.
            byte puissance3j = 1;
            for (byte j = 0; j < 5; j++) { // On stocke 5 emplacements d'anneau par case du tableau
                byte numAno = (byte) (5 * i + j);
                if (numAno < 64) {
                    numAno = Statique.getAnneauSymetrieInverse(numAno, symetrieNumber);
                    joueur = (byte) ((signature[i]+128) / puissance3j % 3);
                    if (joueur != 0 && inverserJoueurs) {
                        joueur = (byte) (3 - joueur);
                    }
                    if (joueur != 0) {
                        jouer(numAno, joueur);
                    }
                }
                puissance3j = (byte) (3 * puissance3j);
            }
        }

    }

    public byte getNbAnneauxJoues() {
         return nombreAnneauxJoues;
    }

    public void jouer(byte numAnneau, byte j){
        nombreAnneauxJoues++;
        anneaux[numAnneau] = j;
        byte[] aligs = Statique.getAlignements(numAnneau);
        for (byte numAlig : aligs) {
            StatiqueAlig.jouerAlig(this, numAlig, j);
        }
    }

    public void dejouer(byte numAnneau, byte j){
        nombreAnneauxJoues--;
        anneaux[numAnneau] = 0;
        byte[] aligs = Statique.getAlignements(numAnneau);
        for (byte numAlig : aligs) {
            StatiqueAlig.dejouerAlig(this, numAlig, j);
        }
    }

    public void restart() {
        Arrays.fill(anneaux, (byte) 0);
        Arrays.fill(alignements, (byte) 0);
        ensembles[0].fill();
        for (int i = 1; i < ensembles.length; i++) {
            ensembles[i].clear();
        }
        nombreAnneauxJoues = 0;
    }


    public byte[] getSignature(boolean inverserJoueurs) {
        byte[] tableau = new byte[13];
        for (byte i = 0; i < 13; i++) { // On parcourt les cases du tableau.
            tableau[i] = Byte.MIN_VALUE;
            byte puissance3j = 1;
            for (byte j = 0; j < 5; j++) { // On stocke 5 emplacements d'anneau par case du tableau
                byte numAno = (byte) (5 * i + j);
                if (numAno < 64 && anneaux[numAno] > 0) {
                    tableau[i] += puissance3j * (inverserJoueurs ?
                            3-anneaux[numAno] :
                            anneaux[numAno]);
                }
                puissance3j = (byte) (3 * puissance3j);
            }
        }
        return tableau;
    }

    public byte[] getLowestSignature(boolean inverserJoueurs) {
        byte[] lowest = null;
        byte[] tableau = new byte[13];
        for (byte numeroSymetrie = 0; numeroSymetrie < 48; numeroSymetrie++) {
            for (byte i = 0; i < 13; i++) { // On parcourt les cases du tableau.
                tableau[i] = Byte.MIN_VALUE;
                byte puissance3j = 1;
                for (byte j = 0; j < 5; j++) { // On stocke 5 emplacements d'anneau par case du tableau
                    byte numAno = (byte) (5 * i + j);
                    if (numAno < 64) {
                        numAno = Statique.getAnneauSymetrique(numAno, numeroSymetrie); // SYMETRIE
                        if (anneaux[numAno] > 0) {
                            tableau[i] += puissance3j * (inverserJoueurs ?
                                    3-anneaux[numAno] :
                                    anneaux[numAno]);
                        }
                        puissance3j = (byte) (3 * puissance3j);
                    }
                }
            }
            if (lowest == null) {
                lowest = new byte[13];
                System.arraycopy(tableau, 0, lowest, 0, 13);
            } else if (inferieurStricte(tableau, lowest)) {
                System.arraycopy(tableau, 0, lowest, 0, 13);
            }
        }
        return lowest;
    }

    /**
     * Cette méthode compare deux tableaux de même taille.
     * Elle renvoit true si tabA est strictement inférieur à tabB.
     * @param tabA Un tableau de byte.
     * @param tabB Un tableau de byte.
     * @return Un boolean indiquant si tabA est inférieur stricte à tabB.
     */
    private boolean inferieurStricte(byte[] tabA, byte[] tabB) {
        int length = tabA.length;
        for (int i = 0; i<length; i++) {
            if (tabA[i] < tabB[i]) {
                return true;
            } else if (tabA[i] > tabB[i]) {
                return false;
            }
        }
        return false;
    }

}