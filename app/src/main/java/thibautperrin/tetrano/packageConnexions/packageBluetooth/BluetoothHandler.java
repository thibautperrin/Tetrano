package thibautperrin.tetrano.packageConnexions.packageBluetooth;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;

import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageGestionJeu.GestionJeuBluetooth;

/**
 * Created by Thibaut PERRIN on 13/06/2015.
 * This class is the handler managing the messages from BluetoothService.
 */
public class BluetoothHandler extends Handler {

    // ATTRIBUTS:
    private ConnexionBluetoothActivity connexionBluetoothActivity;
    private GestionJeuBluetooth gestionJeuBluetooth;

    //CONSTRUCTEUR:
    public BluetoothHandler(ConnexionBluetoothActivity connexionBluetoothActivity) {
        this.connexionBluetoothActivity = connexionBluetoothActivity;
    }

    public void preciseGestionJeuBluetooth(GestionJeuBluetooth gestionJeuBluetooth) {
        this.gestionJeuBluetooth = gestionJeuBluetooth;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case StaticFields.SERVICE_STATE_CHANGE:
                // on choisi de ne pas l'afficher.
                break;
            case StaticFields.SERVICE_READ:
                final byte[] readBuf = (byte[]) msg.obj;
                final int nbBytes = msg.arg1;
                if (gestionJeuBluetooth != null) {
                    GestionMessageBluetooth.recevoirMessage(readBuf, nbBytes, gestionJeuBluetooth);
                } else {
                    this.sendMessageDelayed(this.obtainMessage(StaticFields.SERVICE_READ, nbBytes, -1, readBuf), 100);
                }
                break;
            case StaticFields.SERVICE_WRITE:
                //nothing to do
                break;
            case StaticFields.SERVICE_CONNEXION_FAILED:
                connexionBluetoothActivity.afficher(connexionBluetoothActivity.
                        getResources().getString(R.string.Connection_Failed));
                connexionBluetoothActivity.dismissConnectingDialog();
                if (gestionJeuBluetooth != null) gestionJeuBluetooth.sortir();
                break;
            case StaticFields.SERVICE_CONNEXION_LOST:
                if (gestionJeuBluetooth != null) gestionJeuBluetooth.sortir();
                break;
            case StaticFields.SERVICE_CONNECTE1:
                connexionBluetoothActivity.apresConnexion(true, (BluetoothDevice) msg.obj);
                break;
            case StaticFields.SERVICE_CONNECTE2:
                connexionBluetoothActivity.apresConnexion(false,(BluetoothDevice) msg.obj);
                break;
        }
    }
}
