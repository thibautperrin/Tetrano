package thibautperrin.tetrano.packageSurfaceGraphView;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import thibautperrin.tetrano.StaticFields;

/**
 * Cette classe possède des méthodes statiques permettant de réaliser les opérations de
 * dessins sans redondance de code, et sans surchargé les classes qui les utilisent.
 * Created by Thibaut PERRIN on 03/06/2015.
 */
public class Dessin {

    public static void dessineGrille(Canvas canvas, Paint paint, int cote, int couleur) {
        float epaisseurCadre = ((float) cote) * StaticFields.thicknessBorderCoeff;
        float epaisseurTrait = ((float) cote) * StaticFields.thicknessSeparationLinesCoeff;
        float distanceTrait = ((float) cote) * StaticFields.distanceLinesCoeff;

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(couleur);
        paint.setStrokeWidth(epaisseurCadre);

        // DESSIN DU CADRE:
        canvas.drawLine(epaisseurCadre / 2, 0, epaisseurCadre / 2, cote, paint);
        canvas.drawLine(cote - epaisseurCadre / 2, 0, cote - epaisseurCadre / 2, cote, paint);
        canvas.drawLine(0, epaisseurCadre / 2, cote, epaisseurCadre / 2, paint);
        canvas.drawLine(0, cote - epaisseurCadre / 2, cote, cote - epaisseurCadre / 2, paint);

        // Dessin des lignes separatrices des cases:
        paint.setStrokeWidth(epaisseurTrait);
        canvas.drawLine(epaisseurCadre + distanceTrait + epaisseurTrait / 2, 0 + epaisseurTrait / 2,
                epaisseurCadre + distanceTrait + epaisseurTrait / 2, cote - epaisseurTrait / 2, paint);
        canvas.drawLine(epaisseurCadre + 2 * distanceTrait + epaisseurTrait * 3 / 2, 0 + epaisseurTrait / 2,
                epaisseurCadre + 2 * distanceTrait + epaisseurTrait * 3 / 2, cote - epaisseurTrait / 2, paint);
        canvas.drawLine(epaisseurCadre + 3 * distanceTrait + epaisseurTrait * 5 / 2, 0 + epaisseurTrait / 2,
                epaisseurCadre + 3 * distanceTrait + epaisseurTrait * 5 / 2, cote - epaisseurTrait / 2, paint);

        canvas.drawLine(0 + epaisseurTrait / 2, epaisseurCadre + distanceTrait + epaisseurTrait / 2,
                cote - epaisseurTrait / 2, epaisseurCadre + distanceTrait + epaisseurTrait / 2, paint);
        canvas.drawLine(0 + epaisseurTrait / 2, epaisseurCadre + 2 * distanceTrait + epaisseurTrait * 3 / 2,
                cote - epaisseurTrait / 2, epaisseurCadre + 2 * distanceTrait + epaisseurTrait * 3 / 2, paint);
        canvas.drawLine(0 + epaisseurTrait / 2, epaisseurCadre + 3 * distanceTrait + epaisseurTrait * 5 / 2,
                cote - epaisseurTrait / 2, epaisseurCadre + 3 * distanceTrait + epaisseurTrait * 5 / 2, paint);
    }

    public static void dessineAnneau(Canvas canvas, Paint paint, int cote, RectF rectangle,
                                     int couleur, float posX, float posY, int tailleAnneau) {
        dessineArcs(canvas, paint, cote, rectangle, couleur, posX, posY, tailleAnneau, 360);
    }

    public static void dessineArcs(Canvas canvas, Paint paint, int cote, RectF rectangle,
                                   int couleur, float posX, float posY, int tailleAnneau, float angle) {
        float longueurRayon0 = ((float) cote) * StaticFields.shortestRadiusCoeff;
        float diffLongueur = ((float) cote) * StaticFields.diffRadiusCoeff;
        float epaisseurAnneau = ((float) cote) * StaticFields.thicknessRingsCoeff;
        paint.setColor(couleur);
        paint.setStrokeWidth(epaisseurAnneau);
        float rayon = longueurRayon0 + diffLongueur * tailleAnneau;
        rectangle.set(posX - rayon, posY - rayon, posX + rayon, posY + rayon);
        if (angle == 360) {
            canvas.drawOval(rectangle, paint);
        } else {
            canvas.drawArc(rectangle, 0, angle, false, paint);
        }

    }

