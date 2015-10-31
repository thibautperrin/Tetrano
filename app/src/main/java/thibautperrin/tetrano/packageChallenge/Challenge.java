package thibautperrin.tetrano.packageChallenge;

import java.util.ArrayList;

import thibautperrin.tetrano.Intelligence2.AnoAlig;
import thibautperrin.tetrano.Intelligence2.IA;
import thibautperrin.tetrano.Intelligence2.Statique;
import thibautperrin.tetrano.Intelligence2.StatiqueAlig;

/**
 * Created by Thibaut PERRIN on 22/09/2015.
 */
public class Challenge {
    private AnoAlig anoAlig;
    private int levelFork; // -1: no fork, 0: simple fork, n: advanced n-fork
    private ArrayList<Byte> ringsPlayed;
    private int numberStarRings = 0;
    private byte ringPlayedUser = -1; // The byte representing the ring chosen by the user.
    private final IA iA;

    public Challenge(AnoAlig anoAlig, int levelFork, int numberStarRings, byte[] ringsPlayed) {
        this.anoAlig = anoAlig;
        this.levelFork = levelFork;
        if (ringsPlayed == null) {
            this.ringsPlayed = new ArrayList<>();
        } else {
            byte joueur = 1;
            this.ringsPlayed = new ArrayList<>(ringsPlayed.length);
            for (byte numRing : ringsPlayed) {
                anoAlig.jouer(numRing, joueur);
                this.ringsPlayed.add(numRing);
                joueur = (byte) (3 - joueur);
            }
        }
        this.numberStarRings = numberStarRings;
        iA = new IA(anoAlig);
    }

    public AnoAlig getAnoAlig() {
        return anoAlig;
    }

    public int getUsedRings() {
        return getNumberUserRings() + numberStarRings;
    }

    public int getNumberUserRings() {
        return ringsPlayed.size() / 2 + ringsPlayed.size() % 2;
    }

    public int getAuthorizedRings() {
        return levelFork + 2;
    }

    public boolean gameFinished() {
        return (anoAlig.getNbAnneauxJoues() == 64) ||
                (anoAlig.ensembles[4].length() > 0) ||
                (anoAlig.ensembles[8].length() > 0) ||
                (getNumberUserRings() + numberStarRings == getAuthorizedRings());
    }

    public boolean isWinnedByUser() {
        return anoAlig.ensembles[4].length() > 0;
    }

    public byte[] getBlinkingRings() {
        if (anoAlig.ensembles[4].length() > 0) {
            byte numAlig = anoAlig.ensembles[4].next((byte) -1);
            return Statique.tabAlignements[numAlig];
        } else if (anoAlig.ensembles[8].length() > 0) {
            byte numAlig = anoAlig.ensembles[8].next((byte) -1);
            return Statique.tabAlignements[numAlig];
        } else throw new RuntimeException("No line to blink.");
    }

    public byte playComputer() {
        byte ring = iA.playChallenge(levelFork, getNumberUserRings() + numberStarRings);
        anoAlig.jouer(ring, (byte) 2);
        ringsPlayed.add(ring);
        return ring;
    }

    public boolean userTriedPlaying(byte ringNumber) {
        if (anoAlig.anneaux[ringNumber] == 0) {
            anoAlig.jouer(ringNumber, (byte) 1);
            ringsPlayed.add(ringNumber);
            ringPlayedUser = ringNumber;
            return true;
        } else {
            return false;
        }
    }

    public boolean userCanPlay() {
        return ringPlayedUser == -1 && !gameFinished();
    }

    public byte validateRingPlayedByUser() {
        byte res = ringPlayedUser;
        ringPlayedUser = -1;
        return res;
    }


    public StarUsedBean useAStar() {
        StarUsedBean bean = new StarUsedBean();
        if (getNumberUserRings() == 0) {
            bean.setNeedRestart(false);
        } else {
            bean.setNeedRestart(true);
            int size = ringsPlayed.size();
            byte joueur = (byte) (2 - (size % 2));
            Byte[] tab = new Byte[size];
            ringsPlayed.toArray(tab);
            for (int i = size - 1; i >= 0; i--) {
                anoAlig.dejouer(tab[i], joueur);
                joueur = (byte) (3-joueur);
            }
            ringsPlayed.clear();
            byte[] matrix12 = new byte[64];
            System.arraycopy(anoAlig.anneaux, 0, matrix12, 0, 64);
            bean.setMatrix12(matrix12);
        }
        if (anoAlig.ensembles[3].length() > 0) {
            bean.setIsFinishedJustAfterStar(true);
            byte numAlig = anoAlig.ensembles[3].next((byte) -1);
            bean.setRingUser(StatiqueAlig.trovLastAno(anoAlig, numAlig));
            anoAlig.jouer(bean.getRingUser(), (byte) 1);
        } else {
            bean.setIsFinishedJustAfterStar(false);
            iA.playStar(levelFork, numberStarRings, bean);
            numberStarRings++;
        }
        return bean;
    }
}
