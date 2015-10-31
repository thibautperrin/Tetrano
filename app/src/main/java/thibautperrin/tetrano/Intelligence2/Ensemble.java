package thibautperrin.tetrano.Intelligence2;

import java.util.Arrays;

/**
 * Created by Thibaut PERRIN on 11/07/2015.
 * Cette classe vise à remplacer l'usage des Hashset dans AnoAlig.
 * Elle permet de stocker un ensemble d'octets positifs (entre 0 et  la
 * valeur indiquée dans le constructeur (maximum 127)). Elle permet en O(1) d'ajouter et
 * supprimer un élement. Elle permet également de parcourir l'ensemble en O du nombre d'élements
 * réélement présent dans l'ensemble.
 * Le constructeur est en O(taille).
 * Le clear est en O(nombre élements réélement présents).
 * Le contains est en O(1).
 * La structure sous-jacente permettant de représenter les données est une pile.
 */
public class Ensemble {

    private byte[] posSuiv;
    private byte[] posPrec;
    private byte pointeur;
    private byte size;


    public Ensemble(byte taille, boolean rempli) {
        posSuiv = new byte[taille];
        posPrec = new byte[taille];
        if (rempli) {
            fill();
        } else {
            pointeur = -1;
            Arrays.fill(posPrec, (byte) -1);
            Arrays.fill(posSuiv, (byte) -1);
            size = 0;
        }
    }

    /**
     * Ce constructeur permet de créer un Ensemble à partir d'un autre ensemble.
     * On obtient au final un clone à l'identique.
     * @param ensembleACopier : l'ensemble à copier.
     */
    public Ensemble(Ensemble ensembleACopier) {
        byte taille = (byte) ensembleACopier.posSuiv.length;
        this.posSuiv = new byte[taille];
        this.posPrec = new byte[taille];
        System.arraycopy(ensembleACopier.posSuiv, 0, this.posSuiv, 0, taille);
        System.arraycopy(ensembleACopier.posPrec, 0, this.posPrec, 0, taille);
        this.pointeur = ensembleACopier.pointeur;
        this.size = ensembleACopier.size;
    }

    public boolean contains(byte b) {
        return (posSuiv[b] != -1);
    }

    /**
     * Ajoute l'octet b à l'ensemble
     * @param b : l'octet à ajouter
     */
    public void add(byte b) {
        if (!contains(b)) {
            size++;
            if (pointeur == -1) {
                pointeur = b;
                posPrec[b] = -1;
                posSuiv[b] = b;
            } else {
                posSuiv[pointeur] = b;
                posPrec[b] = pointeur;
                pointeur = b;
                posSuiv[b] = b;
            }
        }
    }

    /**
     * Retire l'octet b de l'ensemble
     * @param b : l'octet à retirer
     */
    public void remove(byte b) {
        if (contains(b)) {
            size--;
            if (posPrec[b] == -1 && posSuiv[b] == b) {
                pointeur = -1;
            }else if (posSuiv[b] == b) {
                pointeur = posPrec[b];
                posSuiv[pointeur] = pointeur;
            } else if (posPrec[b] == -1) {
                posPrec[posSuiv[b]] = -1;
            } else {
                byte precedent = posPrec[b];
                posSuiv[posPrec[b]] = posSuiv[b];
                posPrec[posSuiv[b]] = precedent;
            }
            posSuiv[b] = -1;
            posPrec[b] = -1;
        }
    }

    /**
     * Cette méthode doit permettre de parcourir l'ensemble. On commence avec le byte -1 et
     * une fois qu'on a tout parcouru, cette méthode renvoit -1.
     * @param b : Un octet à partir duquel on veut trouver le suivant. (-1 pour begin).
     * @return -1 s'il n'y a plus rien dans l'ensemble après l'octet b, l'octet suivant sinon.
     */
    public byte next(byte b) {
        if (b == -1) {
            return pointeur;
        } else {
            return posPrec[b];
        }
    }

    public void clear() {
        int taille = posPrec.length;
        if (size > taille / 4) {
            pointeur = -1;
            Arrays.fill(posPrec, (byte) -1);
            Arrays.fill(posSuiv, (byte) -1);
            size = 0;
        } else {
            byte b = next((byte) -1);
            while (b != -1) {
                byte b0 = b;
                b = next(b);
                remove(b0);
            }
        }
    }

    public void fill() {
        int taille = posPrec.length;
        for (int i = 0; i < taille; i++) {
            posPrec[i] = (byte) (i - 1);
            posSuiv[i] = (byte) (i + 1);
        }
        posSuiv[taille - 1] = (byte) (taille - 1);
        pointeur = (byte) (taille - 1);
        size = (byte) taille;
    }

    public int length() {
        return size;
    }
}