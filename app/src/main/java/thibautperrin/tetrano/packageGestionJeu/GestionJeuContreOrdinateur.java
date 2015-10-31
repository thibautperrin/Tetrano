package thibautperrin.tetrano.packageGestionJeu;

import android.os.Bundle;
import android.os.Handler;

import java.util.Arrays;

import thibautperrin.tetrano.Intelligence2.Partie;
import thibautperrin.tetrano.JeuActivity;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageSurfaceGraphView.SurfaceGraphView;

/**
 * Gère les données de jeu d'une partie contre l'intelligence artificielle.
 * Created by Thibaut PERRIN on 12/06/2015.
 */
public class GestionJeuContreOrdinateur extends GestionJeu {
    private ThreadIA threadIA;


    // CONSTRUCTEUR:
    public GestionJeuContreOrdinateur(SurfaceGraphView surfaceGraphView, Bundle bundle, SauvegardePartie sauvegarde, JeuActivity jeuActivity) {
        super(surfaceGraphView, jeuActivity);
        int niveauJeu = bundle.getInt(StaticFields.LABEL_LEVEL);
        boolean utilisateurCommence = bundle.getBoolean(StaticFields.LABEL_USER_BEGINS);
        noms[1] = bundle.getString(StaticFields.LABEL_USER_NAME);
        noms[2] = bundle.getString(StaticFields.LABEL_ADVERSARY_NAME);
        couleurs[1] = bundle.getInt(StaticFields.LABEL_USER_COLOR);
        couleurs[2] = bundle.getInt(StaticFields.LABEL_ADVERSARY_COLOR);
        partie = new Partie();
        this.sauvegarde = sauvegarde;
        sauvegarde.extraireSauvegarde(partie);
        if (utilisateurCommence != (partie.getJoueurCommencant() == 1)) {
            partie.restart(true);
        }
        if (partie.getAnoAlig().getNbAnneauxJoues() == 0) { // On test le cas d'une ancienne partie enregistrée.
            byte[] ringsPlayed = bundle.getByteArray(StaticFields.LABEL_RINGS_PLAYED);
            if (ringsPlayed != null) {
                for (byte b : ringsPlayed) {
                    partie.jouer(b);
                }
            }
        }
        Handler handler = new HandlerIntelligence(this);
        threadIA = new ThreadIA(handler, niveauJeu);
        threadIA.start();
    }

    @Override
    public void jouerAdversaire() {
        threadIA.reflechir(partie.getAnneauxJoues());
    }

    public void onOrdinateurAJoue(byte numeroAnneau, byte[] signature) {
        if (Arrays.equals(partie.getSignature(), signature)) {
            try {
                byte joueurJouant = partie.getProchainJoueur();
                partie.jouer(numeroAnneau);
                postAnneauJoueParAdversaire(numeroAnneau, joueurJouant);
            } catch (Exception e) {
                afficheErreurInterne();
                e.printStackTrace();
            }
        }
    }

    @Override
    public void mettreLeBonTitre() {
        if (partie.estFini()) {
            byte gagnant = partie.getGagnant();
            if (gagnant  == 1) {
                activity.setTitle(res.getString(R.string.Sentence_End_Win));
            } else if (gagnant ==2) {
                activity.setTitle(res.getString(R.string.Sentence_End_Lost));
            } else {
                activity.setTitle(R.string.Sentence_End_Null);
            }
        } else {
            byte prochainJoueur = partie.getProchainJoueur();
            if (partie.getAnoAlig().getNbAnneauxJoues() == 0 && prochainJoueur == 1) {
                activity.setTitle(R.string.Sentence_Begin_Start);
            } else if (prochainJoueur == 1){
                activity.setTitle(noms[1] + " " + res.getString(R.string.Your_Turn));
            } else if (prochainJoueur == 2) {
                activity.setTitle(R.string.Sentence_Computer_Work);
            }
        }
    }

    @Override
    protected void logGame() {
        app.getGameLog().addGame(StaticFields.GAME_AGAINST_COMPUTER,
                !partie.estPartieNulle() && partie.getGagnant() == 1, activity);
    }

    @Override
    public void cliqueAnnuler() {
        if (partie.getAnoAlig().getNbAnneauxJoues() == 0) return;
        if (occuped) {
            surfaceGraphView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    cliqueAnnuler();
                }
            }, 100);
            return;
        }
        try {
            byte lastJoueur; // Le dernier joueur à avoir joué.
            if (partie.getGagnant() == -1) {
                lastJoueur = (byte) (3-partie.getProchainJoueur());
            } else {
                lastJoueur = partie.getGagnant();
            }
            if (lastJoueur == 2) {
                if (partie.getAnoAlig().getNbAnneauxJoues() >= 2) {
                    byte numero1 = partie.dejouer();
                    setOccuped(1600);
                    surfaceGraphView.autoriserDeplacement((byte) 0, 0);
                    byte numJ1 = partie.getProchainJoueur();
                    byte numero2 = partie.dejouer();
                    byte numJ2 = partie.getProchainJoueur();
                    surfaceGraphView.jouerAnneau((byte) 0, numero1, 0);
                    surfaceGraphView.afficherAnneauASelectionner(numJ1, 0);
                    surfaceGraphView.afficherDeplacementAnneau(numero1, 0, 800, 20, false);
                    surfaceGraphView.jouerAnneau((byte) 0, numero2, 800);
                    surfaceGraphView.afficherAnneauASelectionner(numJ2, 800);
                    surfaceGraphView.afficherDeplacementAnneau(numero2, 800, 800, 20, false);
                    autoriserUtilisateurAJouer(partie.getProchainJoueur(), 1600);
                }
            } else /*Cas où lastJouer = utilisateur */ {
                int numero = partie.dejouer();
                setOccuped(1200);
                surfaceGraphView.autoriserDeplacement((byte) 0, 0);
                surfaceGraphView.jouerAnneau((byte) 0, numero, 0);
                surfaceGraphView.afficherAnneauASelectionner(lastJoueur, 0);
                surfaceGraphView.afficherDeplacementAnneau(numero, 0, 1200, 30, false);
                autoriserUtilisateurAJouer(partie.getProchainJoueur(), 1200);
            }
        } catch (Exception e) {
            afficheErreurInterne();
            e.printStackTrace();
        }
    }

    @Override
    public void begin() {
        super.begin();
        if (!partie.estFini()) {
            if (partie.getProchainJoueur() == 1) {
                autoriserUtilisateurAJouer(partie.getProchainJoueur(), 0);
            } else {
                jouerAdversaire();
            }
        }
    }

    @Override
    public void actualiserReglages() {
        noms[1] = app.getNomUtilisateur();
        couleurs[1] = app.getCouleurUtilisateur();
        couleurs[2] = app.getCouleurAdversaire();
        surfaceGraphView.changerCouleur(couleurs[1], 1);
        surfaceGraphView.changerCouleur(couleurs[2], 2);
    }

    @Override
    public void sortir() {
        threadIA.finish();
        threadIA.interrupt();
        try {
            threadIA.join();
        } catch (InterruptedException e) {
            afficheErreurInterne();
            e.printStackTrace();
        }
        super.sortir();
    }
}
