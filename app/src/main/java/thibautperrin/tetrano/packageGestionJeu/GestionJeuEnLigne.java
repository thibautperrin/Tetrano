package thibautperrin.tetrano.packageGestionJeu;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.widget.Toast;

import java.util.Arrays;

import thibautperrin.tetrano.Intelligence2.Partie;
import thibautperrin.tetrano.JeuActivity;
import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageConnexions.packageJeuEnLigne.GestionMessageInternet;
import thibautperrin.tetrano.packageConnexions.packageJeuEnLigne.InternetHandler;
import thibautperrin.tetrano.packageConnexions.packageJeuEnLigne.InternetService;
import thibautperrin.tetrano.packageSurfaceGraphView.SurfaceGraphView;

import static junit.framework.Assert.assertTrue;

/**
 * Cette classe permet la gestion d'un jeu en ligne.
 * Created by Thibaut PERRIN on 15/06/2015.
 */
public class GestionJeuEnLigne extends GestionJeu {
    private AlertDialog dialogDemanderAnnuler;
    private AlertDialog dialogDemanderRecommencer;
    private InternetService internet;
    private boolean partieCommence;

    public GestionJeuEnLigne(SurfaceGraphView surfaceGraphView, Bundle bundle, SauvegardePartie sauvegarde, JeuActivity jeuActivity) {
        super(surfaceGraphView, jeuActivity);
        partieCommence = false;
        InternetHandler handler = new InternetHandler(this);
        internet = new InternetService(handler);
        internet.start();
        noms[1] = bundle.getString(StaticFields.LABEL_USER_NAME);
        noms[2] = bundle.getString(StaticFields.LABEL_ADVERSARY_NAME);
        couleurs[1] = bundle.getInt(StaticFields.LABEL_USER_COLOR);
        couleurs[2] = bundle.getInt(StaticFields.LABEL_ADVERSARY_COLOR);
        partie = new Partie();
        this.sauvegarde = sauvegarde;
        sauvegarde.extraireSauvegarde(partie);
    }

    public void onConnecting() {
        activity.setTitle(res.getString(R.string.Connecting_To_Server));
        afficher(res.getString(R.string.Connecting), Toast.LENGTH_LONG);
    }

    public void onConnected() {
        afficher(res.getString(R.string.Connection_Succeeded), Toast.LENGTH_SHORT);
        activity.setTitle(res.getString(R.string.Searching_Adversary));
        GestionMessageInternet.demanderDerniereVersion(internet);
    }

    public void recevoirInformationVersion(int versionServeur) {
        Context context = surfaceGraphView.getContext();
        String pName = context.getPackageName();
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(pName, 0);
            int versionApplication = pInfo.versionCode;
            if (versionServeur > versionApplication) {
                afficher(res.getString(R.string.Sentence_Version_Internet), Toast.LENGTH_LONG);
            }
        } catch (Exception e) {
            e.printStackTrace();
            afficheErreurInterne();
        }
    }

    public void recevoirAdversaire(boolean jeCommence) {
        partieCommence = true;
        if (jeCommence) {
            begin();
            assertTrue(partie.getProchainJoueur() == 1);
        } else {
            partie.restart(true);
            begin();
            assertTrue(partie.getProchainJoueur() == 2);
        }
        GestionMessageInternet.envoyerMonNom(internet, app.getNomUtilisateur());
    }

    @Override
    public void postRingPlayedByUser() {
        try {
            byte numeroAnneau = partie.getLastAnneau();
            GestionMessageInternet.jouerUtilisateur(internet, numeroAnneau);
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
                activity.setTitle(res.getString(R.string.Winner) +": "+ noms[gagnant]);
            } else {
                activity.setTitle(R.string.Sentence_End_Null);
            }
        } else {
            if (partie.getAnoAlig().getNbAnneauxJoues() == 0 && partie.getProchainJoueur() == 1) {
                activity.setTitle(R.string.Sentence_Begin_Start);
            } else if (partie.getProchainJoueur() == 1){
                activity.setTitle(noms[1] + " " + res.getString(R.string.Your_Turn));
            } else {
                activity.setTitle(noms[2]+" "+ res.getString(R.string.Is_Thinking));
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
        GestionMessageInternet.demanderAnnuler(internet, partie.getSignature());
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
        GestionMessageInternet.demanderRecommencer(internet, partie.getSignature());
        activity.retourUtilisateurBoutonRecommencer();
    }

    @Override
    protected void logGame() {
        app.getGameLog().addGame(StaticFields.GAME_ONLINE, null, activity);
    }

    @Override
    public void begin() {
        if (this.partieCommence) {
            super.begin();
            if (!partie.estFini()) {
                if (partie.getProchainJoueur() == 1) {
                    autoriserUtilisateurAJouer((byte) 1, 0);
                }
            }
        }
    }

    public void adversaireInternetJoue(byte numeroAnneau) {
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


    public void recevoirDemandeAnnulation(final byte[] signatureJeu) {
        if (Arrays.equals(partie.getSignature(), signatureJeu)) {
            if (occuped) /* on recommence dans 0,15 secondes */ {
                surfaceGraphView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        recevoirDemandeAnnulation(signatureJeu);
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
            builder.setMessage(noms[2]+ " "+
                    res.getString(R.string.Sentence_Ask_Cancel));
            builder.setPositiveButton(res.getString(R.string.Accept), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        GestionMessageInternet.accepterAnnulation(internet);
                        setOccuped(1200);
                        byte numero = partie.dejouer();
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
            byte numero = partie.dejouer();
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
            builder.setMessage(noms[2]+ " "+
                    res.getString(R.string.Sentence_Ask_Restart));
            builder.setPositiveButton(res.getString(R.string.Accept), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        partie.restart(true);
                        boolean jeCommence = partie.getProchainJoueur() == 1;
                        GestionMessageInternet.accepterRecommencer(internet, jeCommence);
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
                    GestionMessageInternet.refuserRecommencer(internet);
                    if (partie.getProchainJoueur() == 1 && !partie.estFini()) {
                        autoriserUtilisateurAJouer((byte) 1, 0);
                    }
                }
            });
            builder.setCancelable(false);
            dialogDemanderRecommencer = builder.create();
        }
        dialogDemanderRecommencer.show();
    }

    public void recevoirDemandeRecommencer(final byte[] signatureJeu) {
        if (Arrays.equals(partie.getSignature(), signatureJeu)) {
            if (occuped) /* on recommence dans 0,15 secondes */ {
                surfaceGraphView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        recevoirDemandeRecommencer(signatureJeu);
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
        if (partie.getProchainJoueur() == 2 != ilCommence) {
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
        GestionMessageInternet.envoyerMonNom(internet, app.getNomUtilisateur());
        mettreLeBonTitre();
        surfaceGraphView.changerCouleur(couleurs[1], 1);
        surfaceGraphView.changerCouleur(couleurs[2], 2);
    }

    @Override
    public void sortir() {
        internet.stop();
        super.sortir();
    }

    public void connectionFailed(boolean inInitiation) {
        if (inInitiation) {
            afficher(res.getString(R.string.Sentence_Internet_Connection_Problem), Toast.LENGTH_LONG);
        }
        sortir();
    }
}
