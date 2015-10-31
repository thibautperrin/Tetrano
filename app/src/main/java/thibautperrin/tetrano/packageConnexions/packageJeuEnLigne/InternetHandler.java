package thibautperrin.tetrano.packageConnexions.packageJeuEnLigne;


import android.os.Handler;
import android.os.Message;

import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageGestionJeu.GestionJeuEnLigne;

/**
 * Ce Handler gère les messages envoyé par l'InternetService.
 * Il se repose complètement sur les méthodes statiques de GestionMessageInternet pour
 * gérer les messages envoyé par le serveur et reçu par l'InternetService.
 * Created by Thibaut PERRIN on 15/06/2015.
 */
public class InternetHandler extends Handler {

    private final GestionJeuEnLigne gestionJeuEnLigne;

    public InternetHandler(GestionJeuEnLigne gestionJeuEnLigne) {
        this.gestionJeuEnLigne = gestionJeuEnLigne;
    }
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case StaticFields.MESSAGE_SERVICE_STATE_CHANGE:
                switch (msg.arg1) {
                    case StaticFields.STATE_NONE:
                        break;
                    case StaticFields.STATE_CONNECTING:
                        gestionJeuEnLigne.onConnecting();
                        break;
                    case StaticFields.STATE_CONNECTED:
                        gestionJeuEnLigne.onConnected();
                        break;
                }
                break;
            case StaticFields.MESSAGE_SERVICE_READ:
                GestionMessageInternet.recevoirMessage((byte[]) msg.obj,
                        msg.arg1, gestionJeuEnLigne);
                break;
            case StaticFields.MESSAGE_SERVICE_CONNECTION_FAILED_INITIATION:
                gestionJeuEnLigne.connectionFailed(true);
                break;
            case StaticFields.MESSAGE_SERVICE_CONNECTION_FAILED_NOT_IN_INITIATION:
                gestionJeuEnLigne.connectionFailed(false);
        }
    }
}
