package thibautperrin.tetrano.Intelligence2;

import android.os.Handler;
import android.support.v4.util.LongSparseArray;

import java.util.BitSet;

import thibautperrin.tetrano.StaticFields;
import thibautperrin.tetrano.packageChallenge.StarUsedBean;

/**
 * Created by Thibaut PERRIN on 11/07/2015.
 * Cette classe contient les élements de l'intelligence artificielle.
 */
public class IA {

    private final AnoAlig anoAlig;
    private BitSet tabBool = new BitSet(64);
    private LongSparseArray<Byte>[] memoire;

    public IA(AnoAlig anoAlig) {
        this.anoAlig = anoAlig;
        memoire = new LongSparseArray[6];
        for (int i = 0; i < 6; i++) {
            memoire[i] = new LongSparseArray<Byte>();
        }
    }

    /**
     * La méthode isFourchetteSimple prends un joueur et regarde
     * s'il possède une fourchette simple. S'il en possède une,
     * la méthode renvoit le numéro de l'anneau qu'il doit jouer,
     * c'est-à-dire l'anneau d'intersection des deux alignements.
     * S'il ne possède pas de fourchette simple, la méthode
     * renvoit -1.
     * Le principe de l'algorithme est le suivant:
     * On parcours l'ensemble des anneaux libres appartenant à un
     * alignement de deux anneaux. S'il y a des doublons, c'est
     * qu'il y a une fourchette avancée.
     * Ainsi, on parcourt ces anneaux et on les ajoute au fure et
     * à mesure dans une HashSet. On regarde à chaque fois si
     * l'anneau est présent avant de le stocker. S'il est déjà
     * présent, c'est qu'on l'a en double.
     *
     * @param joueur : Le joueur dont on cherche à determiner s'il a une fourchette.
     * @return le numéro de l'anneau de la fourchette, ou -1 en l'absence de fourchette.
     */
    public byte isFourchetteSimple(byte joueur) {
        if (anoAlig.getNbAnneauxJoues() > 61) {
            return -1;
        }
        tabBool.clear();
        Ensemble set; // Ensemble des alignements de 2 anneaux du joueur.
        if (joueur == 1) {
            set = anoAlig.ensembles[2];
        } else {
            set = anoAlig.ensembles[6];
        }

        // On parcourt les alignements de deux anneaux:
        byte numAlig = set.next((byte) -1);
        while (numAlig != -1) {
            // On récupère les deux anneaux vide d'un alignement:
            byte numAno1 = StatiqueAlig.trov2ano(anoAlig, numAlig);
            byte numAno2 = StatiqueAlig.trov2anoBis(anoAlig, numAlig);

            if (tabBool.get(numAno1)) {
                return numAno1;
            }
            tabBool.set(numAno1);

            if (tabBool.get(numAno2)) {
                return numAno2;
            }
            tabBool.set(numAno2);
            numAlig = set.next(numAlig);
        }
        return -1; // Pas de doublon: renvoit -1
    }

