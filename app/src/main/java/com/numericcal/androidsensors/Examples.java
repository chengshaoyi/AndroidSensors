package com.numericcal.androidsensors;

import android.graphics.Bitmap;
import android.widget.ImageView;

import io.fotoapparat.preview.Frame;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

import com.numericcal.androidsensors.Tags.TTok;
import com.numericcal.edge.Dnn;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.numericcal.androidsensors.Tags.combine;
import static com.numericcal.androidsensors.Tags.extract;

/**
 * Pre-configured ObservableTransformers for convenience.
 */
public class Examples {

    public static class YoloV2 {
        private static final String TAG = "Ex.YoloV2";

        public static Observable<TTok<Bitmap>> demo(
                Single<Dnn.Handle> yolo, Observable<Bitmap> inStream, ImageView imgView) {

            return yolo.flatMapObservable(handle -> {
                int dnnInputWidth = handle.info.inputShape.get(1);
                int dnnInputHeight = handle.info.inputShape.get(2);

                Yolo.ModelParams mp = new Yolo.ModelParams(handle);

                // note: there is a race here, but DNN loading always loses to ImageView layout
                float drawScaleW = (float) imgView.getWidth() / dnnInputWidth;
                float drawScaleH = (float) imgView.getHeight() / dnnInputHeight;

                float modelScaleX = (float) dnnInputWidth / mp.S;
                float modelScaleY = (float) dnnInputHeight / mp.S;

                return inStream
                        //Camera.getFeed(this, cameraView, camPerm)
                        // add thread/entry/exit time tagging
                        .map(Tags.srcTag("source"))
                        .observeOn(Schedulers.computation())
                        // resize bitmap to fit the DNN input tensor
                        .compose(scaleTT(dnnInputWidth, dnnInputHeight))
                        .observeOn(Schedulers.computation())
                        // normalize and lay out in memory
                        .compose(yoloV2Normalize(mp.inputMean, mp.inputStd))
                        .compose(handle.runInference(extract(), combine("inference")))
                        // pull out sub-tensors for each YOLOv2 cell
                        .compose(splitCellsTT(mp.S, mp.B, mp.C))
                        // pull out high-confidence boxes in each cell
                        .compose(thresholdBoxesTT(0.3f, mp, modelScaleX, modelScaleY))
                        // remove overlapping boxes
                        .compose(suppressNonMax(0.3f))
                        // create bounding box overlay bitmap
                        .compose(drawBBoxes(imgView.getWidth(), imgView.getHeight(), drawScaleW, drawScaleH));
            });
        }

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
        yoloV2Normalize(int mean, float std) {
            return Utils.mkOT(Yolo.v2Normalize(mean, std), extract(), combine("normalize"));
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
