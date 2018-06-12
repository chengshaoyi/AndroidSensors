package com.numericcal.androidsensors;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;

/**
 * Conveniences for adding (measurement) tags into Rx chain.
 */
public class Tags {

    private static final String TAG = "AS.Tags";

    public static class TTok<T> {
        List<String> tags;
        List<Long> timestamps;

        T token;

        TTok(List<String> tags, List<Long> timestamps, T token) {
            this.tags = tags;
            this.timestamps = timestamps;
            this.token = token;
        }
    }

    public static <T> Function<TTok<T>, T> extract() {
        return input -> input.token;
    }

    public static <T,F> BiFunction<TTok<T>, F, TTok<F>> combine(String tag) {
        return (input, res) -> {
            input.tags.add(tag);
            input.timestamps.add(System.currentTimeMillis());
            return new TTok<>(input.tags, input.timestamps, res);
        };
    }

    public static <T> Function<T, TTok<T>> srcTag(String tag) {
        return src -> {
            List<String> tags = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();

            tags.add(tag);
            timestamps.add(System.currentTimeMillis());
            return new TTok<>(tags, timestamps, src);
        };
    }

}