    /**
     * Cette fonction est utilisée dans les fonctions de fourchettes avancée.
     * Elle permet d'obtenir les anneaux que l'on peut jouer, forçant l'adversaire
     * à bloquer, mais ne nous forçant pas à bloquer en retour.
     *
     * @param ja : Le numero du joueur faisant la fourchette.
     * @return Un tableau d'entier: Chaque entier contient: l'anneau que l'on joue (1),
     * l'anneau que l'adversaire joue pour bloquer (2), et l'indice de la paire d'anneau (i).
     * Les entiers sont sous la forme: uu uu uu uu uu ii ii ii ii ii 22 22 22 11 11 11
     * les bits (u) ne sont pas utilisés.
     */
    private int[] listesAnneauxFourchette(byte ja) {
        tabBool.clear(); // tabBool représentes les anneaux libres des alignements advers de 2 anneaux.
        Ensemble setA; // ensemble de nos alignements de 2 anneaux
        Ensemble setB; // ensemble des alignements de 2 anneaux de l'adversaire

        // On ajuste les ensembles en fonction de si ja est joueur1 ou joueur2:
        if (ja == 1) {
            setA = anoAlig.ensembles[2];
            setB = anoAlig.ensembles[6];
        } else {
            setA = anoAlig.ensembles[6];
            setB = anoAlig.ensembles[2];
        }

        // Afin de ne pas se faire pièger par un alignement dans
        // lequel on joue un anneau et en bloquant, l'adversaire
        // nous force à bloquer, on récupère l'ensemble des anneaux
        // adversaire appartenant à un alignement de 2 anneaux:
        byte numAlig = setB.next((byte) -1);
        while (numAlig != -1) {
            // On récupère les deux anneaux vide d'un alignement:
            tabBool.set(StatiqueAlig.trov2ano(anoAlig, numAlig));
            tabBool.set(StatiqueAlig.trov2anoBis(anoAlig, numAlig));
            numAlig = setB.next(numAlig);
        }

        int[] liste = new int[2 * setA.length()];
        byte[] tab;
        int size = 0;
        numAlig = setA.next((byte) -1);
        while (numAlig != -1) {
            // On récupère les deux anneaux vides d'un alignement:
            tab = StatiqueAlig.trov2anoIndice(anoAlig, numAlig);
            if (!tabBool.get(tab[2])) {
                liste[size] = tab[1]
                        + (1 << 6) * tab[2]
                        + (1 << 12) * (12 * numAlig + 2 * tab[0]);
                size++;
            }
            if (!tabBool.get(tab[1])) {
                liste[size] = tab[2]
                        + (1 << 6) * tab[1]
                        + (1 << 12) * (12 * numAlig + 2 * tab[0] + 1);
                size++;
            }
            numAlig = setA.next(numAlig);
        }
        int[] resultat = new int[size];
        System.arraycopy(liste, 0, resultat, 0, size);
        return resultat;
    }

    /**
     * La méthode fourchetteAvancee permet de trouver la solution optimale de fourchette
     * avancée à un certain ordre.
     * Quand le joueur possède effectivement une fourchette avancée, elle renvoit la serie d'anneau
     * la plus courte à jouer pour gagner à partir de la fourchette avancée.
     * Si le joueur n'a pas de fourchette avancée, la méthode renvoit null.
     *
     * @param ja    : Le joueur qui essaye de faire une fourchette avancée
     * @param ordre : ordre jusqu'auquel on calcule la fourchette avancée
     * @return : null si le joueur ja n'a pas de fourchette avancée à cet ordre.
     * Un tableau contenant la série des numéro d'anneaux joués par la suite sinon.
     */
    public byte[] fourchetteAvancee(byte ja, int ordre) {
        // Si l'ordre risque de faire déborder le nombre de cases du plateau
        // ie n + 2*ordre + 3 > 64:
        if (anoAlig.getNbAnneauxJoues() > 61) {
            return null;
        }
        if (ordre * 2 + 3 + anoAlig.getNbAnneauxJoues() > 64) {
            // On ajuste l'ordre pour que ça rentre:
            return fourchetteAvancee(ja, (64 - 3 - anoAlig.getNbAnneauxJoues()) / 2);
        }

        // On commence par regarder s'il y a une fourchette simple:
        byte resFourchetteSimple = isFourchetteSimple(ja);
        if (resFourchetteSimple != -1) {
            return new byte[]{resFourchetteSimple};
        }

        // Si l'ordre est égal à 0: on ne va pas plus loin:
        if (ordre == 0) {
            return null;
        }

        // Là, il n'y a pas de fourchette simple et l'ordre est > 0.
        byte jb = (byte) (3 - ja);
        byte[] meilleureFin = null; // On mémorise la meilleure fin en
        int ordreRecherche = ordre - 1;  // en cours, ainsi que son ordre
        byte[] finPartieCourante;
        int ordreCourant;
        byte anneau1, anneau2;

        int[] tab = listesAnneauxFourchette(ja);
        for (int aTab : tab) {
            anneau1 = (byte) (aTab % (1 << 6));
            anneau2 = (byte) (aTab / (1 << 6) % (1 << 6));

            // On joue anneau1 et l'adversaire bloque avec anneau2:
            anoAlig.jouer(anneau1, ja);
            anoAlig.jouer(anneau2, jb);
            finPartieCourante = fourchetteAvancee(ja, ordreRecherche);
            anoAlig.dejouer(anneau2, jb);
            anoAlig.dejouer(anneau1, ja);
            // Cas où on a trouvé (mieux qu'avant):
            if (finPartieCourante != null) {
                ordreCourant = finPartieCourante.length / 2; // Critique
                finPartieCourante = superCopy(anneau1, anneau2, finPartieCourante);
                // Cas où on a trouvé une fourchette simple:
                if (ordreCourant == 0) {
                    return finPartieCourante;
                }
                ordreRecherche = ordreCourant - 1;
                meilleureFin = finPartieCourante;
            }

        }
        return meilleureFin; // (Eventuellement null)
    }

