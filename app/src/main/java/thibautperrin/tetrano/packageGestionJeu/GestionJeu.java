package thibautperrin.tetrano.packageGestionJeu;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.Toast;

import thibautperrin.tetrano.ApplicationTetrano;
import thibautperrin.tetrano.Intelligence2.Partie;
import thibautperrin.tetrano.JeuActivity;
import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageSavedGame.DAOSavedGame;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.packageSavedGame.SavedGame;
import thibautperrin.tetrano.packageSurfaceGraphView.SurfaceGraphView;

/**
 * Permet de gérer les données d'une partie.
 * Cette classe est étendue par chaque type de partie
 * Created by Thibaut PERRIN on 12/06/2015.
 */
public abstract class GestionJeu implements InterfaceGameManagement{
    // ATTRIBUTS:
    protected SurfaceGraphView surfaceGraphView;
    protected ApplicationTetrano app;
    public JeuActivity activity;
    private Toast toast;
    protected Partie partie;
    protected Resources res;
    public boolean occuped = false;
    protected SauvegardePartie sauvegarde;
    protected int[] couleurs = new int[3];
    protected String[] noms = new String[3];

    // CONSTRUCTEUR:
    public GestionJeu(SurfaceGraphView surfaceGraphView, JeuActivity jeuActivity) {
        this.activity = jeuActivity;
        this.app = (ApplicationTetrano) jeuActivity.getApplication();
        this.surfaceGraphView = surfaceGraphView;
        res = surfaceGraphView.getResources();
    }

    public static GestionJeu CreeGestionJeu(Bundle bundle, SauvegardePartie sauvegarde, SurfaceGraphView surfaceGraphView, JeuActivity jeuActivity) throws Exception {
        int typePartie = bundle.getInt(StaticFields.LABEL_GAME_CODE);
        switch (typePartie) {
            case StaticFields.GAME_2_PLAYERS:
                return new GestionJeu2Joueurs(surfaceGraphView, bundle, sauvegarde, jeuActivity);
            case StaticFields.GAME_AGAINST_COMPUTER:
                return new GestionJeuContreOrdinateur(surfaceGraphView, bundle, sauvegarde, jeuActivity);
            case StaticFields.GAME_BLUETOOTH:
                return new GestionJeuBluetooth(surfaceGraphView, bundle, sauvegarde, jeuActivity);
            case StaticFields.GAME_ONLINE:
                return new GestionJeuEnLigne(surfaceGraphView, bundle, sauvegarde, jeuActivity);
            default:
                return null;
        }
    }

    public void setSurfaceGraphView(SurfaceGraphView surfaceGraphView) {
        this.surfaceGraphView = surfaceGraphView;
        res = surfaceGraphView.getResources();
    }

    public void autoriserUtilisateurAJouer(byte joueur, int delai) {
        surfaceGraphView.autoriserDeplacement(joueur, delai);
        mettreLeBonTitre();
    }

    public boolean userTriedPlaying(byte numeroAnneau) {
        if (partie.isAnneauEstLibre(numeroAnneau)) {
            try {
                partie.jouer(numeroAnneau);
            } catch (Exception e) {
                afficheErreurInterne();
                e.printStackTrace();
            }
            return true;
        } else {
            return false;
        }
    }

    public void afficheErreurInterne() {
        afficher(res.getString(R.string.Internal_Error), Toast.LENGTH_LONG);
    }

    public void afficher(String string, int duration) {
        if (toast != null) {
            toast.cancel();
        }
        Context context = surfaceGraphView.getContext();
        toast = Toast.makeText(context, string, duration);
        toast.show();
    }

    /**
     * On regarde si la partie est finie.
     * Dans le cas d'une partie online ou en bluetooth, il faut overrider cette méthode pour ajouter
     * l'envoi d'un message à l'adversaire lui disant ce qu'on a joué.
     */
    public void postRingPlayedByUser() {
        if (partie.estFini()) {
            finPartie();
        } else {
            mettreLeBonTitre();
            jouerAdversaire();
        }
    }

    /**
     * Cette méthode doit afficher l'anneau joué, regarder si la partie est finie, et donner la main
     * à l'utilisateur pour lui permettre de jouer.
     */
    public void postAnneauJoueParAdversaire(byte anneau, byte joueurJouant) {
        if (partie.estFini()) {
            surfaceGraphView.jouerAdversaireAnimer(anneau, joueurJouant, (byte) 0, 1500, 40);
            finPartie(1500);
        } else {
            setOccuped(1500);
            surfaceGraphView.jouerAdversaireAnimer(anneau, joueurJouant, (byte) (3 - joueurJouant), 1500, 40);
            surfaceGraphView.cliquerClignoter(anneau, 5, 400, 1500);
            mettreLeBonTitre();
        }
    }

    public abstract void jouerAdversaire();

    public abstract void mettreLeBonTitre();

    public void finPartie() {
        mettreLeBonTitre();
        cliqueClignoter();
    }

    public void finPartie(int retard) {
        surfaceGraphView.postDelayed(new Runnable() {
            @Override
            public void run() {
                finPartie();
            }
        }, retard);
    }

    public void cliqueRecommencer() {
        if (occuped) {
            surfaceGraphView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    cliqueRecommencer();
                }
            }, 100);
            return;
        }
        if (partie.estFini()) {
            logGame();
        }
        partie.restart(true);
        begin();
    }

    protected abstract void logGame();

    public abstract void cliqueAnnuler();

    public void cliqueClignoter() {
        byte[] anneauxClignotants = partie.getAnneauxClignotants();
        for (byte anneau : anneauxClignotants) {
            surfaceGraphView.cliquerClignoter(anneau, 5, 400, 0);
        }
    }

    public void begin() {
        surfaceGraphView.redemarrerPartieDepuis(partie.getAnoAlig().anneaux);
        surfaceGraphView.changerCouleur(couleurs[1], 1);
        surfaceGraphView.changerCouleur(couleurs[2], 2);
        mettreLeBonTitre();
    }

    public void setOccuped(int duree) {
        occuped = true;
        surfaceGraphView.postDelayed(new Runnable() {
            @Override
            public void run() {
                occuped = false;
            }
        }, duree);
    }

    public void actualiserReglages() {
        // Override in subclasses
    }

    public void sortir() {
        if (partie.estFini()) {
            logGame();
        }
        activity.finish();
    }

    public SauvegardePartie sauvegarder() {
        this.sauvegarde.sauvegarder(partie);
        return this.sauvegarde;
    }

    public void sauvegarderPartiePourPlusTard(String nomPartie) {
        SavedGame savedGame = new SavedGame(nomPartie, partie.getAnneauxJoues(),
                partie.getJoueurCommencant() == 1,couleurs[1], couleurs[2]);
        DAOSavedGame dao = app.getOpenedDAOSavedGame();
        if (!dao.addSavedGame(savedGame)) {
            int cpt = 2;
            do {
                savedGame.rename(nomPartie + " (" + cpt + ")");
                cpt++;
            } while (!dao.addSavedGame(savedGame));
        }
    }
}
