package com.numericcal.androidsensors;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.Log;

import com.numericcal.edge.Dnn;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.functions.Function;

import static java.lang.Float.compare;

/**
 * Processing required to prepare inputs and interpret outputs from TinyYOLOv2.
 */
public class FaceDet {

    private static final String TAG = "AS.faceDet";
    private static float box_threshold = 0.7f;
    public static class ModelParams {
        public int max_num_boxes;



        public List<String> labels;

        ModelParams(Dnn.Handle hdl) throws JSONException, IOException {

            JSONObject json = hdl.info.params;

            Log.wtf(TAG, json.toString());

            this.max_num_boxes = json.getInt("max_num_boxes");
        }
    }



    /**
     * A class to represent a single bounding box, it's class and confidence of the detection.
     */
    public static class BBox {
        float bottom;
        float top;
        float left;
        float right;

        float confidence;

        BBox(float bottom, float top, float left, float right, float confidence) {
            this.bottom = bottom;
            this.top = top;
            this.left = left;
            this.right = right;
            this.confidence = confidence;
        }

        @Override
        /* we match the python code for debug simplicity */
        public String toString() {
            return "[(" + left + ", " + top + ", " + right + ", " + bottom + ") |  | " + confidence + "]";
        }
    }



    /**
     * Preprocessing for YOLOv2. Normalizes image data to [0.0, 1.0] range and aligns it in memory
     * in NHWC.BGR order.
     * @return float[] representation of the input image
     */
    public static Function<Bitmap, float[]> v2Normalize(int mean, float std) {
        return Utils.bmpToFloat_HWC_BGR(mean, std);
    }

    /**
     * return a function to create bounding boxes and scores from the returned tensor
     * @param maxNumFaces - classes detected per box
     * @return
     */
    public static Function<float[], List<BBox>> makeBoundingBoxesScores(int maxNumFaces,int height, int width) {
        return tensor -> {

            int scoreBegin = maxNumFaces*4;

            List<BBox> lst = new ArrayList<>();
            for(int boxInd = 0; boxInd<maxNumFaces; boxInd++)
            {
                float curScore = tensor[scoreBegin+boxInd];
                //
                if(curScore > box_threshold)
                {
                    Log.d(TAG,"ind:"+ boxInd+"score:"+curScore+";");
                    float ymin = tensor[boxInd*4]*height;
                    float xmin = tensor[boxInd*4+1]*width;
                    float ymax = tensor[boxInd*4+2]*height;
                    float xmax = tensor[boxInd*4+3]*width;
                    lst.add(new BBox(ymax,ymin, xmin, xmax,curScore));
                }
            }
            return lst;
        };
    }





    /**
     * Draw boxes on a bitmap to be overlaid on top of the camera frame stream.
     * @return
     */
    public static Function<List<BBox>, Bitmap> drawBBoxes(int w, int h, float scaleW, float scaleH) {
        return bBoxes -> {
            Bitmap boxBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas boxCanvas = new Canvas(boxBmp);

            boxCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            for (BBox bbox: bBoxes) {


                Overlay.drawBox(expandBBoxBy(bbox, w, h, scaleW, scaleH),
                        new Overlay.LineStyle(Color.GREEN, 2.0f), boxCanvas);
                //Log.wtf(TAG, bbox.toString());
            }

            return boxBmp;
        };
    }

    /**
     * Scale the BBox assuming assuming simple central projection.
     * @param b - the box to scale
     * @param stretchWidth - stretch of the x axis
     * @param stretchHeight - stretch of the y axis
     * @return
     */
    public static BBox expandBBoxBy(BBox b, float stretchWidth, float stretchHeight, float scaleW, float scaleH) {
        return new BBox(
                Math.round(Math.min(stretchHeight,  b.bottom +20)*scaleH),
                Math.round(Math.max(0, b.top -20)*scaleH),
                Math.round(Math.max(0, b.left -20)*scaleW),
                Math.round(Math.min(stretchWidth,  b.right +20)*scaleW),
                b.confidence);
    }
}