    private byte[] superCopy(byte anneau1, byte anneau2, byte[] anneaux) {
        byte[] res = new byte[anneaux.length + 2];
        res[0] = anneau1;
        res[1] = anneau2;
        System.arraycopy(anneaux, 0, res, 2, anneaux.length);
        return res;
    }

    private byte choixAnneauPoints(byte joueur) {
        byte[] reglages;
        if (joueur == 1) {
            reglages = new byte[]{10, 7, -12, 40, 40, 6, 10, 10, 40, 0}; // Les 40 ne seront pas utlisés...
        } else {
            reglages = new byte[]{10, 6, 10, 10, 40, 7, -12, 40, 40, 0}; // Les 40 ne seront pas utlisés...
        }
        return choixAnneauPoints(reglages);
    }

    private byte choixAnneauPoints(byte[] reglages) {
        byte[] meilleurs = new byte[20];
        byte taille = 0;
        byte best = Byte.MIN_VALUE;
        byte pointsCourant;
        for (byte numAno = 0; numAno < 64; numAno++) {
            if (anoAlig.anneaux[numAno] == 0) {
                pointsCourant = Statique.getPointsSimple(numAno, anoAlig.alignements, reglages);
                if (pointsCourant >= best) {
                    if (pointsCourant == best && taille < 20) {
                        meilleurs[taille] = numAno;
                        taille++;
                    } else {
                        best = pointsCourant;
                        meilleurs[0] = numAno;
                        taille = 1;
                    }
                }
            }
        }
        if (taille == 0) {
            return -1;
        } else if (taille == 1) {
            return meilleurs[0];
        } else {
            byte pos = (byte) (Math.random() * taille);
            return meilleurs[pos];
        }
    }


    /**
     * Cette méthode permet de choisir un anneau au hasard. Elle s'utilise dans les faibles
     * niveau de l'intelligence artificielle.
     *
     * @return un numéro d'anneau disponible au hasard, ou -1 si tout les anneaux ont été joués.
     */
    private byte choixAnneauRandom() {
        if (anoAlig.getNbAnneauxJoues() < 64) {
            byte pos = (byte) (Math.random() * (64 - anoAlig.getNbAnneauxJoues()));
            byte posCourant = 0;
            for (byte i = 0; i < 64; i++) {
                if (anoAlig.anneaux[i] == 0) {
                    if (posCourant == pos) {
                        return i;
                    } else {
                        posCourant++;
                    }
                }
            }
        }
        return -1;
    }


