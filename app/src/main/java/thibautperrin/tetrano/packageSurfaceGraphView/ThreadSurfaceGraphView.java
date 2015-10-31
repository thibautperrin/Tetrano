package thibautperrin.tetrano.packageSurfaceGraphView;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.SurfaceHolder;

import java.util.Arrays;

public class ThreadSurfaceGraphView extends Thread {

    private final SurfaceHolder surfaceHolder;
    private boolean doitAfficher = false;
    private boolean runBool = false;
    private boolean orientationVerticale;
    private boolean formeCarre = false;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private RectF rectangle = new RectF();
    private int cote = 1;
    private byte[] matrice; // 0: anneau non joué, 1: anneau joué par J1, 2: anneau joué par J2.
    private boolean[] matriceAnneauxAffiches;
    private int[] couleur; // 0: aucun anneau joué, 1: couleur J1, 2: couleur J2, 3 couleur cadre, 4 couleur fond
    private byte joueurAnneauSelection = 0; // Le joueur correspondant à la couleur des anneaux à selectionner.
    private int anneauDeplacementAdversaire = -1;
    private float fractionDeplacementAdversaire = 0.0f;
    private float angleAnneaux = 360;

    private float posXAnneauSelectionne = 0.0f;
    private float posYAnneauSelectionne = 0.0f;
    private int tailleAnneauSelectionne = -1;

    public ThreadSurfaceGraphView(SurfaceHolder surfaceHolder, SurfaceGraphView surfaceGraphView) {
        this.surfaceHolder = surfaceHolder;
        surfaceGraphView.preciseHandler(new HandlerSurfaceGraphView(this));
        this.matrice = new byte[64];
        this.couleur = new int[5];
        couleur[0] = 0xffe6e6e6;
        couleur[1] = 0xff0000ff;
        couleur[2] = 0xffff0000;
        couleur[3] = 0xff000000;
        couleur[4] = 0xfffcfcfc;

        matriceAnneauxAffiches = new boolean[64];
        for (int i = 0; i < 64; i++) {
            matriceAnneauxAffiches[i] = true;
        }
    }

    public void changerCouleur(int couleur, int indice) {
        synchronized (surfaceHolder) {
            this.couleur[indice] = couleur;
            doitAfficher = true;
        }
    }

    @Override
    public void run() {
        Canvas canvas = null;
        while (runBool) {
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (doitAfficher) {
                try {
                    canvas = surfaceHolder.lockCanvas();
                    synchronized (surfaceHolder) {
                        dessine(canvas);
                        doitAfficher = false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }
    }

    public void dessine(Canvas canvas) {
        if (canvas != null) {
            // Rempli le fond:
            canvas.drawColor(couleur[4]);

            // Dessine la grille:
            Dessin.dessineGrille(canvas, paint, cote, couleur[3]);

            // Dessine les anneaux déjà joués:
            for (int numero = 0; numero < 64; numero++) {
                if (matriceAnneauxAffiches[numero]) {
                    Dessin.dessineAnneauNumero(canvas, paint, cote, rectangle,
                            couleur[matrice[numero]], numero);
                }
            }

            // Dessine les 4 anneaux:
            if (!formeCarre) {
                Dessin.dessineAnneauASaisir(canvas, paint, cote, rectangle,
                        couleur[joueurAnneauSelection], orientationVerticale, angleAnneaux);
            }

            // Dessine l'anneau en cours de déplacement automatique en ligne droite:
            if (anneauDeplacementAdversaire >= 0) {
                Dessin.dessineTrajetAnneau(canvas, paint, cote, rectangle,
                        couleur[joueurAnneauSelection], orientationVerticale,
                        anneauDeplacementAdversaire, fractionDeplacementAdversaire);
            }

            // Dessine l'anneau que l'utilisateur est en train de déplacer:
            if (tailleAnneauSelectionne >= 0) {
                Dessin.dessineAnneau(canvas, paint, cote, rectangle,
                        couleur[joueurAnneauSelection], posXAnneauSelectionne,
                        posYAnneauSelectionne, tailleAnneauSelectionne);
            }
        }
    }

    public void setRunning(boolean b) {
        this.runBool = b;
    }

    public void setSurfaceSize(int cote, boolean orientationVerticale, boolean formeCarre) {
        synchronized (surfaceHolder) {
            this.cote = cote;
            this.orientationVerticale = orientationVerticale;
            this.formeCarre = formeCarre;
            doitAfficher = true;
        }
    }

    protected void jouerAnneau(byte joueur, int numeroAnneau) {
        synchronized (surfaceHolder) {
            this.matrice[numeroAnneau] = joueur;
            anneauDeplacementAdversaire = -1;
            tailleAnneauSelectionne = -1;
            doitAfficher = true;
        }
    }

    protected void afficherAnneauASelectionner(byte joueur, float angle) {
        synchronized (surfaceHolder) {
            this.joueurAnneauSelection = joueur;
            this.angleAnneaux = angle;
            doitAfficher = true;
        }
    }

    protected void afficherDeplacementAnneau(int numeroAnneau, float fraction) {
        synchronized (surfaceHolder) {
            anneauDeplacementAdversaire = numeroAnneau;
            fractionDeplacementAdversaire = fraction;
            doitAfficher = true;
        }
    }

    protected void afficherAnneauSelectionne(float posx, float posy) {
        synchronized (surfaceHolder) {
            posXAnneauSelectionne = posx;
            posYAnneauSelectionne = posy;
            doitAfficher = true;
        }
    }

    protected void changeTailleAnneauSelectionne(int tailleAnneauDeplace) {
        synchronized (surfaceHolder) {
            tailleAnneauSelectionne = tailleAnneauDeplace;
            doitAfficher = true;
        }
    }

    protected void redemarrerPartieDepuis(byte[] matrice) {
        synchronized (surfaceHolder) {
            if (matrice != null) {
                System.arraycopy(matrice, 0, this.matrice, 0, 64);
                Arrays.fill(matriceAnneauxAffiches, true);
                doitAfficher = true;
            }
        }
    }

    protected void setAnneauClignotant(int numero, boolean afficher) {
        synchronized (surfaceHolder) {
            matriceAnneauxAffiches[numero] = afficher;
            doitAfficher = true;
        }
    }

    protected void deplacementValidee(int numeroAnneau) {
        synchronized (surfaceHolder) {
            tailleAnneauSelectionne = -1;
            this.matrice[numeroAnneau] = joueurAnneauSelection;
            joueurAnneauSelection = 0;
            doitAfficher = true;
        }
    }
}