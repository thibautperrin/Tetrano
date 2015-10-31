package thibautperrin.tetrano.packageGestionJeu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import java.util.Arrays;

import thibautperrin.tetrano.ApplicationTetrano;
import thibautperrin.tetrano.Intelligence2.Partie;
import thibautperrin.tetrano.JeuActivity;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageConnexions.packageBluetooth.BluetoothHandler;
import thibautperrin.tetrano.packageConnexions.packageBluetooth.BluetoothService;
import thibautperrin.tetrano.packageConnexions.packageBluetooth.GestionMessageBluetooth;
import thibautperrin.tetrano.packageSurfaceGraphView.SurfaceGraphView;

import static junit.framework.Assert.assertNotNull;

/**
 * Cette classe permet la gestion d'un jeu en bluetooth.
 * Created by Thibaut PERRIN on 12/06/2015.
 */
public class GestionJeuBluetooth extends GestionJeu {

    private BluetoothService bluetoothService;
    private AlertDialog dialogDemanderAnnuler;
    private AlertDialog dialogDemanderRecommencer;

    public GestionJeuBluetooth(SurfaceGraphView surfaceGraphView, Bundle bundle, SauvegardePartie sauvegarde, JeuActivity jeuActivity) {
        super(surfaceGraphView, jeuActivity);
        ApplicationTetrano app = (ApplicationTetrano) jeuActivity.getApplication();
        assertNotNull(app);
        BluetoothHandler btH = app.getBluetoothHandler();
        btH.preciseGestionJeuBluetooth(this);
        bluetoothService = app.getBluetoothService();
        boolean utilisateurCommence = bundle.getBoolean(StaticFields.LABEL_USER_BEGINS);
        noms[1] = bundle.getString(StaticFields.LABEL_USER_NAME);
        noms[2] = bundle.getString(StaticFields.LABEL_ADVERSARY_NAME);
        couleurs[1] = bundle.getInt(StaticFields.LABEL_USER_COLOR);
        couleurs[2] = bundle.getInt(StaticFields.LABEL_ADVERSARY_COLOR);
        String distMacAddress = bundle.getString(StaticFields.LABEL_DIST_MAC_ADDRESS);
        String distDeviceName = bundle.getString(StaticFields.LABEL_DIST_DEVICE_NAME);
        partie = new Partie();
        this.sauvegarde = sauvegarde;
        sauvegarde.extraireSauvegarde(partie);
        if (utilisateurCommence != (partie.getJoueurCommencant() == 1)) {
            partie.restart(true);
        }
        // On envoit son nom avec un retard pour être sur que l'adversaire soit en
        // mesure de le recevoir.
        surfaceGraphView.postDelayed(new Runnable() {
            @Override
            public void run() {
                GestionMessageBluetooth.envoyerMonNom(bluetoothService,
                        noms[1]);
            }
        }, 800);
        app.addMacAdressBluetooth(activity, distMacAddress, distDeviceName);
    }

    @Override
    public void postRingPlayedByUser() {
        try {
            byte numeroAnneau = partie.getLastAnneau();
            GestionMessageBluetooth.jouerUtilisateur(bluetoothService, numeroAnneau);
        } catch (Exception e) {
            afficheErreurInterne();
            e.printStackTrace();
        }
        super.postRingPlayedByUser();
    }

    @Override
    public void jouerAdversaire() {
        // nothing to do.
    }

    @Override
    public void mettreLeBonTitre() {
        if (partie.estFini()) {
            byte gagnant = partie.getGagnant();
            if (gagnant == 1 || gagnant == 2) {
                activity.setTitle(res.getString(R.string.Winner) + ": " + noms[gagnant]);
            } else {
                activity.setTitle(R.string.Sentence_End_Null);
            }
        } else {
            if (partie.getAnoAlig().getNbAnneauxJoues() == 0 && partie.getProchainJoueur() == 1) {
                activity.setTitle(R.string.Sentence_Begin_Start);
            } else if (partie.getProchainJoueur() == 1) {
                activity.setTitle(noms[1] + " " + res.getString(R.string.Your_Turn));
            } else if (partie.getProchainJoueur() == 2) {
                activity.setTitle(noms[2] + " " + res.getString(R.string.Is_Thinking));
            }
        }
    }

    @Override
    public void cliqueAnnuler() {
        if (partie.getAnoAlig().getNbAnneauxJoues() == 0 ||
                partie.getProchainJoueur() == 1 ||
                occuped) {
            return;
        }
        GestionMessageBluetooth.demanderAnnuler(bluetoothService, partie.getSignature());
        activity.retourUtilisateurBoutonAnnuler();
    }

