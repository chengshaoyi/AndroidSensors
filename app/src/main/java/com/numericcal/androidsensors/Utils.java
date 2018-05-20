package com.numericcal.androidsensors;

import java.util.ArrayList;
import java.util.List;

public class Utils {
    public static final String TAG = "AS.Utils";

    public static int argmax(float[] arr) {
        float candidate = arr[0];
        int pos = 0;

        for(int i=0; i < arr.length; i++) {
            if (arr[i] > candidate) {
                candidate = arr[i];
                pos = i;
            }
        }

        return pos;
    }

    public static List<String> topkLabels(float[] probs, List<String> labels, int TOP_LABELS) {
        List<String> topLabels = new ArrayList<>();
        for(int k=0; k<TOP_LABELS; k++) {
            int maxPos = Utils.argmax(probs);
            probs[maxPos] = 0.0f;
            topLabels.add(labels.get(maxPos));
        }
        return topLabels;
    }
}