    /**
     * Cette fonction de blocage cherche à bloquer une fourchette avancée en un coup.
     * Dans le cas où elle n'arrive pas à la bloquer, elle renvoit l'anneau qui une fois joué
     * sera tel que la fourchette avancée de l'adversaire soit d'un ordre le plus grand possible.
     * Dans le cas où quoique l'on joue, on force l'adversaire à bloquer, cette fonction renvoit -1.
     *
     * @param jA     : Le joueur qui cherche à bloquer la fourchette de son adversaire.
     * @param ordreF : L'ordre de recherche des fourchettes que l'on essaie de bloquer.
     * @return : -1 si aucun anneau permet de jouerRapide sans forcer l'adversaire à bloquer.
     */
    public byte bloqueFourchetteAvanceeSimplifiee(byte jA, int ordreF, Handler handler) {
        // On commence par calculer les points de chaque anneau libre non bloquant:
        byte[] reglages;
        if (jA == 1) {
            reglages = new byte[]{10, 7, -12, 40, 40, 6, 10, 10, 40, 0}; // Les 40 ne seront pas utlisés...
        } else {
            reglages = new byte[]{10, 6, 10, 10, 40, 7, -12, 40, 40, 0}; // Les 40 ne seront pas utlisés...
        }
        byte sorteAlig = (jA == 1 ? (byte) 2 : (byte) 6);
        byte[] numsAn = new byte[64 - anoAlig.getNbAnneauxJoues()];
        byte[] points = new byte[64 - anoAlig.getNbAnneauxJoues()];
        byte size = 0;
        for (byte numAno = 0; numAno < 64; numAno++) {
            if (anoAlig.anneaux[numAno] == 0 &&
                    !Statique.isAnneauAppartientTelSorteAlig(numAno, anoAlig.alignements, sorteAlig)) {
                numsAn[size] = numAno;
                points[size] = Statique.getPointsSimple(numAno, anoAlig.alignements, reglages);
                size++;
            }
        }
        if (size == 0) return -1; // Si tout les anneaux libres sont bloquants, on renvoit -1.

        // À ce stade, les numeros des anneaux libre non bloquants sont stockés dans le tableau
        // numsAnn (entre 0 et size-1). Leurs points sont stockés dans le tableau points.
        // On va maintenant parcourir les anneaux libres non bloquants, pour trouver un anneau
        // qui bloque la fourchette, ou qui à defaut de la bloquer, lui donne un ordre plus élevé.
        // Le parcours ce fait évidemment dans l'ordre de l'anneau ayant le plus de point, vers
        // celui ayant le moins de points.
        byte meilleurAnneau = -1;
        byte numAno;
        byte ordreB = -1;
        byte max;
        byte posMax = -1;
        for (byte i = 0; i < size; i++) {
            max = Byte.MIN_VALUE;
            for (byte j = 0; j < size; j++) {
                if (numsAn[j] != -1) {
                    if (points[j] > max) {
                        max = points[j];
                        posMax = j;
                    }
                }
            }
            numAno = numsAn[posMax];
            numsAn[posMax] = -1;
            anoAlig.jouer(numAno, jA);
            byte[] res = fourchetteAvancee((byte) (3 - jA), ordreF);
            anoAlig.dejouer(numAno, jA);
            if (res == null) return numAno; // Si l'adversaire n'a plus de fourchette, c'est bon.
            else if (res.length / 2 > ordreB) {
                meilleurAnneau = numAno;
                ordreB = (byte) (res.length / 2);
            }
            if (handler != null) { // Plus pratique pour les test unitaires: handler null.
                handler.obtainMessage(StaticFields.LABEL_INFO_BLOCK_FORK, i, size).sendToTarget();
            }

        }
        return meilleurAnneau;
    }