    @Override
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
        GestionMessageBluetooth.demanderRecommencer(bluetoothService, partie.getSignature());
        activity.retourUtilisateurBoutonRecommencer();
    }

    @Override
    protected void logGame() {
        app.getGameLog().addGame(StaticFields.GAME_BLUETOOTH, null, activity);
    }


    @Override
    public void begin() {
        super.begin();
        if (!partie.estFini()) {
            if (partie.getProchainJoueur() == 1) {
                autoriserUtilisateurAJouer((byte) 1, 0);
            }
        }
    }

    public void adversaireBluetoothJoue(byte numeroAnneau) {
        try {
            if (partie.getProchainJoueur() != 2) {
                throw new Exception("L'utilisateur joue alors que ce n'est pas à son tour.");
            }
            partie.jouer(numeroAnneau);
            postAnneauJoueParAdversaire(numeroAnneau, (byte) 2);
        } catch (Exception e) {
            afficheErreurInterne();
            e.printStackTrace();
        }
    }

    public void recevoirDemandeAnnulation(final byte[] signatureJeuActuel) {
        if (Arrays.equals(partie.getSignature(), signatureJeuActuel)) {
            if (occuped) /* on recommence dans 0,15 secondes */ {
                surfaceGraphView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        recevoirDemandeAnnulation(signatureJeuActuel);
                    }
                }, 150);
            } else {
                if (partie.getProchainJoueur() == 1 && !partie.estFini()) {
                    surfaceGraphView.autoriserDeplacement((byte) 0, 0);
                    demanderAutorisationAnnuler();
                }
            }
        }
    }

    private void demanderAutorisationAnnuler() {
        if (dialogDemanderAnnuler == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(surfaceGraphView.getContext());
            builder.setMessage(noms[2] + " " +
                    res.getString(R.string.Sentence_Ask_Cancel));
            builder.setPositiveButton(res.getString(R.string.Accept), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        GestionMessageBluetooth.accepterAnnulation(bluetoothService);
                        setOccuped(1200);
                        int numero = partie.dejouer();
                        surfaceGraphView.jouerAnneau((byte) 0, numero, 0);
                        surfaceGraphView.afficherAnneauASelectionner(partie.getProchainJoueur(), 0);
                        surfaceGraphView.afficherDeplacementAnneau(numero, 0, 1200, 30, false);
                        surfaceGraphView.autoriserDeplacement((byte) 0, 1200);
                    } catch (Exception e) {
                        afficheErreurInterne();
                        e.printStackTrace();
                    }
                }
            });
            builder.setNegativeButton(res.getString(R.string.Refuse), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    autoriserUtilisateurAJouer(partie.getProchainJoueur(), 0);
                }
            });
            builder.setCancelable(false);
            dialogDemanderAnnuler = builder.create();
        }
        dialogDemanderAnnuler.show();
    }

    public void recevoirAcceptationDeMonAnnulation() {
        try {
            setOccuped(1200);
            int numero = partie.dejouer();
            surfaceGraphView.jouerAnneau((byte) 0, numero, 0);
            surfaceGraphView.afficherAnneauASelectionner(partie.getProchainJoueur(), 0);
            surfaceGraphView.afficherDeplacementAnneau(numero, 0, 1200, 30, false);
            autoriserUtilisateurAJouer(partie.getProchainJoueur(), 1200);
        } catch (Exception e) {
            afficheErreurInterne();
            e.printStackTrace();
        }
    }


    private void demanderAutorisationRecommencer() {
        if (dialogDemanderRecommencer == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(surfaceGraphView.getContext());
            builder.setMessage(noms[2] + " " +
                    res.getString(R.string.Sentence_Ask_Restart));
            builder.setPositiveButton(res.getString(R.string.Accept), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        partie.restart(true);
                        boolean jeCommence = partie.getProchainJoueur() == 1;
                        GestionMessageBluetooth.accepterRecommencer(bluetoothService, jeCommence);
                        begin();
                    } catch (Exception e) {
                        afficheErreurInterne();
                        e.printStackTrace();
                    }
                }
            });
            builder.setNegativeButton(res.getString(R.string.Refuse), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    GestionMessageBluetooth.refuserRecommencer(bluetoothService);

                    if (partie.getProchainJoueur() == 1 && !partie.estFini()) {
                        autoriserUtilisateurAJouer(partie.getProchainJoueur(), 0);
                    }
                }
            });
            builder.setCancelable(false);
            dialogDemanderRecommencer = builder.create();
        }
        dialogDemanderRecommencer.show();
    }


    public void recevoirDemandeRecommencer(final byte[] signatureJeuActuel) {
        if (Arrays.equals(partie.getSignature(), signatureJeuActuel)) {
            if (occuped) /* on recommence dans 0,15 secondes */ {
                surfaceGraphView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        recevoirDemandeRecommencer(signatureJeuActuel);
                    }
                }, 150);
            } else {
                surfaceGraphView.autoriserDeplacement((byte) 0, 0);
                demanderAutorisationRecommencer();
            }
        }
    }

    public void recevoirAcceptationRecommencer(boolean ilCommence) {
        surfaceGraphView.autoriserDeplacement((byte) 0, 0);
        partie.restart(true);
        if ((partie.getProchainJoueur() == 2) != ilCommence) {
            partie.restart(true);
        }
        begin();
    }

    public void recevoirRefusRecommencer() {
        if (partie.getProchainJoueur() == 1) {
            autoriserUtilisateurAJouer((byte) 1, 0);
        }
    }

    public void recevoirNomAdversaire(byte[] bytes) {
        noms[2] = new String(bytes);
        mettreLeBonTitre();
    }

    @Override
    public void actualiserReglages() {
        noms[1] = app.getNomUtilisateur();
        couleurs[1] = app.getCouleurUtilisateur();
        couleurs[2] = app.getCouleurAdversaire();
        GestionMessageBluetooth.envoyerMonNom(bluetoothService, app.getNomUtilisateur());
        mettreLeBonTitre();
        surfaceGraphView.changerCouleur(couleurs[1], 1);
        surfaceGraphView.changerCouleur(couleurs[2], 2);
    }

    @Override
    public void sortir() {
        bluetoothService.stop();
        app.setBluetoothService(null, null);
        super.sortir();
    }
}
