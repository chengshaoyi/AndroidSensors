package com.numericcal.androidsensors;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.widget.ImageView;

public class Overlay {

    public static class Line {
        int color;
        float width;
        Line(int color, float width) {
            this.color = color;
            this.width = width;
        }
    }

    public static void drawBox(Yolo.BBox box, Line line, Canvas canvas) {
        Paint paint = new Paint();

        paint.setColor(line.color);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(line.width);

        canvas.drawLine(0, 0, canvas.getWidth(), canvas.getHeight(), paint);

    }
}
