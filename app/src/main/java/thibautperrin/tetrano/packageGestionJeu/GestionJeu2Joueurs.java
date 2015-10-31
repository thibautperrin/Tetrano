package thibautperrin.tetrano.packageGestionJeu;

import android.os.Bundle;
import thibautperrin.tetrano.Intelligence2.Partie;
import thibautperrin.tetrano.JeuActivity;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageSurfaceGraphView.SurfaceGraphView;

/**
 * Gère les données de jeu d'une partie à deux joueurs sur un même smartphone.
 * Created by Thibaut PERRIN on 12/06/2015.
 */
public class GestionJeu2Joueurs extends GestionJeu {

    public GestionJeu2Joueurs(SurfaceGraphView surfaceGraphView, Bundle bundle, SauvegardePartie sauvegarde, JeuActivity jeuActivity) {
        super(surfaceGraphView, jeuActivity);
        noms[1] = bundle.getString(StaticFields.LABEL_PLAYER1_NAME);
        couleurs[1] = bundle.getInt(StaticFields.LABEL_PLAYER1_COLOR);
        noms[2] = bundle.getString(StaticFields.LABEL_PLAYER2_NAME);
        couleurs[2] = bundle.getInt(StaticFields.LABEL_PLAYER2_COLOR);
        partie = new Partie();
        this.sauvegarde = sauvegarde;
        sauvegarde.extraireSauvegarde(partie);
        if (partie.getAnoAlig().getNbAnneauxJoues() == 0) {
            byte[] ringsPlayed = bundle.getByteArray(StaticFields.LABEL_RINGS_PLAYED);
            if (ringsPlayed != null) {
                for (byte b : ringsPlayed) {
                    partie.jouer(b);
                }
            }
        }
    }

    @Override
    public void jouerAdversaire() {
        autoriserUtilisateurAJouer(partie.getProchainJoueur(), 0);
    }

    @Override
    public void mettreLeBonTitre() {
        if (partie.estFini()) {
            byte gagnant = partie.getGagnant();
            if (gagnant > 0) {
                activity.setTitle(res.getString(R.string.Winner) +": "+ noms[gagnant]);
            } else {
                activity.setTitle(R.string.Sentence_End_Null);
            }
        } else {
            if (partie.getAnoAlig().getNbAnneauxJoues() == 0) {
                activity.setTitle(noms[partie.getProchainJoueur()]+ res.getString(R.string.You_Can_Start));
            } else {
                activity.setTitle(noms[partie.getProchainJoueur()] + ", " + res.getString(R.string.Your_Turn));
            }
        }
    }

    @Override
    protected void logGame() {
        app.getGameLog().addGame(StaticFields.GAME_2_PLAYERS, null, activity);
    }

    @Override
    public void begin() {
        super.begin();
        if (!partie.estFini()) {
            autoriserUtilisateurAJouer(partie.getProchainJoueur(), 0);
        }
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
            setOccuped(1200);
            surfaceGraphView.autoriserDeplacement((byte) 0, 0);
            byte numero = partie.dejouer();
            byte joueurAnnulant = partie.getProchainJoueur();
            surfaceGraphView.jouerAnneau((byte) 0, numero, 0);
            surfaceGraphView.afficherAnneauASelectionner(joueurAnnulant, 0);
            surfaceGraphView.afficherDeplacementAnneau(numero, 0, 1200, 30, false);
            autoriserUtilisateurAJouer(joueurAnnulant, 1200);
        } catch (Exception e) {
            e.printStackTrace();
            afficheErreurInterne();
        }
    }
}
