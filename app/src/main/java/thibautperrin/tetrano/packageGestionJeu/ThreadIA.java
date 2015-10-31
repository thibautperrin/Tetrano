package thibautperrin.tetrano.packageGestionJeu;
import android.os.Handler;

import thibautperrin.tetrano.Intelligence2.IA;
import thibautperrin.tetrano.Intelligence2.Partie;
import thibautperrin.tetrano.StaticFields;

/**
 * Ce thread permet au smartphone de réflechir au prochain anneau à poser
 * dans un Thread différent du thread UI.
 * Il doit être crée et lancé, puis quand on a besoin de lui, il faut appeler la methode reflechir.
 * Cette classe pourrait réflechir aussi bien pour l'utilisateur que pour l'ordinateur. Elle
 * joue en fait pour le prochain joueur à jouer.
 *
 * Created by Thibaut PERRIN on 22/06/2015.
 */
public class ThreadIA extends Thread {

    private Handler handler;
    private Partie partie;
    private IA iA;
    private int niveauJeu;
    private boolean thinking;
    private boolean running;

    public ThreadIA(Handler handler, int niveauJeu) {
        this.handler = handler;
        this.partie = new Partie();
        iA = new IA(partie.getAnoAlig());
        this.niveauJeu = niveauJeu;
        running = true;
        thinking = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(100);
                synchronized (ThreadIA.this) {
                    if (thinking) {
                        long time = System.nanoTime();
                        byte anneau = iA.jouerNiveau(partie.getProchainJoueur(), niveauJeu, handler);
                        handler.obtainMessage(StaticFields.LABEL_RING_CHOSEN, anneau, 0, partie.getSignature()).sendToTarget();
                        thinking = false;
                        long time2 = System.nanoTime();
                        System.out.println("Temps: "+ (time2-time));
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
                handler.obtainMessage(StaticFields.LABEL_EXCEPTION_THROWN).sendToTarget();
            }
        }
    }

    public void reflechir(byte[] anneauxJoues) {
        synchronized (ThreadIA.this) {
            if (!thinking) {
                partie.restart(false);
                for (byte anneauxJoue : anneauxJoues) {
                    partie.jouer(anneauxJoue);
                }
                thinking = true;
            }
        }
    }

    public void finish() {
        running = false;
    }
}