    public static void dessineAnneauPlace(Canvas canvas, Paint paint, int cote, RectF rectangle,
                                          int couleur, int coorX, int coorY, int tailleAnneau) {
        float distanceAnneau = ((float) cote) * StaticFields.distanceRingsCentersCoeff;
        float pos0 = ((float) cote) * StaticFields.pos0Coeff;

        float posX = pos0 + distanceAnneau * coorX;
        float posY = pos0 + distanceAnneau * coorY;
        dessineAnneau(canvas, paint, cote, rectangle, couleur, posX, posY, tailleAnneau);
    }

    public static void dessineAnneauNumero(Canvas canvas, Paint paint, int cote, RectF rectangle,
                                           int couleur, int numeroAnneau) {
        int coorX = numeroAnneau % 4;
        int coorY = (numeroAnneau / 4) % 4;
        int coorZ = (numeroAnneau / 16) % 4;
        dessineAnneauPlace(canvas, paint, cote, rectangle, couleur, coorX, coorY, coorZ);
    }

    public static void dessineAnneauASaisir(Canvas canvas, Paint paint, int cote, RectF rectangle,
                                            int couleur, boolean orientationVerticale, float angle) {
        float distanceAnneau = ((float) cote) * StaticFields.distanceRingsCentersCoeff;
        float pos0 = ((float) cote) * StaticFields.pos0Coeff;

        for (int i = 0; i < 4; i++) {
            float posX = pos0 + distanceAnneau * (orientationVerticale ? i : 4);
            float posY = pos0 + distanceAnneau * (orientationVerticale ? 4 : i);
            dessineArcs(canvas, paint, cote, rectangle, couleur, posX, posY, i, angle);
        }
    }

    public static void dessineAnneauASaisir(Canvas canvas, Paint paint, int cote, RectF rectangle,
                                            int couleur, boolean orientationVerticale) {
        dessineAnneauASaisir(canvas, paint, cote, rectangle, couleur, orientationVerticale, 360);
    }

    /**
     * Cette methode static permet de dessiner un anneau en deplacement entre la zone de selection,
     * et la zone d'arrivee. Ce deplacement est represente par un avancement entre 0 et 1.
     *
     * @param canvas               Le canvas sur lequel on dessine
     * @param paint                le pinceau utilisé pour dessiner
     * @param cote                 La longueur du cote de la grille
     * @param rectangle            Le rectangle que l'on ne recrée pas à chaque fois.
     * @param couleur              : La couleur de l'anneau
     * @param orientationVerticale false si on est en landscape, true si on est en portrait.
     * @param numeroAnneau         Le numero de l'anneau (entre 0 et 63)
     * @param avancement           : float entre 0 et 1. en 1: l'anneau est dans la case.
     */
    public static void dessineTrajetAnneau(Canvas canvas, Paint paint, int cote, RectF rectangle,
                                           int couleur, boolean orientationVerticale,
                                           int numeroAnneau, float avancement) {
        float distanceAnneau = ((float) cote) * StaticFields.distanceRingsCentersCoeff;
        float pos0 = ((float) cote) * StaticFields.pos0Coeff;
        int coorX = numeroAnneau % 4;
        int coorY = (numeroAnneau / 4) % 4;
        int coorZ = (numeroAnneau / 16) % 4;
        float posX0 = pos0 + distanceAnneau * (orientationVerticale ? coorZ : 4);
        float posY0 = pos0 + distanceAnneau * (orientationVerticale ? 4 : coorZ);
        float posX1 = pos0 + distanceAnneau * coorX;
        float posY1 = pos0 + distanceAnneau * coorY;
        float posX = posX0 * (1 - avancement) + posX1 * avancement;
        float posY = posY0 * (1 - avancement) + posY1 * avancement;
        dessineAnneau(canvas, paint, cote, rectangle, couleur, posX, posY, coorZ);
    }
}