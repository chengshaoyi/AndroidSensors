package com.numericcal.androidsensors;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.functions.Function;

import static java.lang.Float.compare;

public class Yolo {

    private static final String TAG = "AS.Yolo";

    /**
     * A class to represent all the boxes reported by a single cell.
     * We do not copy the tensor data, but simply add context (e.g. cell upper left corner x/y)
     * and start/stop pointer for reading from the original YOLOv2 output tensor.
     *
     * Every object is relevant for all the boxes reported by a single cell.
     */
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

    /**
     * A class to represent a single bounding box, it's class and confidence of the detection.
     */
    public static class BBox {
        int bottom;
        int top;
        int left;
        int right;

        int maxClassArg;
        float confidence;
        String label;

        BBox(int bottom, int top, int left, int right, int maxClassArg, String label, float confidence) {
            this.bottom = bottom;
            this.top = top;
            this.left = left;
            this.right = right;

            this.maxClassArg = maxClassArg;
            this.label = label;
            this.confidence = confidence;
        }

        @Override
        /* we match the python code for debug simplicity */
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

    /**
     * Preprocessing for YOLOv2. Normalizes image data to [0.0, 1.0] range and aligns it in memory
     * in NHWC.BGR order.
     * @return float[] representation of the input image
     */
    public static Function<Bitmap, float[]> v2Normalize() {
        return Utils.bmpToFloat_HWC_BGR(0, 255.0f);
    }

    /**
     * Slice YOLOv2 output tensor into sections. Each section is relevant for all the boxes of one
     * image cell.
     * @param S - number of x/y divisions on the cell grid
     * @param B - boxes per cell
     * @param C - classes detected per box
     * @return
     */
    public static Function<float[], List<CellBoxes>> splitCells(int S, int B, int C) {
        return tensor -> {
            int dataPerCell = (5 + C) * B;
            int dataPerRow = S * dataPerCell; // S cells, B boxes/cell, 5+C floats/box
            List<CellBoxes> lst = new ArrayList<>();

            for(int row=0; row<S; row++) {
                for(int col=0; col<S; col++) {
                    int offset = row * dataPerRow + col * dataPerCell;

                    lst.add(new CellBoxes((float) col, (float) row, S, B, C, tensor, offset, offset + dataPerCell));
                }
            }
            return lst;
        };
    }

    /**
     * Detect potential boxes above the given threshold. Repackage them by calculating their
     * detected class, confidence in that detection and their actual shapes.
     * @param threshold - minimum overal confidence to accept detection
     * @param anchors - list of anchor box descriptions for this network (DNN parameter)
     * @param scaleW - width scaling for calculations based on image width vs cell width (DNN parameter)
     * @param scaleH - height scaling (DNN parameter)
     * @return
     */
    public static Function<List<CellBoxes>, List<BBox>> thresholdAndBox(
            float threshold, List<AnchorBox> anchors, List<String> labels, float scaleW, float scaleH) {
        return cbList -> {
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

                        highConfidenceBoxes.add(new BBox(bottom, top, left, right, maxClass, labels.get(maxClass), confidence));
                    }
                }
            }

            return highConfidenceBoxes;
        };
    }

    /**
     * Calculate box area. Assumes (x->right, y->down) coordinate system. See thresholdAndBox.
     * @param b
     * @return
     */
    public static float area(BBox b) {
        return (float) (b.bottom - b.top) * (b.right - b.left);
    }

    /**
     * Calculate Intersection/Union metric. Assumes (x->right, y->down) coordinate system.
     * @param x
     * @param y
     * @return
     */
    public static float iou(BBox x, BBox y) {
        int top = Math.max(x.top, y.top);
        int bottom = Math.min(x.bottom, y.bottom);
        if (bottom <= top) { // remember y->down
            return 0.0f; // no intersection
        }

        int left = Math.max(x.left, y.left);
        int right = Math.min(x.right, y.right);
        if (right <= left) {
            return 0.0f;
        }

        float intersection = area(new BBox(bottom, top, left, right, 0, "", 0.0f)); // dummy maxClassArg/confidence

        return intersection / (area(x) + area(y) - intersection);
    }

    public static Function<List<BBox>, List<BBox>> suppressNonMax(float thresholdIoU) {
        return bBoxes -> {
            List<BBox> boxes = new ArrayList<>();

            if (bBoxes.size() > 0) {
                Collections.sort(bBoxes, (b1,b2) -> compare(b2.confidence, b1.confidence));
                boxes.add(bBoxes.get(0));
                bBoxes.remove(0);

                for (BBox newBox: bBoxes) { // k^2
                    boolean newDetection = true;
                    for (BBox detectedBox: boxes) {
                        if (iou(newBox, detectedBox) > thresholdIoU) newDetection = false;;
                    }
                    if (newDetection) boxes.add(newBox); // does not overlap with others too much, add it
                }
            }

            return boxes;
        };
    }

    /**
     * Scale the BBox assuming assuming simple central projection.
     * @param b - the box to scale
     * @param stretchWidth - stretch of the x axis
     * @param stretchHeight - stretch of the y axis
     * @return
     */
    public static BBox rescaleBBoxBy(BBox b, float stretchWidth, float stretchHeight) {
        return new BBox( Math.round(b.bottom * stretchHeight), Math.round(b.top * stretchHeight),
                Math.round(b.left * stretchWidth), Math.round(b.right * stretchWidth), b.maxClassArg, b.label, b.confidence);
    }
}
