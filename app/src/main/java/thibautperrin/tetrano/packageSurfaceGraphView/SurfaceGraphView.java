package thibautperrin.tetrano.packageSurfaceGraphView;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import thibautperrin.tetrano.R;
import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageGestionJeu.InterfaceGameManagement;

public class SurfaceGraphView extends SurfaceView implements SurfaceHolder.Callback {

    private HandlerSurfaceGraphView handler;
    private ThreadSurfaceGraphView threadSurfaceGraphView;
    private InterfaceGameManagement game;
    private boolean orientationVerticale = true;
    private boolean formeCarre = false;
    private int cote = 1;
    private boolean deplacementAutorisee = false;
    private int tailleAnneauDeplace = -1;

    public SurfaceGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        initialiseThread(holder);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ButtonPreviousNext,
                0, 0);
        try {
            formeCarre = a.getBoolean(R.styleable.SurfaceGraphView_square, false);
        } finally {
            a.recycle();
        }

    }

    public void initialiseThread(SurfaceHolder holder) {
        deplacementAutorisee = false;
        tailleAnneauDeplace = -1;
        threadSurfaceGraphView = new ThreadSurfaceGraphView(holder, this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (threadSurfaceGraphView.getState() != Thread.State.NEW) {
            initialiseThread(holder);
        }
        threadSurfaceGraphView.setRunning(true);
        threadSurfaceGraphView.start();
        if (game != null) {
            game.setSurfaceGraphView(this);
            commencer();
        }
    }

    private void commencer() {
        if (game != null) {
            game.begin();
        } else {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    commencer();
                }
            }, 80);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        doMeasure(width, height);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        doMeasure(width, height);
    }

    private void doMeasure(int width, int height) {
        this.orientationVerticale = (height >= width);
        if (formeCarre) {
            if (width == 0) {
                cote = height;
            } else if (height == 0) {
                cote = width;
            } else {
                cote = Math.min(width, height);
                setMeasuredDimension(cote, cote);
            }
        } else {
            if (orientationVerticale) {
                if (width == 0) {
                    cote = height * 4 / 5;
                } else if (height == 0) {
                    cote = width;
                } else {
                    cote = Math.min(width, height * 4 / 5);
                }
                setMeasuredDimension(cote, cote * 5 / 4);
            } else {
                if (width == 0) {
                    cote = height;
                } else if (height == 0) {
                    cote = width * 4 / 5;
                } else {
                    cote = Math.min(width * 4 / 5, height);
                }
                setMeasuredDimension(cote * 5 / 4, cote);
            }
        }
        threadSurfaceGraphView.setSurfaceSize(cote, orientationVerticale, formeCarre);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        tuerThread();
    }

    public void tuerThread() {
        boolean retry = true;
        threadSurfaceGraphView.setRunning(false);
        while (retry) {
            try {
                threadSurfaceGraphView.join();
                retry = false;
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void setFormeCarre(boolean formeCarre) {
        this.formeCarre = formeCarre;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (this.deplacementAutorisee) {
            float posx = event.getX();
            float posy = event.getY();
            float posu = (orientationVerticale ? posx : posy);
            float posv = (orientationVerticale ? posy : posx);

            if (posv > this.cote && this.tailleAnneauDeplace == -1) {
                this.tailleAnneauDeplace = (int) posu * 4 / this.cote;
                changeTailleAnneauSelectionne(tailleAnneauDeplace);
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) { // Si l'evenement est un relâché du doigt sur l'écran
                if (posv > this.cote) {
                    this.tailleAnneauDeplace = -1;
                    changeTailleAnneauSelectionne(tailleAnneauDeplace);
                }
                if (this.tailleAnneauDeplace != -1) {
                    int i = (int) posx * 4 / this.cote;
                    int j = (int) posy * 4 / this.cote;
                    if (i > 3) i = 3;
                    if (i < 0) i = 0;
                    if (j > 3) j = 3;
                    if (j < 0) j = 0;
                    byte numeroAnneau = (byte) (i + 4 * j + 16 * tailleAnneauDeplace);
                    this.tailleAnneauDeplace = -1;
                    if (game.userTriedPlaying(numeroAnneau)) {
                        deplacementAutorisee = false;
                        deplacementValidee(numeroAnneau);
                        game.postRingPlayedByUser();
                    } else {
                        changeTailleAnneauSelectionne(-1);
                    }
                }
            }
            if (tailleAnneauDeplace >= 0) {
                afficherAnneauSelectionne(posx, posy);
            }
        }
        return true; // À peut être changer à un moment donné.
    }


    public void preciseHandler(HandlerSurfaceGraphView handler) {
        this.handler = handler;
    }

    public void setGameManagement(InterfaceGameManagement game) {
        this.game = game;
    }

    /**
     * Cette methode permet d'afficher les anneaux a selectionner de la couleur du joueur,
     * d'autoriser les deplacements, et d'afficher les deplacements (quand le doigt se deplace,
     * l'anneau suit).
     * Une fois que l'anneau est depose par l'utilisateur dans une case valide (case non occupe par
     * un anneau de meme taille), les anneau a selectionner ne sont plus visible.
     * Cette méthode doit également permettre d'interdire le déplacement, si on lui met 0 en
     * argument. Ca peut être utile lorsque l'un des joueurs a annulé son dernier coup. Il faut donc
     * empêcher le second joueur de sélectionner un anneau.
     * On pourra envisager le cas où on passe directement de autoriser joueur1 à autoriser joueur2.
     * Dans ce cas, il faut bien déselectionner l'anneau du joueur 1 et changer la couleur des
     * anneaux à saisir.
     *
     * @param joueur 0, 1 ou 2: 0 interdit le déplacement.
     */
    public void autoriserDeplacement(byte joueur, int delai) {
        afficherAnneauASelectionner(joueur, delai);
        if (delai == 0) {
            deplacementAutorisee = joueur != 0;
        } else {
            if (joueur == 0) {
                this.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        deplacementAutorisee = false;
                    }
                }, delai);

            } else {
                this.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        deplacementAutorisee = true;
                    }
                }, delai);
            }
        }
    }


    /**
     * @param couleur La nouvelle couleur
     * @param indice  l'indice dans le tableau couleur, de la couleur à changer.
     */
    public void changerCouleur(int couleur, int indice) {
        Message msg = handler.obtainMessage(StaticFields.CHANGE_COLOR);
        msg.arg1 = couleur;
        msg.arg2 = indice;
        handler.sendMessage(msg);
    }

    /**
     * Permet de redémarrer une partie depuis une situation donnée par la matrice.
     *
     * @param matrice 64 bytes: 0: anneau non joué, 1: anneau joué par J1, 2: anneau joué par J2.
     */
    public void redemarrerPartieDepuis(byte[] matrice) {
        handler.removeMessages(StaticFields.BLINK_DISPLAY);
        handler.removeMessages(StaticFields.BLINK_HIDE);
        Bundle bundle = new Bundle();
        bundle.putByteArray("matrice", matrice);
        Message msg = handler.obtainMessage(StaticFields.RESTART_GAME_FROM);
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    public void cliquerClignoter(int numeroAnneau, int nombre, int periode, int delai) {
        Message msg;
        for (int i = 0; i < nombre; i++) {
            msg = handler.obtainMessage(StaticFields.BLINK_HIDE);
            msg.arg1 = numeroAnneau;
            handler.sendMessageDelayed(msg, i * periode + delai);
            msg = handler.obtainMessage(StaticFields.BLINK_DISPLAY);
            msg.arg1 = numeroAnneau;
            handler.sendMessageDelayed(msg, i * periode + periode / 2 + delai);
        }
    }

    /**
     * Permet de visualiser le déplacement de l'anneau joué par l'adversaire, puis de permettre à
     * l'utilisateur de jouer.
     */
    public void jouerAdversaireAnimer(int numeroAnneau, byte joueurAdversaire, byte joueurSuivant, int duree, int nbFrames) {
        afficherAnneauASelectionner(joueurAdversaire, 0);
        afficherDeplacementAnneau(numeroAnneau, 0, duree, nbFrames, true);
        jouerAnneau(joueurAdversaire, numeroAnneau, duree);
        autoriserDeplacement(joueurSuivant, duree);
    }

    /**
     * Envoie un message au handler pour faire jouer le joueur en paramètre.
     *
     * @param joueur       : 1 ou 2.
     * @param numeroAnneau entre 0 et 63
     * @param delai        en milliseconde
     */
    public void jouerAnneau(byte joueur, int numeroAnneau, int delai) {
        Message msg = handler.obtainMessage(StaticFields.PLAY_RING, joueur, numeroAnneau);
        handler.sendMessageDelayed(msg, delai);
    }

    /**
     * Affiche les quatres anneaux "à selectionner" de la couleur du joueur en paramètre.
     *  @param joueur : 0: ne pas afficher. 1: J1, 2: J2
     * @param delai  en milliseconde, représente la durée à attendre avant d'afficher.
     * @param angle : entre 0 et 360, représente la fraction d'anneau visible.
     */
    public void afficherAnneauASelectionner(byte joueur, int delai, float angle) {
        Message msg = handler.obtainMessage(StaticFields.DISPLAY_4_RINGS);
        msg.arg1 = joueur;
        msg.obj = angle;
        handler.sendMessageDelayed(msg, delai);
    }
    public void afficherAnneauASelectionner(byte joueur, int delai) {
        afficherAnneauASelectionner(joueur, delai, 360);
    }


    /**
     * Envoie de messages au handler pour afficher le déplacement linéaire d'un anneau joué par
     * l'adversaire.
     *
     * @param numeroAnneau entre 0 et 63: le numéro de l'anneau déplacé
     * @param duree        en milliseconde: représente la durée du déplacement complet.
     * @param nbFrames     indique le nombre de rafraichissement de l'écran pour se déplacement.
     * @param placer       false si on désire retirer l'anneau.
     */
    public void afficherDeplacementAnneau(int numeroAnneau, int instantDepart, int duree, int nbFrames, boolean placer) {
        for (int i = 0; i < nbFrames; i++) {
            Message msg = handler.obtainMessage(StaticFields.MOVE_RING_NUMBER);
            msg.arg1 = numeroAnneau;
            int numFrame = placer ? i : nbFrames - i;
            msg.arg2 = numFrame * (Integer.MAX_VALUE / nbFrames);
            handler.sendMessageDelayed(msg, instantDepart + i * duree / nbFrames);
        }
        Message msg = handler.obtainMessage(StaticFields.MOVE_RING_NUMBER);
        msg.arg1 = -1;
        msg.arg2 = Integer.MAX_VALUE;
        handler.sendMessageDelayed(msg, instantDepart + duree);
    }

    /**
     * Envoie un message au handler pour afficher l'anneau que l'utilisateur déplace.
     *
     * @param posx coordonnée X du centre de l'anneau
     * @param posy coordonnée Y du centre de l'anneau
     */
    private void afficherAnneauSelectionne(float posx, float posy) {
        Message msg = handler.obtainMessage(StaticFields.MOVE_RING);
        msg.arg1 = (int) (posx * 1000.0f);
        msg.arg2 = (int) (posy * 1000.0f);
        handler.sendMessage(msg);
    }

    private void changeTailleAnneauSelectionne(int tailleAnneauDeplace) {
        Message msg = handler.obtainMessage(StaticFields.CHANGE_RING_SELECTED);
        msg.arg1 = tailleAnneauDeplace;
        handler.sendMessage(msg);
    }

    private void deplacementValidee(int numeroAnneau) {
        Message msg = handler.obtainMessage(StaticFields.MOVE_CHOICE_ACCEPTED);
        msg.arg1 = numeroAnneau;
        handler.sendMessage(msg);
    }
}