package com.numericcal.androidsensors;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Convenience functions for working with ImageView overlays.
 */
public class Overlay {

    public static class LineStyle {
        int color;
        float width;
        LineStyle(int color, float width) {
            this.color = color;
            this.width = width;
        }
    }

    public static void drawBox(Yolo.BBox box, LineStyle line, Canvas canvas) {
        Paint paint = new Paint();

        paint.setColor(line.color);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(line.width);

        canvas.drawLine(box.left, box.bottom, box.left, box.top, paint);
        canvas.drawLine(box.left, box.bottom, box.right, box.bottom, paint);
        canvas.drawLine(box.right, box.top, box.right, box.bottom, paint);
        canvas.drawLine(box.right, box.top, box.left, box.top, paint);

        paint.setTextSize(30.0f);
        canvas.drawText(box.label, box.left, box.top, paint);

    }
}
