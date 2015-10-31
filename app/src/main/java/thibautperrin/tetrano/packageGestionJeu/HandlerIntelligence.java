package thibautperrin.tetrano.packageGestionJeu;

import android.os.Handler;
import android.os.Message;

import thibautperrin.tetrano.StaticFields;

/**
 * Created by Thibaut PERRIN on 22/06/2015.
 */
public class HandlerIntelligence extends Handler {


    private final GestionJeuContreOrdinateur gestion;

    public HandlerIntelligence(GestionJeuContreOrdinateur gestion) {
        this.gestion = gestion;
    }

    @Override
    public void handleMessage(Message msg) {
        float current, total;
        switch (msg.what) {
            case StaticFields.LABEL_RING_CHOSEN:
                gestion.onOrdinateurAJoue((byte) msg.arg1, (byte[]) msg.obj);
                break;
            case StaticFields.LABEL_EXCEPTION_THROWN:
                gestion.afficheErreurInterne();
                break;
            case StaticFields.LABEL_INFO_FORK:
                current = msg.arg1;
                total = msg.arg2;
                gestion.surfaceGraphView.afficherAnneauASelectionner((byte) 2, 0, current * 180.0f / total);
                System.out.println("avancement fourchette: " + current + ", " + total);
                break;
            case StaticFields.LABEL_INFO_BLOCK_FORK:
                current = msg.arg1;
                total = msg.arg2;
                gestion.surfaceGraphView.afficherAnneauASelectionner((byte) 2, 0, 180.0f + current * 180.0f / total);
                System.out.println("avancement fourchette: " + current + ", " + total);
                break;
            default:
                break;
        }
    }
}