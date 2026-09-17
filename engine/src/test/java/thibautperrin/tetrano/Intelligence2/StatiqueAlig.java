package thibautperrin.tetrano.Intelligence2;

/**
 * Created by Thibaut PERRIN on 11/07/2015.
 * Cette classe possède un ensemble de méthodes statiques pour agir sur un alignement qui est
 * représenté par un byte. Ce byte contient toutes les infos utiles sur l'alignement, notamment
 * le nombre d'anneaux joué par chacun des deux joueurs.
 * Ce byte représentant l'alignement ne doit pas être confondu avec le numéro de l'alignement.
 * La valeur de ce byte représente juste l'état de l'alignement à un instant donné de la partie.
 * Plus exactement, ce byte vaut: nbAno1 + 5 * nbAno2.
 */
public class StatiqueAlig {

    /**
     * @param alig un octet représentant l'alignement (et non pas le numéro de l'alignement).
     * @return Le nombre d'anneaux appartenant au joueur 1 sur l'alignement.
     */
    public static byte nbAno1(byte alig) {
        return (byte) (alig % 5);
    }

    /**
     * @param alig un octet représentant l'alignement (et non pas le numéro de l'alignement).
     * @return Le nombre d'anneaux appartenant au joueur 2 sur l'alignement.
     */
    public static byte nbAno2(byte alig) {
        return (byte) (alig / 5);
    }

    /**
     * On part du principe que:
     * 0 -> alignement vide
     * 1 -> 1 anneau J1, 0 anneau J2
     * 2 -> 2 anneau J1, 0 anneau J2
     * 3 -> 3 anneau J1, 0 anneau J2
     * 4 -> 4 anneau J1, 0 anneau J2
     * 5 -> 0 anneau J1, 1 anneau J2
     * 6 -> 0 anneau J1, 2 anneau J2
     * 7 -> 0 anneau J1, 3 anneau J2
     * 8 -> 0 anneau J1, 4 anneau J2
     * 9 -> X anneau J1, Y anneau J2 avec X > 0, Y > 0
     *
     * @param alig un octet représentant l'alignement (et non pas le numéro de l'alignement).
     * @return Le numéro de l'ensemble d'alignements le contenant.
     */
    public static byte numEnsemble(byte alig) {
        if (nbAno2(alig) == 0) {
            return nbAno1(alig);
        } else if (nbAno1(alig) == 0) {
            return (byte) (nbAno2(alig) + 4);
        } else {
            return 9;
        }
    }

    public static void jouerAlig(AnoAlig anoAlig, byte numAlig, byte j) {
        byte alig = anoAlig.alignements[numAlig];
        anoAlig.ensembles[numEnsemble(alig)].remove(numAlig);
        byte res = (byte) (alig - 3 + 4 * j);
        anoAlig.ensembles[numEnsemble(res)].add(numAlig);
        anoAlig.alignements[numAlig] = res;
    }

    public static void dejouerAlig(AnoAlig anoAlig, byte numAlig, byte j) {
        byte alig = anoAlig.alignements[numAlig];
        anoAlig.ensembles[numEnsemble(alig)].remove(numAlig);
        byte res = (byte) (alig + 3 - 4 * j);
        anoAlig.ensembles[numEnsemble(res)].add(numAlig);
        anoAlig.alignements[numAlig] = res;
    }

    /**
     * Étant donné un alignement ayant exactement 2 anneaux appartenant à un joueur, et
     * aucun anneau appartenant à son adversaire, cette méthode trouve le premier anneau
     * vide de l'alignement. Elle fonctionne de la même façon que trov2anoBis, qui elle cherche
     * le dernier anneau libre de l'alignement.
     *
     * @param anoAlig Les données de la partie.
     * @param numAlig un octet représentant le numéro de l'alignement.
     * @return un octet représentant le numéro du permier anneau vide de l'alignement.
     */
    public static byte trov2ano(AnoAlig anoAlig, byte numAlig) {
        byte[] ans = Statique.tabAlignements[numAlig];
        if (anoAlig.anneaux[ans[0]] == 0) {
            return ans[0];
        } else if (anoAlig.anneaux[ans[1]] == 0) {
            return ans[1];
        } else {
            return ans[2];
        }
    }

    /**
     * Étant donné un alignement ayant exactement 2 anneaux appartenant à un joueur, et
     * aucun anneau appartenant à son adversaire, cette méthode trouve le dernier anneau
     * vide de l'alignement. Elle fonctionne de la même façon que trov2ano, qui elle cherche
     * le premier anneau libre de l'alignement.
     *
     * @param anoAlig Les données de la partie.
     * @param numAlig un octet représentant le numéro de l'alignement.
     * @return un octet représentant le numéro du dernier anneau vide de l'alignement.
     */
    public static byte trov2anoBis(AnoAlig anoAlig, byte numAlig){
        byte[] ans = Statique.tabAlignements[numAlig];
        if (anoAlig.anneaux[ans[3]] == 0) {
            return ans[3];
        } else if (anoAlig.anneaux[ans[2]] == 0) {
            return ans[2];
        } else {
            return ans[1];
        }
    }

    /**
     * Étant donné un alignement ayant exactement 3 anneaux appartenant à un joueur, et
     * aucun anneau appartenant à son adversaire, cette méthode trouve le dernier anneau
     * vide restant de l'alignement.
     *
     * @param anoAlig Les données de la partie.
     * @param numAlig un octet représentant le numéro de l'alignement.
     * @return un octet représentant le numéro du dernier anneau vide restant de l'alignement.
     */
    public static byte trovLastAno(AnoAlig anoAlig, byte numAlig) {
        byte[] ans = Statique.tabAlignements[numAlig];
        if (anoAlig.anneaux[ans[0]] == 0) {
            return ans[0];
        } else if (anoAlig.anneaux[ans[1]] == 0) {
            return ans[1];
        } else if (anoAlig.anneaux[ans[2]] == 0) {
            return ans[2];
        } else {
            return ans[3];
        }
    }

    /**
     * Cette méthode fait le travail de trov2ano et trov2anoBis, et regarde également
     * le sous-indice de la paire d'anneau obtenue.
     *
     * @param anoAlig Les données de la partie.
     * @param numAlig un octet représentant le numéro de l'alignement.
     * @return Cette méthode renvoit unt tableau de trois octets contenant:
     * Le sous-indice de la paire d'anneau, numAno1 et numAno2.
     */
    public static byte[] trov2anoIndice(AnoAlig anoAlig, byte numAlig) {
        byte[] ans = Statique.tabAlignements[numAlig];
        byte numAno1, numAno2, sousIndice;
        if (anoAlig.anneaux[ans[0]] == 0) {
            numAno1 = ans[0];
            if (anoAlig.anneaux[ans[1]] == 0) {
                numAno2 = ans[1];
                sousIndice = 0;
            } else if (anoAlig.anneaux[ans[2]] == 0) {
                numAno2 = ans[2];
                sousIndice = 1;
            } else {
                numAno2 = ans[3];
                sousIndice = 2;
            }
        } else if (anoAlig.anneaux[ans[1]] == 0) {
            numAno1 = ans[1];
            if (anoAlig.anneaux[ans[2]] == 0) {
                numAno2 = ans[2];
                sousIndice = 3;
            } else {
                numAno2 = ans[3];
                sousIndice = 4;
            }
        } else {
            numAno1 = ans[2];
            numAno2 = ans[3];
            sousIndice = 5;
        }
        return new byte[] {sousIndice, numAno1, numAno2};
    }


}
