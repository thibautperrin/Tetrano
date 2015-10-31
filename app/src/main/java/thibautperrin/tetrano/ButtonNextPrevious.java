package thibautperrin.tetrano;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;


/**
 * Created by Thibaut PERRIN on 15/08/2015.
 * This class aims to replace the PNG used to display a Next or previous button.
 * This new button has 4 XML attributs:
 * next (boolean): If true, it is a Next button, else it is a Previous button.
 * fillWidth (boolean), fillHeight(boolean) and ratioWidthOnHeight(float):
 * if (fillWidth and not fillHeight): We fill completly the available width and the height is
 * defined (wathever the space available) as Width / ratioWidthOnHeight.
 * if (fillHeight and not fillWidth): We fill completly the available Height and the Width is
 * defined (wathever the space available) as Height * ratioWidthOnHeight.
 * if (fillHeight and fillWidth): we fill completely the height and the width, without
 * paying attention to ratioWidthOnHeight.
 * if (not fillHeight and not fillWidth): we fill the maximum of the available space,
 * following the ratioWidthOnHeight.
 */
public class ButtonNextPrevious extends View {
    private final Paint paint;
    private Path path;
    private final float ratioWidthOnHeight;
    private int width;
    private int height;
    private final float[] tabPointsX;
    private final float[] tabPointsY = {0.9f, 0.9f, 0.5f, 0.1f, 0.1f, 0.5f};


    public ButtonNextPrevious(Context context, AttributeSet attrs) {
        super(context, attrs);
        boolean isNext;
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ButtonPreviousNext,
                0, 0);
        try {
            isNext = a.getBoolean(R.styleable.ButtonPreviousNext_next, false);
            ratioWidthOnHeight = a.getFloat(R.styleable.ButtonPreviousNext_ratioWidthOnHeight, 1);
        } finally {
            a.recycle();
        }
        if (isNext) {
            tabPointsX = new float[]{0.1f, 0.5f, 0.9f, 0.5f, 0.1f, 0.35f};
        } else {
            tabPointsX = new float[]{0.9f, 0.5f, 0.1f, 0.5f, 0.9f, 0.65f};
        }
        paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setColor(0xff000000);
        path = new Path();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        width = MeasureSpec.getSize(widthMeasureSpec);
        height = MeasureSpec.getSize(heightMeasureSpec);
        if (width == 0) {
            width = (int) (height * ratioWidthOnHeight);
        } else if (height == 0) {
            height = (int) (width / ratioWidthOnHeight);
        } else if (height * ratioWidthOnHeight < width) {
            width = (int) (height * ratioWidthOnHeight);
        } else {
            height = (int) (width / ratioWidthOnHeight);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        path.reset();
        int length = tabPointsX.length;
        path.moveTo(tabPointsX[length - 1] * width, tabPointsY[length - 1] * height);
        for (int i = 0; i < length; i++) {
            path.lineTo(tabPointsX[i] * width, tabPointsY[i] * height);
        }
        canvas.drawPath(path, paint);
        super.onDraw(canvas);
    }

}