    /**
     * Cette fonction choisit le prochain anneau pour un joueur donné, en fonction d'un
     * niveau de difficulté. Plus le niveau de difficulté est élevé, plus elle jouera intelligement.
     * Notons que l'anneau est renvoyé, mais n'est pas joué. Ainsi, on peut se servir de cette
     * méthode pour donner des suggestions par exemple: qu'est-ce que l'ordinateur jouerait à votre
     * place.
     * Action en fonction du niveau:
     * Quelque soit le niveau, l'ordinateur gagne quand il peut (ie en un coup).
     * <p/>
     * Entre 0 et 10:
     * L'ordinateur bloque parfois, avec une probabilité proportionnelle au niveau,
     * joue au hasard sinon.
     * <p/>
     * Entre 10 et 20:
     * L'ordinateur bloque parfois, avec une probabilité proportionnelle au niveau,
     * joue le "meilleur anneau", en rapport avec la méthode choixAnneauPoints.
     * <p/>
     * Entre 20 et 30:
     * L'ordinateur bloque toujours les tentatives d'alignement simple.
     * L'ordinateur utilise parfois ses fourchettes simple.
     * <p/>
     * Entre 30 et 35:
     * L'ordinateur bloque toujours les tentatives d'alignement simple.
     * L'ordinateur utilise les fourchettes simple dès qu'il en voit.
     * L'ordinateur commence parfois à bloquer les fourchettes simples.
     * <p/>
     * Entre 35 et 40:
     * L'ordinateur bloque toujours les tentatives d'alignement simple.
     * L'ordinateur utilise les fourchettes simple dès qu'il en voit.
     * L'ordinateur utilise parfois les fourchettes avancées d'ordre 1.
     * L'ordinateur bloque parfois les fourchettes simples.
     * <p/>
     * Entre 40 et 50:
     * L'ordinateur bloque toujours les tentatives d'alignement simple.
     * L'ordinateur utilise les fourchettes simple dès qu'il en voit.
     * L'ordinateur utilise parfois les fourchettes avancées d'ordre 1 et 2.
     * L'ordinateur bloque progressivement toutes les fourchettes simples.
     * <p/>
     * Entre 50 et 60:
     * L'ordinateur bloque toujours les tentatives d'alignement, et les fourchettes simples.
     * L'ordinateur commence à bloquer les fourchettes d'ordre 1.
     * L'ordinateur utilise toutes les fourchettes simple et avancées d'ordre 1.
     * L'ordinateur utilise quelques fourchettes d'ordre 2.
     * <p/>
     * Entre 60 et 70:
     * L'ordinateur utilise toutes les fourchettes d'ordre inférieure ou égale à 2.
     * L'ordinateur fait quelques fourchettes d'ordre 3.
     * L'ordinateur bloque progressivement les fourchettes d'ordre 1 et 2.
     * <p/>
     * Entre 70 et 80:
     * L'ordinateur utilise toutes les fourchettes d'ordre inférieure ou égale à 2.
     * L'ordinateur fait quelques fourchettes d'ordre 3 ou 4.
     * L'ordinateur bloque progressivement les fourchettes d'ordre 1 et 2
     * <p/>
     * Entre 80 et 90:
     * L'ordinateur utilise toutes les fourchettes d'ordre inférieure ou égale à 3
     * L'ordinateur fait quelques fourchettes d'ordre 4
     * L'ordinateur bloque les fourchettes d'ordre inférieur à 1.
     * L'ordinateur bloque progressivement les fourchettes d'ordre 2
     * <p/>
     * <p/>
     * Du point de vu des techniques:
     * Blocage des alignements simples: commence à 0, fini à 20.
     * Géneration fourchette simple: commence à 20, fini à 30.
     * Blocage fourchette simple: commence à 30, fini à 50.
     * Géneration fourchette ordre 1: commence à 35, fini à 50
     * Géneration fourchette ordre 2: commence à 40, fini à 60
     * Géneration fourchette ordre 3: commence à 60, fini à 80
     * Géneration fourchette ordre 4: commence à 70, fini à 90
     * Blocage fourchette ordre 1: commence à 50, fini à 80
     * Blocage fourchette ordre 2: commence à 60, fini à 90
     *
     * @param joueur: Le joueur qui va jouerRapide.
     * @param niveau: un nombre compris entre 0 et 1 (inclusivement).
     * @return le numéro de l'anneau à jouer.
     */
    public byte jouerNiveau(byte joueur, int niveau, Handler handler) {
        byte res;
        byte numAlig;
        int rand;

        // Dans tout les cas, si l'ordi peut gagner, il gagne.
        numAlig = anoAlig.ensembles[(joueur == 1 ? 3 : 7)].next((byte) -1);
        if (numAlig != -1) {
            return StatiqueAlig.trovLastAno(anoAlig, numAlig);
        }

        // L'ordinateur bloque toujours à partir du niveau 20. Bloque des fois sinon:
        numAlig = anoAlig.ensembles[(joueur == 1 ? 7 : 3)].next((byte) -1);
        if (numAlig != -1) {
            res = StatiqueAlig.trovLastAno(anoAlig, numAlig);
            if (niveau >= 20) return res;
            else {
                rand = (int) (Math.random() * 20);
                if (niveau > rand) {
                    return res;
                }
            }
        }

        if (niveau < 90) {
            // Cas où on ne va même pas regarder les fourchettes:
            if (niveau <= 20) {
                if (niveau < 10) {
                    return choixAnneauRandom();
                } else {
                    return choixAnneauPoints(joueur);
                }
            }

            // L'ordinateur teste les fourchettes avancées:
            byte ordreF = -1;
            if (20 < niveau && niveau < 30) {
                rand = (int) (20 + Math.random() * 10);
                if (niveau > rand) {
                    ordreF = 0;
                }
            } else if (30 <= niveau && niveau <= 35) {
                ordreF = 0;
            } else if (35 < niveau && niveau < 50) {
                rand = (int) (35 + Math.random() * 15);
                if (niveau > rand) {
                    ordreF = 1;
                } else {
                    ordreF = 0;
                }
            }
            if (40 <= niveau && niveau < 60) {
                rand = (int) (40 + Math.random() * 20);
                if (niveau > rand) {
                    ordreF = 2;
                } else if (50 <= niveau) {
                    ordreF = 1;
                }
            }
            if (60 <= niveau && niveau < 80) {
                rand = (int) (60 + Math.random() * 20);
                if (niveau > rand) {
                    ordreF = 3;
                } else {
                    ordreF = 2;
                }
            }
            if (70 <= niveau && niveau < 90) {
                rand = (int) (70 + Math.random() * 20);
                if (niveau > rand) {
                    ordreF = 4;
                } else if (80 <= niveau) {
                    ordreF = 3;
                }
            }
            if (ordreF == 0) {
                res = isFourchetteSimple(joueur);
                if (res != -1) {
                    return res;
                }
            } else if (ordreF > 0) {
                byte[] tab = fourchetteAvancee(joueur, ordreF);
                if (tab != null) {
                    return tab[0];
                }
            }

            // On essaie de bloquer les fourchette adversaires:
            ordreF = -1;
            if (30 < niveau && niveau < 50) {
                rand = (int) (30 + Math.random() * 20);
                if (niveau > rand) {
                    ordreF = 0;
                }
            } else if (50 <= niveau && niveau < 80) {
                rand = (int) (50 + Math.random() * 30);
                if (niveau > rand) {
                    ordreF = 1;
                } else {
                    ordreF = 0;
                }
            }
            if (60 < niveau && niveau < 90) {
                rand = (int) (60 + Math.random() * 30);
                if (niveau > rand) {
                    ordreF = 2;
                } else if (80 <= niveau) {
                    ordreF = 1;
                }
            }
            if (ordreF >= 0) {
                res = bloqueFourchetteAvanceeSimplifiee(joueur, ordreF, handler);
                if (res != -1) {
                    return res;
                }
            }
            // S'il n'y a rien, on renvoit le "meilleur anneau":
            return choixAnneauPoints(joueur);


        } else {
            return jouerNiveauSuperFort(joueur, niveau, handler);

        }
    }

