package thibautperrin.tetrano.Intelligence2;

/**
 * Created by Thibaut PERRIN on 15/07/2015.
 */
public class Partie {

    private AnoAlig anoAlig;
    private byte prochainJoueur;
    private byte[] anneauxJoues;

    public Partie() {
        anoAlig = new AnoAlig();
        prochainJoueur = 1;
        anneauxJoues = new byte[64];
    }

    public Partie(byte[] signature, boolean inverserJoueurs) {
        anoAlig = new AnoAlig(signature, inverserJoueurs);
        byte premierJoueur = (byte) (inverserJoueurs ? 2 : 1);
        if (anoAlig.getNbAnneauxJoues() %2 == 0) {
            prochainJoueur = premierJoueur;
        } else {
            prochainJoueur = (byte) (3 - premierJoueur);
        }
        anneauxJoues = new byte[64];
        byte[] numAno = new byte[]{0,-1,-1};
        byte joueurCourant = premierJoueur;
        for (int c = 0 ; c < anoAlig.getNbAnneauxJoues() ; c ++) {
            do {
                numAno[joueurCourant] ++;
            } while (anoAlig.anneaux[numAno[joueurCourant]] != joueurCourant);
            anneauxJoues[c] = numAno[joueurCourant];
            joueurCourant = (byte) (3- joueurCourant);
        }
    }

    public AnoAlig getAnoAlig() {
        return anoAlig;
    }

    public boolean estFini() {
        return (anoAlig.getNbAnneauxJoues() == 64) ||
                (anoAlig.ensembles[4].length() > 0) ||
                (anoAlig.ensembles[8].length() > 0);
    }

    public boolean estPartieNulle() {
        return (anoAlig.getNbAnneauxJoues() == 64) &&
                !(anoAlig.ensembles[4].length() > 0) &&
                !(anoAlig.ensembles[8].length() > 0);
    }

    public byte dejouer() {
        if (anoAlig.getNbAnneauxJoues() > 0) {
            byte numAno = anneauxJoues[anoAlig.getNbAnneauxJoues() - 1];
            anoAlig.dejouer(numAno, (byte) (3 - prochainJoueur));
            prochainJoueur = (byte) (3-prochainJoueur);
            return numAno;
        } else return -1;
    }

    public boolean jouer(byte numAno) {
        if (!estFini() && 0 <= numAno && numAno < 64) {
            anneauxJoues[anoAlig.getNbAnneauxJoues()] = numAno;
            anoAlig.jouer(numAno, prochainJoueur);
            prochainJoueur = (byte) (3 - prochainJoueur);
            return true;
        } else return false;
    }

    /**
     * Cette méthode permet de recommencer la partie. On peut désirer vouloir recommencer
     * en changeant la personne ayant commencé. Dans ce cas là on précise le boolean
     * changingBeginner à true.
     * Dans ce cas, si la partie est finie et qu'il y a un gagnant, le perdant commencera
     * la nouvelle partie.
     * Sinon, on change simplement le joueur qui commence par rapport à la partie précedente.
     * Dans le cas où changingBeginner est à false, on garde la même personne ayant commencé
     * quoiqu'il arrive.
     *
     * @param changingBeginner : true si on veut "éventuellement" changer la personne
     *                         ayant commencé.
     */
    public void restart(boolean changingBeginner) {
        byte joueurQuiCommencera;
        if (changingBeginner) {
            byte gagnant = getGagnant();
            if (gagnant != -1) {
                joueurQuiCommencera = (byte) (3 - gagnant);
            } else {
                joueurQuiCommencera = (byte) (3- getJoueurCommencant());
            }
        } else {
            joueurQuiCommencera = getJoueurCommencant();
        }
        this.anoAlig.restart();
        prochainJoueur = joueurQuiCommencera;
    }


    public byte getJoueurCommencant() {
        if (anoAlig.getNbAnneauxJoues() == anoAlig.getNbAnneauxJoues()/2*2) {
            return prochainJoueur;
        } else {
            return (byte) (3-prochainJoueur);
        }
    }
    /**
     * Cette méthode indique le numéro du joueur ayant gagné la partie.
     * Si la partie n'est pas finie ou si elle s'est soldée par un match nul, cette
     * méthode renvoie -1.
     * @return : 1, 2 ou -1.
     */
    public byte getGagnant() {
        if (anoAlig.ensembles[4].length() > 0) {
            return 1;
        } else if (anoAlig.ensembles[8].length() > 0) {
            return 2;
        } else {
            return -1;
        }
    }

    public boolean isAnneauEstLibre(byte numeroAnneau) {
        return anoAlig.anneaux[numeroAnneau] == 0;
    }

    public byte[] getAnneauxJoues() {
        byte[] res = new byte[anoAlig.getNbAnneauxJoues()];
        System.arraycopy(anneauxJoues, 0, res, 0, anoAlig.getNbAnneauxJoues());
        return res;
    }

    public byte getLastAnneau() {
        return anneauxJoues[anoAlig.getNbAnneauxJoues()-1];
    }

    public byte[] getAnneauxClignotants() {
        if (anoAlig.ensembles[4].length() > 0) {
            byte numAlig = anoAlig.ensembles[4].next((byte) -1);
            return Statique.tabAlignements[numAlig];
        } else if (anoAlig.ensembles[8].length() > 0) {
            byte numAlig = anoAlig.ensembles[8].next((byte) -1);
            return Statique.tabAlignements[numAlig];
        } else if (anoAlig.getNbAnneauxJoues() > 0) {
            return new byte[]{anneauxJoues[anoAlig.getNbAnneauxJoues() - 1]};
        } else return null;
    }

    public byte getProchainJoueur() {
        return prochainJoueur;
    }

    public byte[] getSignature() {
        return anoAlig.getSignature(getJoueurCommencant() == 2);
    }

    public byte[] getLowestSignature() {
        return anoAlig.getLowestSignature(getJoueurCommencant() == 2);
    }
}