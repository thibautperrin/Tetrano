package thibautperrin.tetrano;

import android.content.Context;
import android.content.res.TypedArray;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

/**
 * Cette classe permet de représenter des boutons ayant un fond jaune partiellement transparent,
 * une image et un texte. C'est utile dans la fenêtre activity_jouer où l'utilisateur choisit
 * quel mode de jeu il va utiliser.
 * Created by Thibaut PERRIN on 22/06/2015.
 */
public class BoutonsJouer extends View {
    private Drawable drawable;
    private String texte;
    private int height;
    private Paint paint;


    public BoutonsJouer(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.BoutonsJouer,
                0, 0);


        try {
            drawable = a.getDrawable(R.styleable.BoutonsJouer_drawable);
            texte = a.getString(R.styleable.BoutonsJouer_texte);
        } finally {
            a.recycle();
        }
        paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        this.height = width/2;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xdbffff32);
        drawable.setBounds(0, 0, height * 2, height);
        drawable.draw(canvas);
        if (texte != null) {
            paint.setColor(Color.BLACK);
            setTextSizeForWidth(paint, height * 1.2f, texte);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(texte, height * 1.0f, height * 0.35f, paint);
        }
        super.onDraw(canvas);
    }


    /**
     * Sets the text size for a Paint object so a given string of text will be a
     * given width.
     *
     * @param paint
     *            the Paint to set the text size for
     * @param desiredWidth
     *            the desired width
     * @param text
     *            the text that should be that width
     */
    private static void setTextSizeForWidth(Paint paint, float desiredWidth,
                                            String text) {

        // Pick a reasonably large value for the test. Larger values produce
        // more accurate results, but may cause problems with hardware
        // acceleration. But there are workarounds for that, too; refer to
        // http://stackoverflow.com/questions/6253528/font-size-too-large-to-fit-in-cache
        final float testTextSize = 70f;

        // Get the bounds of the text, using our testTextSize.
        paint.setTextSize(testTextSize);
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);

        // Calculate the desired size as a proportion of our testTextSize.
        float desiredTextSize = testTextSize * desiredWidth / bounds.width();

        // Set the paint for that size.
        paint.setTextSize(desiredTextSize);
    }
}