    private byte jouerNiveauSuperFort(byte joueur, int niveau, Handler handler) {
        //Si l'ordi peut gagner, il gagne:
        byte numAlig = anoAlig.ensembles[(joueur == 1 ? 3 : 7)].next((byte) -1);
        if (numAlig != -1) {
            return StatiqueAlig.trovLastAno(anoAlig, numAlig);
        }

        // Si l'adversaire peut gagner, l'ordi bloque:
        numAlig = anoAlig.ensembles[(joueur == 1 ? 7 : 3)].next((byte) -1);
        if (numAlig != -1) {
            return StatiqueAlig.trovLastAno(anoAlig, numAlig);
        }

        // Calcul de fourchette:
        byte ordre = 4;
        int random = (int) (89 + Math.random() * 4);
        if (niveau > random) {
            ordre = 5;
        }
        random = (int) (92 + Math.random() * 4);
        if (niveau > random) {
            ordre = 6;
        }

        random = (int) (96 + Math.random() * 4);
        if (niveau > random) {
            ordre = 7;
        }

        if (niveau == 100) {
            ordre = 8;
        }
        byte res = bigFourchetteAvancee(joueur, ordre, handler);
        if (res != -1) {
            return res;
        }

        // Calcul de blocage de fourchette:
        ordre = 2;
        random = (int) (90 + Math.random() * 5);
        if (niveau > random) {
            ordre = 3;
        }

        random = (int) (90 + Math.random() * 10);
        if (niveau > random) {
            ordre = 4;
        }

        res = bloqueFourchetteAvanceeSimplifiee(joueur, ordre, handler);
        if (res != -1) {
            return res;
        }
        // S'il n'y a rien, on renvoit le "meilleur anneau":
        return choixAnneauPoints(joueur);
    }


