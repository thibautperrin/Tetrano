package thibautperrin.tetrano;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Cette classe vise à éliminer les taches au format png, qui prennent
 * innutilement beaucoup de place.
 * Created by Thibaut PERRIN on 18/06/2015.
 */
public class TacheView extends ImageView {

    private int cote;
    private Paint paint;
    private Path path;
    private static float[] tabPointsX;
    private static float[] tabPointsY;

    public TacheView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.TacheView,
                0, 0);
        int color;
        try {
            color = a.getColor(R.styleable.TacheView_couleurTache, Color.BLACK);
        } finally {
            a.recycle();
        }
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        path = new Path();
        remplirTabPoints();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        this.cote = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(this.cote, this.cote);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        path.reset();
        int length = tabPointsX.length;
        path.moveTo(tabPointsX[length - 1] * cote, tabPointsY[length - 1] * cote);
        for (int i = 0; i < length; i++) {
            path.lineTo(tabPointsX[i] * cote, tabPointsY[i] * cote);
        }
        canvas.drawPath(path, paint);
        super.onDraw(canvas);
    }

    public int getColor() {
        return paint.getColor();
    }

    private void remplirTabPoints() {
        tabPointsX = new float[29];
        tabPointsX[0] = 0.255859375f;
        tabPointsX[1] = 0.28125f;
        tabPointsX[2] = 0.30859375f;
        tabPointsX[3] = 0.40234375f;
        tabPointsX[4] = 0.60546875f;
        tabPointsX[5] = 0.66796875f;
        tabPointsX[6] = 0.853515625f;
        tabPointsX[7] = 0.89453125f;
        tabPointsX[8] = 0.927734375f;
        tabPointsX[9] = 0.93359375f;
        tabPointsX[10] = 0.93359375f;
        tabPointsX[11] = 0.8515625f;
        tabPointsX[12] = 0.8359375f;
        tabPointsX[13] = 0.83984375f;
        tabPointsX[14] = 0.935546875f;
        tabPointsX[15] = 0.927734375f;
        tabPointsX[16] = 0.875f;
        tabPointsX[17] = 0.693359375f;
        tabPointsX[18] = 0.50390625f;
        tabPointsX[19] = 0.19140625f;
        tabPointsX[20] = 0.14453125f;
        tabPointsX[21] = 0.140625f;
        tabPointsX[22] = 0.068359375f;
        tabPointsX[23] = 0.080078125f;
        tabPointsX[24] = 0.140625f;
        tabPointsX[25] = 0.146484375f;
        tabPointsX[26] = 0.064453125f;
        tabPointsX[27] = 0.0703125f;
        tabPointsX[28] = 0.2421875f;

        tabPointsY = new float[29];
        tabPointsY[0] = 0.0625f;
        tabPointsY[1] = 0.060546875f;
        tabPointsY[2] = 0.083984375f;
        tabPointsY[3] = 0.08203125f;
        tabPointsY[4] = 0.138671875f;
        tabPointsY[5] = 0.13671875f;
        tabPointsY[6] = 0.205078125f;
        tabPointsY[7] = 0.23828125f;
        tabPointsY[8] = 0.251953125f;
        tabPointsY[9] = 0.279296875f;
        tabPointsY[10] = 0.3671875f;
        tabPointsY[11] = 0.474609375f;
        tabPointsY[12] = 0.544921875f;
        tabPointsY[13] = 0.5859375f;
        tabPointsY[14] = 0.64453125f;
        tabPointsY[15] = 0.693359375f;
        tabPointsY[16] = 0.78125f;
        tabPointsY[17] = 0.884765625f;
        tabPointsY[18] = 0.88671875f;
        tabPointsY[19] = 0.7734375f;
        tabPointsY[20] = 0.716796875f;
        tabPointsY[21] = 0.61328125f;
        tabPointsY[22] = 0.5703125f;
        tabPointsY[23] = 0.48046875f;
        tabPointsY[24] = 0.42578125f;
        tabPointsY[25] = 0.373046875f;
        tabPointsY[26] = 0.330078125f;
        tabPointsY[27] = 0.24609375f;
        tabPointsY[28] = 0.091796875f;
    }
}
