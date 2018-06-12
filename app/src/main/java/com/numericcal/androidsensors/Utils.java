package com.numericcal.androidsensors;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;
import android.util.Pair;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import io.fotoapparat.preview.Frame;
import io.reactivex.FlowableTransformer;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;

/**
 * Helper functions.
 */
public class Utils {
    public static final String TAG = "AS.Utils";

    /**
     * Get 1st order finite diff. NOTE: does not check edge cases (<2 elements in the list).
     * @param data
     * @return
     */
    public static List<Long> diff(List<Long> data) {
        List<Long> res = new ArrayList<>();
        for(int i=1; i<data.size(); i++) {
            res.add(data.get(i) - data.get(i-1));
        }
        return res;
    }

    /**
     * A simple printer for arrays during debugging.
     * @param n - how many items to print
     * @param offset - index of the first item
     * @param arr - the array to print
     * @return a string representation of the print
     */
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

    /**
     * Find the maxumum element in the subarray.
     * @param arr - the array to search
     * @param start - start index (inclusive)
     * @param stop - stop index (exclusive)
     * @return the value of the max
     */
    public static float max(float[] arr, int start, int stop) {
        float max = arr[start];
        for(int k=start; k<stop; k++) {
            if (arr[k] > max) {
                max = arr[k];
            }
        }

        return max;
    }

    /**
     * Find top k labels. Trivial implementation. Complexity k*n is not great.
     * For small k (2-3) probably faster than to sort. Not important to optimize.
     * @param probs - probability distribution for labels
     * @param labels - list of labels in string forms
     * @param k - how many labels to find
     * @return
     */
    public static List<String> topkLabels(float[] probs, List<String> labels, int k) {
        List<String> topLabels = new ArrayList<>();
        for(int i=0; i<k; i++) {
            int maxPos = Utils.argmax(probs, 0, probs.length);
            probs[maxPos] = 0.0f;
            topLabels.add(labels.get(maxPos));
        }
        return topLabels;
    }

    /**
     * Calculate softmax on a sub-array [fromOffset, fromOffset+len-1].
     * @param len - length of the subarray
     * @param fromOffset - starting offset for reading
     * @param from - read array
     * @param toOffset - starting offset for writing
     * @param to - write array
     */
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

    /**
     * Calculate sigmoid(x) = 1/(1 + e^-x).
     * @param num
     * @return
     */
    public static float sigmoidS(float num) {
        return (float) (1.0/(1.0 + Math.exp(-num)));
    }

    /**
     * Extract red/green/blue from the integer representation of a pixel (ARGB).
     * @param pix
     * @return
     */
    static int red(int pix) { return (pix >> 16) & 0xFF; }
    static int green(int pix) { return (pix >> 8) & 0xFF; }
    static int blue(int pix) { return (pix) & 0xFF; }

    /**
     * Create a flowable transformer from pre-processing, processing, post-processing.
     * @param processor - the actual work
     * @param extractor - pre-processor
     * @param combiner - post-processor
     * @return
     */
    public static <F,R,Q,T> FlowableTransformer<F,T> mkFT(
            Function<R,Q> processor, Function<F,R> extractor, BiFunction<F,Q,T> combiner) {
        return upstream ->
                upstream
                .map(input -> combiner.apply(input, processor.apply(extractor.apply(input))));
    }

    public static <F,T> FlowableTransformer<F,T> mkFT(Function<F,T> processor) {
        return mkFT(processor, (F x) -> x, (__,y) -> y);
    }

    public static abstract class Actor<R,S,Q> implements Function<R,Q> {
        S state;
        @Override
        public abstract Q apply(R arg);
        Actor(S init) {
            this.state = init;
        }
    }

    /**
     * Turn a Bitmap into HWC.RGB float buffer.
     * @param mean - average for normalization
     * @param std - standard dev for normalization
     * @return a function object Bitmap -> float[]
     */
    public static Function<Bitmap, float[]> bmpToFloat_HWC_RGB(int mean, float std) {
        return bmp -> {
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

        };
    }

    /**
     * Turn a Bitmap into HWC.BGR float buffer.
     * @param mean - average for normalization
     * @param std - standard dev for normalization
     * @return float array flowable
     */
    public static Function<Bitmap, float[]> bmpToFloat_HWC_BGR(int mean, float std) {
        return bmp -> {
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
        };
    }

    /**
     * Convert YUV NV21 to Bitmap. Fotoapparat will produce NV21 but we need Bitmap for DNN.
     * @return new bitmap flowable
     */
    public static Function<Frame, Bitmap> yuv2bmp() {
        return f -> {
            int width = f.getSize().width;
            int height = f.getSize().height;
            YuvImage yuv = new YuvImage(f.getImage(), ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 100, os);
            byte[] jpegByteArray = os.toByteArray();
            return BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);
        };
    }

    /**
     * Simple bitmap rotation.
     * @param angle - clockwise angle to rotate.
     * @return rotated bitmap
     */
    public static Function<Bitmap, Bitmap> bmpRotate(float angle) {
        return bmp -> {
            Matrix mat = new Matrix();
            mat.postRotate(angle);
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mat, true);
        };
    }

    public static <K,V> List<Pair<K,V>> zip(List<K> k, List<V> v) {
        List<Pair<K,V>> res = new ArrayList<>();
        for(int i=0; i<Math.min(k.size(), v.size()); i++) {
            res.add(new Pair<>(k.get(i), v.get(i)));
        }
        return res;
    }

    /**
     * Simple IIR filter. Gain of 1.
     * @param vectorSize - number of dimensions to filter
     * @param discount - the pole of the IIR filter
     * @return filtered vector
     */
    public static Utils.Actor<float[], float[], float[]> lpf(int vectorSize, float discount) {
        return new Utils.Actor<float[], float[], float[]>(new float[vectorSize]) {
            @Override
            public float[] apply(float[] arg) {
                float[] res = new float[vectorSize]; // result

                for(int i=0; i<vectorSize; i++) {
                    state[i] = state[i]*discount + arg[i];
                    res[i] = (1-discount) * state[i]; // DC gain
                }
                return res;
            }
        };
    }

    /**
     * Quick and dirty variable-length timestamp diff averaging. NOTE: mutates TTok tags.
     * @param discount - low pass filtering coefficient
     * @param <T>
     * @return
     */
    public static <T> Utils.Actor<Tags.TTok<T>, List<Float>, List<Pair<String, Float>>> lpfTT(float discount) {
        return new Actor<Tags.TTok<T>, List<Float>, List<Pair<String, Float>>>(new ArrayList<>()) {
            @Override
            public List<Pair<String, Float>> apply(Tags.TTok<T> arg) {
                List<Float> filtered = new ArrayList<>();
                List<Long> diffs = diff(arg.timestamps);
                arg.tags.remove(0);

                int idxOverlap = Math.min(state.size(), diffs.size());
                int idxMax = Math.max(state.size(), diffs.size());

                for(int i=0; i<idxOverlap; i++) {
                    filtered.add(state.get(i) * discount + diffs.get(i));
                }

                if (state.size() < diffs.size()) {
                    for(int i=idxOverlap; i<idxMax; i++) {
                        filtered.add((float) diffs.get(i));
                    }
                }

                return zip(arg.tags, filtered);
            }
        };
    }

    public static String printPairs(List<Pair<String, Float>> z) {
        StringBuilder s = new StringBuilder();
        for (Pair p:z) {
            s.append(String.format("%-20s %12.2f ms\n", p.first, p.second));
        }
        return s.toString();
    }

}
