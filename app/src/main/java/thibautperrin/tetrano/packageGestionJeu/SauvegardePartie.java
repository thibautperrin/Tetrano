package thibautperrin.tetrano.packageGestionJeu;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import thibautperrin.tetrano.Intelligence2.Partie;

/**
 * Cette classe permet de réaliser une sauvegarde de la partie en cours lors de l'appel du onStop,
 * afin de restituer la partie lors d'un onCreate pour le cas où Android aurait viré la mémoire
 * associé à la partie.
 * Created by Thibaut PERRIN on 18/06/2015.
 */
public class SauvegardePartie implements Parcelable {

    // ATTRIBUTS:
    private byte numeroPremsJoueur;
    private byte[] anneauxJoues;


    // CONSTRUCTEUR:
    public SauvegardePartie() {
        numeroPremsJoueur = 1;
        anneauxJoues = null;
    }

    // Constructeur Parcelable:
    public SauvegardePartie(Parcel in) {
        this.numeroPremsJoueur = in.readByte();
        int nbAnneauxJoues = in.readByte();
        if (nbAnneauxJoues != 0) {
            anneauxJoues = new byte[nbAnneauxJoues];
            for (int i = 0; i < nbAnneauxJoues; i++) {
                this.anneauxJoues[i] = in.readByte();
            }
        } else {
            anneauxJoues = null;
        }
    }


    public void sauvegarder(Partie partie) {
        int nb = partie.getAnoAlig().getNbAnneauxJoues();
        byte pr = partie.getProchainJoueur();

        if (nb == 0) {
            this.numeroPremsJoueur = pr;
            this.anneauxJoues = null;
        } else {
            this.numeroPremsJoueur = partie.getJoueurCommencant();
            this.anneauxJoues = new byte[nb];
            System.arraycopy(partie.getAnneauxJoues(), 0, anneauxJoues, 0, nb);
        }
    }

    public void extraireSauvegarde(Partie partie) {
        if (anneauxJoues == null) {
            partie.restart(false);
            if (partie.getProchainJoueur() != this.numeroPremsJoueur) {
                partie.restart(true);
            }
            if (this.anneauxJoues != null) {
                for (byte anneauxJoue : this.anneauxJoues) {
                    partie.jouer(anneauxJoue);
                }
            }
        }
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.numeroPremsJoueur);
        if (anneauxJoues == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) anneauxJoues.length);
            for (byte anneauxJoue : anneauxJoues) {
                dest.writeByte(anneauxJoue);
            }
        }
    }


    public static final Parcelable.Creator<SauvegardePartie> CREATOR = new Parcelable.Creator<SauvegardePartie>() {
        @Override
        public SauvegardePartie createFromParcel(Parcel source) {
            return new SauvegardePartie(source);
        }

        @Override
        public SauvegardePartie[] newArray(int size) {
            return new SauvegardePartie[size];
        }
    };

    @Override
    public String toString() {
        return "SauvegardePartie: "+ Arrays.toString(anneauxJoues)+ " commence: " + numeroPremsJoueur;
    }
}