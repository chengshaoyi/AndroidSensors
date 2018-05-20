package com.numericcal.androidsensors;

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
}
