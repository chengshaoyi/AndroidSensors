package com.numericcal.androidsensors;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.FlowableTransformer;

public class Yolo {

    private static final String TAG = "AS.Yolo";

    public static class CellBoxes {
        float[] arr; // raw array for B boxes on C classes
        int startOffset;
        int stopOffset;

        float x;
        float y;     // upper left corner

        int S;       // number of cells per axis (assumed same on x/y)
        int C;       // number of classes
        int B;       // number of boxes per cell
        CellBoxes(float x, float y, int S, int B, int C, float[] arr, int start, int stop) {
            this.x = x;
            this.y = y;

            this.S = S;
            this.C = C;
            this.B = B;

            this.startOffset = start;
            this.stopOffset = stop;
            this.arr = arr;
        }
    }

    public static class BBox {
        int bottom;
        int top;
        int left;
        int right;

        int maxClassArg;
        String maxClass;
        float confidence;

        BBox(int bottom, int top, int left, int right, int maxClassArg, float confidence) {
            this.bottom = bottom;
            this.top = top;
            this.left = left;
            this.right = right;

            this.maxClassArg = maxClassArg;
            this.confidence = confidence;
        }

        @Override
        public String toString() {
            return "[(" + left + ", " + top + ", " + right + ", " + bottom + ") | " + maxClassArg + " | " + confidence + "]";
        }
    }

    public static class AnchorBox {
        float width;
        float height;
        AnchorBox(float width, float height) {
            this.width = width;
            this.height = height;
        }
    }

    public static FlowableTransformer<Bitmap, float[]> v2Normalize() {
        return Utils.bmpToFloat_HWC_BGR(0, 255.0f);
    }

    public static FlowableTransformer<float[], List<CellBoxes>> splitCells(int S, int B, int C) {
        return upstream ->
                upstream
                .map(tensor -> {
                    int dataPerCell = (5 + C) * B;
                    int dataPerRow = S * dataPerCell; // S cells, B boxes/cell, 5+C floats/box
                    List<CellBoxes> lst = new ArrayList<>();

                    for(int row=0; row<S; row++) {
                        for(int col=0; col<S; col++) {
                            int offset = row * dataPerRow + col * dataPerCell;

                            lst.add(new CellBoxes((float) col, (float) row, S, B, C, tensor, offset, offset + dataPerCell));
                        }
                    }
                    Log.wtf(TAG, "data per cell: " + dataPerCell);
                    return lst;
                });
    }

    public static FlowableTransformer<List<CellBoxes>, List<BBox>> thresholdAndBox(
            float threshold, List<AnchorBox> anchors, float scaleW, float scaleH) {
        return upstream ->
                upstream
                .map(cbList -> {
                    List<BBox> highConfidenceBoxes = new ArrayList<>();

                    // run across all cells of the frame
                    for(int c=0; c<cbList.size(); c++) { // TODO: rewrite as foreach
                        CellBoxes cb = cbList.get(c);
                        // run accross all boxes in a cell
                        for(int b=0; b<cb.B; b++) {

                            int offset = cb.startOffset + (5 + cb.C) * b;
                            float[] predictions = new float[cb.C]; // move this out to avoid allocation per box * frame!

                            Utils.softmax(cb.C, offset + 5, cb.arr, 0, predictions);
                            int maxClass = Utils.argmax(predictions, 0, cb.C);

                            float objectConfidence = Utils.sigmoidS(cb.arr[offset + 4]);

                            float confidence = predictions[maxClass] * objectConfidence;

                            if (confidence > threshold) { // is confidence high enough?
                                AnchorBox anchor = anchors.get(b);

                                float tx = cb.arr[offset + 0];
                                float ty = cb.arr[offset + 1];
                                float tw = cb.arr[offset + 2];
                                float th = cb.arr[offset + 3];

                                float centerX = (cb.x + Utils.sigmoidS(tx)) * scaleW;
                                float centerY = (cb.y + Utils.sigmoidS(ty)) * scaleH;

                                float roiW = (float) Math.exp(tw) * anchor.width * scaleW;
                                float roiH = (float) Math.exp(th) * anchor.height * scaleH;

                                int left = (int) (centerX - roiW/2.0);  // coords (x->right, y->down)
                                int right = (int) (centerX + roiW/2.0);
                                int bottom = (int) (centerY + roiH/2.0);
                                int top = (int) (centerY - roiH/2.0);

                                highConfidenceBoxes.add(new BBox(bottom, top, left, right, maxClass, confidence));
                            }
                        }
                    }

                    return highConfidenceBoxes;
                });
    }

    public static FlowableTransformer<List<BBox>, List<BBox>> suppressNonMax(float thresholdIoU) {
        return upstream ->
                upstream
                .map(bBoxes -> {
                    List<BBox> boxes = new ArrayList<>();

                    return boxes;
                });
    }
}
