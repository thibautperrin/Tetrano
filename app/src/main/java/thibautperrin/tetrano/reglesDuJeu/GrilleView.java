package thibautperrin.tetrano.reglesDuJeu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import thibautperrin.tetrano.R;
import thibautperrin.tetrano.packageSurfaceGraphView.Dessin;

/**
 * Created by Thibaut PERRIN on 01/02/2015.
 */
public class GrilleView extends View {
    protected byte[] anneauxJouees; // Ensemble des anneaux présent sur le plateau
    private int cote;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private RectF rectangle = new RectF();

    public GrilleView(Context context) {
        super(context);
    }

    public GrilleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void precise(byte[] anneaux) {
        anneauxJouees = anneaux;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        cote = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(cote, cote);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Dessin.dessineGrille(canvas, paint, cote, ContextCompat.getColor(getContext(),R.color.noir));
        int couleur = ContextCompat.getColor(getContext(), R.color.bleu);
        for (byte anneau : anneauxJouees) {
            Dessin.dessineAnneauNumero(canvas, paint, cote, rectangle, couleur, anneau);
        }
    }
}
