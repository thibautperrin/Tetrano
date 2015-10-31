package thibautperrin.tetrano.packageConnexions.packageJeuEnLigne;

import java.util.Arrays;

import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageConnexions.GestionMessage;
import thibautperrin.tetrano.packageGestionJeu.GestionJeuEnLigne;

/**
 * Created by Thibaut PERRIN on 15/06/2015.
 */
public class GestionMessageInternet extends GestionMessage{
    public static void demanderDerniereVersion(InternetService internet) {
        byte[] message = new byte[1];
        message[0] = StaticFields.INTERNET_ASK_VERSION;
        internet.write(message);
    }

    public static void jouerUtilisateur(InternetService internet, int numeroAnneau) {
        byte[] buffer = new byte[1];
        buffer[0] = (byte) numeroAnneau;
        internet.write(buffer);
    }

    public static void demanderAnnuler(InternetService internet, byte[] signatureJeuActuel) {
        int size = signatureJeuActuel.length;
        byte[] message = new byte[size + 1];
        message[0] = StaticFields.INTERNET_TRANSFER_ASK_CANCEL;
        System.arraycopy(signatureJeuActuel, 0, message, 1, size);
        internet.write(message);
    }

    public static void accepterAnnulation(InternetService internet) {
        byte[] message = new byte[1];
        message[0] = StaticFields.INTERNET_TRANSFER_ACCEPT_CANCEL;
        internet.write(message);
    }


    public static void demanderRecommencer(InternetService internet, byte[] signature) {
        int size = signature.length;
        byte[] message = new byte[size + 1];
        message[0] = StaticFields.INTERNET_TRANSFER_ASK_RESTART;
        System.arraycopy(signature, 0, message, 1, size);
        internet.write(message);
    }

    public static void accepterRecommencer(InternetService internet, boolean jeCommence) {
        byte[] message = new byte[2];
        message[0] = StaticFields.INTERNET_TRANSFER_ACCEPT_RESTART;
        message[1] = jeCommence ? (byte) 1 : (byte) 0;
        internet.write(message);
    }

    public static void refuserRecommencer(InternetService internet) {
        byte[] message = new byte[1];
        message[0] = StaticFields.INTERNET_TRANSFER_REJECT_RESTART;
        internet.write(message);
    }



    public static void envoyerMonNom(InternetService internet, String monNom) {
        byte[] nom = monNom.getBytes();
        if (nom.length > 40) {
            nom = copyOfRange(nom, 0, 39);
        }
        byte[] message = new byte[2 + nom.length];
        message[0] = StaticFields.INTERNET_TRANSFER_NAME;
        message[1] = (byte) nom.length;
        System.arraycopy(nom, 0, message, 2, nom.length);
        internet.write(message);
    }


    public static void recevoirMessage(byte[] readBuf, int nbBytes, GestionJeuEnLigne gestionJeuEnLigne) {
        if (0 <= readBuf[0] && readBuf[0] < 64) {
            gestionJeuEnLigne.adversaireInternetJoue(readBuf[0]);
            poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 1);
        } else {
            switch (readBuf[0]) {
                case StaticFields.INTERNET_RECEIVE_CONNECTION_LOST:
                    gestionJeuEnLigne.sortir();
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 1);
                    break;
                case StaticFields.INTERNET_RECEIVE_VERSION:
                    gestionJeuEnLigne.recevoirInformationVersion(readBuf[1]);
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 2);
                    break;
                case StaticFields.INTERNET_RECEIVE_SO_I_BEGIN:
                    gestionJeuEnLigne.recevoirAdversaire(true);
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 1);
                    break;
                case StaticFields.INTERNET_RECEIVE_SO_HE_BEGINS:
                    gestionJeuEnLigne.recevoirAdversaire(false);
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 1);
                    break;
                case StaticFields.INTERNET_TRANSFER_NAME:
                    gestionJeuEnLigne.recevoirNomAdversaire(copyOfRange(readBuf, 2, readBuf[1]+1));
                    System.out.println("Recois nom, "+ Arrays.toString(readBuf));
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 2 + readBuf[1]);
                    break;
                case StaticFields.INTERNET_TRANSFER_ASK_CANCEL:
                    gestionJeuEnLigne.recevoirDemandeAnnulation(copyOfRange(readBuf, 1, 13));
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 14);
                    break;
                case StaticFields.INTERNET_TRANSFER_ACCEPT_CANCEL:
                    gestionJeuEnLigne.recevoirAcceptationDeMonAnnulation();
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 1);
                    break;
                case StaticFields.INTERNET_TRANSFER_ASK_RESTART:
                    gestionJeuEnLigne.recevoirDemandeRecommencer(copyOfRange(readBuf, 1, 13));
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 14);
                    break;
                case StaticFields.INTERNET_TRANSFER_ACCEPT_RESTART:
                    gestionJeuEnLigne.recevoirAcceptationRecommencer(readBuf[1] == 1);
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 2);
                    break;
                case StaticFields.INTERNET_TRANSFER_REJECT_RESTART:
                    gestionJeuEnLigne.recevoirRefusRecommencer();
                    poursuivreEventuellement(readBuf, nbBytes, gestionJeuEnLigne, 1);
            }
        }
    }

    private static void poursuivreEventuellement(byte[] readBuf, int nbBytes, GestionJeuEnLigne gestionJeuEnLigne, int nbByteNormal) {
        System.out.println("poursuivreEventuellement("+Arrays.toString(readBuf)+", "+nbBytes+", ..., "+nbByteNormal);
        if (nbBytes != nbByteNormal) {
            if (nbBytes < nbByteNormal) {
                try {
                    throw new Exception("PROBLEME");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                byte[] nouveauBuffer = copyOfRange(readBuf, nbByteNormal, nbBytes - 1);
                recevoirMessage(nouveauBuffer, nbBytes - nbByteNormal, gestionJeuEnLigne);
            }
        }
    }

}