    /**
     * C'est la fonction qui doit être appellée pour determiner quelle anneau jouer dans le
     * cas où on aurait une grosse fourchette avancée. Elle renvoit alors le numero de l'anneau
     * correspondant à la fourchette d'ordre le plus faible possible.
     *
     * @param ja      : Le joueur qui fait la fourchette
     * @param ordre   : l'ordre maximale de calcule de la fourchette.
     * @param handler : Le handler qui sera utilisé pour indiquer des resultats intermédiaires
     *                d'avancement.
     * @return : -1 si aucune fourchette n'a été trouvé. Le numero de l'anneau sinon.
     */
    public byte bigFourchetteAvancee(byte ja, byte ordre, Handler handler) {
        if (anoAlig.getNbAnneauxJoues() > 61) {
            return -1;
        }
        // Si l'ordre risque de faire déborder le nombre de cases du plateau
        // ie n + 2*ordre + 3 > 64:
        if (ordre * 2 + 3 + anoAlig.getNbAnneauxJoues() > 64) {
            // On ajuste l'ordre pour que ça rentre:
            return bigFourchetteAvancee(ja, (byte) ((64 - 3 - anoAlig.getNbAnneauxJoues()) / 2), handler);
        }

        // On commence par regarder s'il y a une fourchette simple:
        byte resFourchetteSimple = isFourchetteSimple(ja);
        if (resFourchetteSimple != -1) {
            return resFourchetteSimple;
        }

        // Si l'ordre est égal à 0: on ne va pas plus loin:
        if (ordre == 0) {
            return -1;
        }
        int[] tab = listesAnneauxFourchette(ja);
        byte anneau1, anneau2, res;
        byte meilleurAnneau = -1;
        int indice;
        int size = tab.length;
        byte jb = (byte) (3 - ja);
        for (int i = 0; i < size; i++) {
            anneau1 = (byte) (tab[i] % (1 << 6));
            anneau2 = (byte) (tab[i] / (1 << 6) % (1 << 6));
            indice = tab[i] / (1 << 12);

            anoAlig.jouer(anneau1, ja);
            anoAlig.jouer(anneau2, jb);
            res = isFourchetteAvancee(ja, 1, ordre - 1, getDejaJoue(indice));
            anoAlig.dejouer(anneau2, jb);
            anoAlig.dejouer(anneau1, ja);
            if (res != -1) {
                ordre = res;
                meilleurAnneau = anneau1;
                if (res == 0) {
                    break;
                }
            }
            if (handler != null) { // Plus pratique pour les test unitaires: handler null.
                handler.obtainMessage(StaticFields.LABEL_INFO_FORK, i, size).sendToTarget();
            }
        }
        for (int i = 0; i < 6; i++) {
            memoire[i].clear();
        }
        return meilleurAnneau;
    }

    /**
     * Cette fonction ne doit pas être appelé directement.
     * Elle permet de determiner s'il y a une fourchette avancée, en utilisant de la mémorisation.
     *
     * @param ja           : le joueur faisant la fourchette
     * @param profondeur   : où on en est dans les appels recursifs...
     * @param ordreRestant : L'ordre auquel on calcule la fourchette
     * @param dejaJoue     : Un long représentant les paires d'anneaux déjà joués.
     * @return -1 s'il n'y a pas de fourchette. L'ordre de la fourchette sinon.
     */
    private byte isFourchetteAvancee(byte ja, int profondeur, int ordreRestant, long dejaJoue) {
        // On commence par regarder s'il y a une fourchette simple:

        byte resFourchetteSimple = isFourchetteSimple(ja);
        if (resFourchetteSimple != -1) {
            return 0;
        }
        // Si l'ordre est égal à 0: on ne va pas plus loin:
        if (ordreRestant == 0) {
            return -1;
        }
        int[] tab = listesAnneauxFourchette(ja);
        byte anneau1, anneau2, res;
        byte resultatFinale = -1;
        long indiceComplet = 0;
        Byte resMemoire;
        byte jb = (byte) (3 - ja);

        for (int aTab : tab) {
            anneau1 = (byte) (aTab % (1 << 6));
            anneau2 = (byte) (aTab / (1 << 6) % (1 << 6));
            if (profondeur - 1 < 6) {
                indiceComplet = getDejaJoue(dejaJoue, aTab / (1 << 12));
                resMemoire = memoire[profondeur - 1].get(indiceComplet);
            } else {
                resMemoire = null;
            }
            if (resMemoire != null) {
                res = resMemoire;
            } else {
                anoAlig.jouer(anneau1, ja);
                anoAlig.jouer(anneau2, jb);
                res = isFourchetteAvancee(ja, profondeur + 1, ordreRestant - 1, indiceComplet);
                anoAlig.dejouer(anneau2, jb);
                anoAlig.dejouer(anneau1, ja);
                if (profondeur - 1 < 6) {
                    memoire[profondeur - 1].put(indiceComplet, res);
                }
            }
            if (res != -1) {
                if (res == 0) {
                    return 1;
                } else if (res < ordreRestant) {
                    ordreRestant = res;
                    resultatFinale = (byte) (res + 1);
                }
            }
        }
        return resultatFinale;
    }

