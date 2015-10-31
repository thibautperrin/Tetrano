package thibautperrin.tetrano.packageSurfaceGraphView;


import android.os.Handler;
import android.os.Message;

import thibautperrin.tetrano.StaticFields;

/**
 * Ce Handler permet d'envoyer au thread d'affichage graphique les élements à changer.
 * Created by Thibaut PERRIN on 04/04/2015.
 */
public class HandlerSurfaceGraphView extends Handler {

    private ThreadSurfaceGraphView threadSurfaceGraphView;

    public HandlerSurfaceGraphView(ThreadSurfaceGraphView threadSurfaceGraphView) {
        this.threadSurfaceGraphView = threadSurfaceGraphView;
    }

    @Override
    public void handleMessage(Message msg) {
        byte joueur;
        int numeroAnneau;
        switch (msg.what) {
            case StaticFields.BLINK_HIDE:
                numeroAnneau = msg.arg1;
                threadSurfaceGraphView.setAnneauClignotant(numeroAnneau, false);
                break;
            case StaticFields.BLINK_DISPLAY:
                numeroAnneau = msg.arg1;
                threadSurfaceGraphView.setAnneauClignotant(numeroAnneau, true);
                break;
            case StaticFields.MOVE_RING:
                float posx = ((float) msg.arg1) / 1000.0f;
                float posy = ((float) msg.arg2) / 1000.0f;
                threadSurfaceGraphView.afficherAnneauSelectionne(posx, posy);
                break;
            case StaticFields.MOVE_RING_NUMBER:
                numeroAnneau = msg.arg1;
                float fraction = ((float) msg.arg2) / (float) Integer.MAX_VALUE;
                threadSurfaceGraphView.afficherDeplacementAnneau(numeroAnneau, fraction);
                break;
            case StaticFields.DISPLAY_4_RINGS:
                joueur = (byte) msg.arg1;
                float angle = (float) msg.obj;
                threadSurfaceGraphView.afficherAnneauASelectionner(joueur, angle);
                break;
            case StaticFields.PLAY_RING:
                joueur = (byte) msg.arg1;
                numeroAnneau = msg.arg2;
                threadSurfaceGraphView.jouerAnneau(joueur, numeroAnneau);
                break;
            case StaticFields.CHANGE_COLOR:
                int couleur = msg.arg1;
                int indice = msg.arg2;
                threadSurfaceGraphView.changerCouleur(couleur, indice);
                break;
            case StaticFields.CHANGE_RING_SELECTED:
                int taille=msg.arg1;
                threadSurfaceGraphView.changeTailleAnneauSelectionne(taille);
                break;
            case StaticFields.RESTART_GAME_FROM:
                byte[] matrice = msg.getData().getByteArray("matrice");
                threadSurfaceGraphView.redemarrerPartieDepuis(matrice);
                break;
            case StaticFields.MOVE_CHOICE_ACCEPTED:
                numeroAnneau = msg.arg1;
                threadSurfaceGraphView.deplacementValidee(numeroAnneau);
                break;
            default:
                break;
        }
    }

}
