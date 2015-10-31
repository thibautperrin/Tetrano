package thibautperrin.tetrano.packageConnexions.packageBluetooth;



import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageConnexions.GestionMessage;
import thibautperrin.tetrano.packageGestionJeu.GestionJeuBluetooth;

/**
 * Created by Thibaut PERRIN on 13/06/2015.
 */
public class GestionMessageBluetooth extends GestionMessage{

    public static void jouerUtilisateur(BluetoothService bluetoothService, int numeroAnneau) {
        byte[] buffer = new byte[1];
        buffer[0] = (byte) numeroAnneau;
        bluetoothService.write(buffer);
    }

    public static void demanderAnnuler(BluetoothService bluetoothService, byte[] signatureJeuActuel) {
        int size = signatureJeuActuel.length;
        byte[] message = new byte[size + 1];
        message[0] = StaticFields.BLUETOOTH_ASK_CANCEL;
        System.arraycopy(signatureJeuActuel, 0, message, 1, size);
        bluetoothService.write(message);
    }

    public static void accepterAnnulation(BluetoothService bluetoothService) {
        byte[] message = new byte[1];
        message[0] = StaticFields.BLUETOOTH_ACCEPT_CANCEL;
        bluetoothService.write(message);
    }

    public static void demanderRecommencer(BluetoothService bluetoothService, byte[] signature) {
        int size = signature.length;
        byte[] message = new byte[size + 1];
        message[0] = StaticFields.BLUETOOTH_ASK_RESTART;
        System.arraycopy(signature, 0, message, 1, size);
        bluetoothService.write(message);
    }

    public static void accepterRecommencer(BluetoothService bluetoothService, boolean jeCommence) {
        byte[] message = new byte[2];
        message[0] = StaticFields.BLUETOOTH_ACCEPT_RESTART;
        message[1] = jeCommence ? (byte) 1 : (byte) 0;
        bluetoothService.write(message);
    }

    public static void refuserRecommencer(BluetoothService bluetoothService) {
        byte[] message = new byte[1];
        message[0] = StaticFields.BLUETOOTH_DISCARD_REJECT;
        bluetoothService.write(message);
    }

    public static void envoyerMonNom(BluetoothService bluetoothService, String monNom) {
        byte[] nom = monNom.getBytes();
        if (nom.length > 40) {
            nom = copyOfRange(nom, 0, 39);
        }
        byte[] message = new byte[2 + nom.length];
        message[0] = StaticFields.BLUETOOTH_SEND_NAME;
        message[1] = (byte) nom.length;
        System.arraycopy(nom, 0, message, 2, nom.length);
        bluetoothService.write(message);
    }

    public static void recevoirMessage(byte[] readBuf, int nbBytes, GestionJeuBluetooth gestionJeuBluetooth) {
        if (0 <= readBuf[0] && readBuf[0] < 64) {
            gestionJeuBluetooth.adversaireBluetoothJoue(readBuf[0]);
            poursuivreEventuellement(readBuf, nbBytes, gestionJeuBluetooth, 1);
        } else {
            switch (readBuf[0]) {
                case StaticFields.BLUETOOTH_ASK_CANCEL:
                    gestionJeuBluetooth.recevoirDemandeAnnulation(copyOfRange(readBuf, 1, 13));
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuBluetooth, 14);
                    break;
                case StaticFields.BLUETOOTH_ACCEPT_CANCEL:
                    gestionJeuBluetooth.recevoirAcceptationDeMonAnnulation();
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuBluetooth, 1);
                    break;
                case StaticFields.BLUETOOTH_ASK_RESTART:
                    gestionJeuBluetooth.recevoirDemandeRecommencer(copyOfRange(readBuf, 1, 13));
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuBluetooth, 14);
                    break;
                case StaticFields.BLUETOOTH_ACCEPT_RESTART:
                    gestionJeuBluetooth.recevoirAcceptationRecommencer(readBuf[1] == 1);
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuBluetooth, 2);
                    break;
                case StaticFields.BLUETOOTH_DISCARD_REJECT:
                    gestionJeuBluetooth.recevoirRefusRecommencer();
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuBluetooth, 1);
                    break;
                case StaticFields.BLUETOOTH_SEND_NAME:
                    gestionJeuBluetooth.recevoirNomAdversaire(copyOfRange(readBuf, 2, readBuf[1]+1));
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuBluetooth, 2 + readBuf[1]);
            }
        }
    }

    private static void poursuivreEventuellement(byte[] readBuf, int nbBytes, GestionJeuBluetooth gestionJeuBluetooth, int nbByteNormal) {
        if (nbBytes != nbByteNormal) {
            if (nbBytes < nbByteNormal) {
                try {
                    throw new Exception("PROBLEME");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                byte[] nouveauBuffer = copyOfRange(readBuf, nbByteNormal, nbBytes - 1);
                recevoirMessage(nouveauBuffer, nbBytes - nbByteNormal, gestionJeuBluetooth);
            }
        }
    }
}
