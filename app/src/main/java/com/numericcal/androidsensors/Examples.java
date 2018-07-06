package com.numericcal.androidsensors;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.Log;
import android.widget.ImageView;

import io.fotoapparat.preview.Frame;
import io.reactivex.ObservableTransformer;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

import com.numericcal.androidsensors.Tags.TTok;

import java.util.List;

import static com.numericcal.androidsensors.Tags.combine;
import static com.numericcal.androidsensors.Tags.extract;

/**
 * Pre-configured ObservableTransformers for convenience.
 */
public class Examples {

    public static class YoloV2 {
        private static final String TAG = "Ex.YoloV2";

        public static ObservableTransformer<TTok<Frame>, TTok<Bitmap>>
        yuv2bmpTT() {
            return Utils.mkOT(Utils.yuv2bmp(), extract(), combine("yuv2bmp"));
        }

        public static ObservableTransformer<TTok<Bitmap>, TTok<Bitmap>>
        rotateTT(float angle) {
            return Utils.mkOT(Utils.bmpRotate(angle), extract(), combine("rotate"));
        }

        public static ObservableTransformer<TTok<Bitmap>, TTok<Bitmap>>
        scaleTT(int width, int height) {
            return Utils.mkOT(Camera.scaleTo(width, height), extract(), combine("scaling"));
        }

        public static <F,S,T> ObservableTransformer<TTok<F>,TTok<T>>
        valueGrabTT(Utils.Agent<F,S,T> actorGrabber) {
            return Utils.mkOT(actorGrabber, extract(), combine("grabber"));
        }

        public static ObservableTransformer<TTok<Bitmap>, TTok<float[]>>
        yoloV2Normalize() {
            return Utils.mkOT(Yolo.v2Normalize(), extract(), combine("normalize"));
        }

        public static ObservableTransformer<TTok<float[]>, TTok<List<Yolo.CellBoxes>>>
        splitCellsTT(int S, int B, int C) {
            return Utils.mkOT(Yolo.splitCells(S, B, C), extract(), combine("cellsplit"));
        }

        public static ObservableTransformer<TTok<List<Yolo.CellBoxes>>, TTok<List<Yolo.BBox>>>
        thresholdBoxesTT(float threshold, Yolo.ModelParams mp,
                    float scaleX, float scaleY) {
            return Utils.mkOT(Yolo.thresholdAndBox(threshold, mp, scaleX, scaleY), extract(), combine("threshold"));
        }

        public static ObservableTransformer<TTok<List<Yolo.BBox>>, TTok<List<Yolo.BBox>>>
        suppressNonMax(float threshold) {
            return Utils.mkOT(Yolo.suppressNonMax(threshold), extract(), combine("suppress"));
        }

        public static ObservableTransformer<TTok<List<Yolo.BBox>>, TTok<Bitmap>>
        drawBBoxes(int w, int h, float scaleW, float scaleH) {
            return Utils.mkOT(Yolo.drawBBoxes(w, h, scaleW, scaleH), extract(), combine("bboxes"));
        }

    }

}