    /**
     * Cette méthode permet de récuperer le long représentant les paires d'anneaux déjà
     * joué à partir de l'ancienne valeure et de la paire d'anneau qu'on lui ajoute.
     *
     * @param dejaJoue : le long de départ
     * @param indice   : la paire d'anneau qu'on lui ajoute
     * @return la nouvelle série de paire d'anneaux.
     * <p/>
     * Tout est représenté de cette façon là:
     * uttt 66666 66666 55555 55555 44444 44444 33333 33333 22222 22222 11111 1111
     * où u est non assigné,
     * t est la taille, c'est à dire le nombre de paire,
     * iiiii iiiii est la ième paire (dans l'ordre croissant).
     */
    public static long getDejaJoue(long dejaJoue, int indice) {
        int ancienneTaille = (int) (dejaJoue / (1L << 60L));
        long resultat = (ancienneTaille + 1) * (1L << 60L);
        long tab[] = new long[ancienneTaille + 1];
        tab[0] = indice;
        for (int i = 0; i < ancienneTaille; i++) {
            tab[i + 1] = dejaJoue / (1L << (10L * ((long) i))) % (1L << 10L);
            if (indice > tab[i + 1]) {
                tab[i] = tab[i + 1];
                tab[i + 1] = indice;
            }
        }
        for (int i = 0; i < ancienneTaille + 1; i++) {
            long inter = (1L << (10L * ((long) i)));
            resultat += tab[i] * inter;
        }
        return resultat;
    }

    /**
     * Cette méthode permet de réaliser l'initilaisation d'un long représentant
     * les paires d'anneaux déjà joués.
     *
     * @param indice : La paire d'anneau
     * @return Un long représentant les paires d'anneaux déjà joués.
     */
    public static long getDejaJoue(int indice) {
        return (1L << 60L) + indice;
    }


    public byte playChallenge(int levelFork, int usedRings) {
        //Si l'ordi peut gagner, il gagne:
        byte numAlig = anoAlig.ensembles[7].next((byte) -1);
        if (numAlig != -1) {
            return StatiqueAlig.trovLastAno(anoAlig, numAlig);
        }

        // Si l'adversaire peut gagner, l'ordi bloque:
        numAlig = anoAlig.ensembles[3].next((byte) -1);
        if (numAlig != -1) {
            return StatiqueAlig.trovLastAno(anoAlig, numAlig);
        }

        int remainingRings = levelFork + 2 - usedRings; // Remaining rings for user.
        if (remainingRings < 1) {
            throw new RuntimeException("Problem remaining rings: " + remainingRings + "(levelFork: " + levelFork + ", usedRings: " + usedRings);
        } else if (remainingRings == 1) {
            return choixAnneauPoints((byte) 2);
        } else {
            byte ring = bigFourchetteAvancee((byte) 2, (byte) (remainingRings - 2), null);
            if (ring != -1) return ring;
            if (remainingRings - 2 <= 4) {
                ring = bloqueFourchetteAvanceeSimplifiee((byte) 1, (remainingRings - 2), null);
                if (ring != -1) return ring;
            }
            byte[] reglages = new byte[]{5, 3, 5, 40, 40, 5, 15, 40, 40, 0}; // Les 40 ne seront pas utlisés...
            return choixAnneauPoints(reglages);
        }
    }

    public void playStar(int levelFork, int numberStarRings, StarUsedBean bean) {
        byte ringUser = bigFourchetteAvancee((byte) 1, (byte) (levelFork - numberStarRings), null);
        if (ringUser == -1)
            throw new RuntimeException("A result should have been find to the fork.");
        bean.setRingUser(ringUser);
        anoAlig.jouer(ringUser, (byte) 1);
        byte ringComputer = playChallenge(levelFork, numberStarRings + 1);
        anoAlig.jouer(ringComputer, (byte) 2);
        bean.setRingComputer(ringComputer);
    }
}