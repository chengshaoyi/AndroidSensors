package com.numericcal.androidsensors;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import io.fotoapparat.preview.Frame;
import io.reactivex.FlowableTransformer;

public class Utils {
    public static final String TAG = "AS.Utils";

    public static String nElemStr(int n, int offset, float[] arr) {
        StringBuilder arrStr = new StringBuilder();
        for(int i=0; i<n; i++) {
            arrStr.append(arr[offset + i]);
            arrStr.append(", ");
        }
        return arrStr.toString();
    }

    public static float sum(float[] arr) {
        float sum = 0.0f;
        for (float x: arr) {
            sum += x;
        }
        return sum;
    }

    /**
     * Find a maximum argument in a subarray.
     * NOTE: no error or bound checking is done!
     * @param arr - the array to search
     * @param start - start index (inclusive)
     * @param stop - stop index (exclusive)
     * @return return the index of the max
     */
    public static int argmax(float[] arr, int start, int stop) {
        float candidate = arr[start];
        int pos = start;

        for(int i=start; i < stop; i++) {
            if (arr[i] > candidate) {
                candidate = arr[i];
                pos = i;
            }
        }

        return pos;
    }

    public static float max(float[] arr, int start, int stop) {
        float max = arr[start];
        for(int k=start; k<stop; k++) {
            if (arr[k] > max) {
                max = arr[k];
            }
        }

        return max;
    }

    public static List<String> topkLabels(float[] probs, List<String> labels, int TOP_LABELS) {
        List<String> topLabels = new ArrayList<>();
        for(int k=0; k<TOP_LABELS; k++) {
            int maxPos = Utils.argmax(probs, 0, probs.length);
            probs[maxPos] = 0.0f;
            topLabels.add(labels.get(maxPos));
        }
        return topLabels;
    }

    public static void softmax(int len, int fromOffset, float[] from, int toOffset, float[] to) {
        float acc = 0.0f;
        float curr;

        float maxVal = max(from, fromOffset, fromOffset + len);

        int readPtr = fromOffset;
        int writePtr = toOffset;

        for(int k=0; k<len; k++) {
            curr = (float) Math.exp(from[readPtr] - maxVal); // make sure all exponents negative
            to[writePtr] = curr;
            acc += curr;

            readPtr += 1;
            writePtr += 1;
        }
        writePtr = toOffset;
        for(int k=0; k<len; k++) {
            to[writePtr] /= acc;

            writePtr += 1;
        }
    }

    public static void sigmoidA(float[] inp, float[] outp) {
        for(int k=0; k<inp.length; k++) {
            outp[k] = (float) (1.0/(1.0 + Math.exp(-inp[k])));
        }
    }

    public static float sigmoidS(float num) {
        return (float) (1.0/(1.0 + Math.exp(-num)));
    }

    static int red(int pix) { return (pix >> 16) & 0xFF; }
    static int green(int pix) { return (pix >> 8) & 0xFF; }
    static int blue(int pix) { return (pix) & 0xFF; }

    /**
     * Turn a Bitmap into HWC.RGB float buffer.
     * @param mean - average for normalization
     * @param std - standard dev for normalization
     * @return float array flowable
     */
    public static FlowableTransformer<Bitmap, float[]> bmpToFloat_HWC_RGB(int mean, float std) {
        return upstream ->
                upstream
                        .map(bmp -> {
                            int height = bmp.getHeight();
                            int width = bmp.getWidth();
                            int size = height * width;

                            int[] ibuff = new int[size];
                            float[] fbuff = new float[3 * size]; // rgb, each a float

                            bmp.getPixels(ibuff, 0, width, 0, 0, width, height);

                            for (int i = 0; i < ibuff.length; i++) {
                                int val = ibuff[i];
                                fbuff[i * 3 + 0] = (red(val) - mean) / std;
                                fbuff[i * 3 + 1] = (green(val) - mean) / std;
                                fbuff[i * 3 + 2] = (blue(val) - mean) / std;
                            }

                            return fbuff;

                        });
    }

    /**
     * Turn a Bitmap into HWC.BGR float buffer.
     * @param mean - average for normalization
     * @param std - standard dev for normalization
     * @return float array flowable
     */
    public static FlowableTransformer<Bitmap, float[]> bmpToFloat_HWC_BGR(int mean, float std) {
        return upstream ->
                upstream
                        .map(bmp -> {
                            int height = bmp.getHeight();
                            int width = bmp.getWidth();
                            int size = height * width;

                            int[] ibuff = new int[size];
                            float[] fbuff = new float[3 * size]; // rgb, each a float

                            bmp.getPixels(ibuff, 0, width, 0, 0, width, height);

                            for (int i = 0; i < ibuff.length; i++) {
                                int val = ibuff[i];
                                fbuff[i * 3 + 0] = (blue(val) - mean) / std;
                                fbuff[i * 3 + 1] = (green(val) - mean) / std;
                                fbuff[i * 3 + 2] = (red(val) - mean) / std;
                            }

                            return fbuff;
                        });
    }

    /**
     * Convert YUV NV21 to Bitmap. Fotoapparat will produce NV21 but we need Bitmap for DNN.
     * @return new bitmap flowable
     */
    public static FlowableTransformer<Frame, Bitmap> yuv2bmp() {
        return upstream ->
                upstream
                        .map((Frame f) -> {
                            int width = f.getSize().width;
                            int height = f.getSize().height;
                            YuvImage yuv = new YuvImage(f.getImage(), ImageFormat.NV21, width, height, null);
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            yuv.compressToJpeg(new Rect(0, 0, width, height), 100, os);
                            byte[] jpegByteArray = os.toByteArray();
                            return BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);
                        });
    }

    /**
     * Simple bitmap rotation.
     * @param angle - clockwise angle to rotate.
     * @return rotated bitmap
     */
    public static FlowableTransformer<Bitmap, Bitmap> bmpRotate(float angle) {
        return upstream ->
                upstream
                        .map(bmp -> {
                            Matrix mat = new Matrix();
                            mat.postRotate(angle);
                            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mat, true);
                        });
    }

}
