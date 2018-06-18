package com.numericcal.androidsensors;

import android.util.Log;
import android.util.Pair;

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

    public static class MetaData {
        List<String> tags;
        List<String> threads;
        List<Long> entryTimes;
        List<Long> exitTimes;

        MetaData(List<String> tags, List<String> threads,
                 List<Long> entryTimes, List<Long> exitTimes) {
            this.tags = tags;
            this.threads = threads;
            this.entryTimes = entryTimes;
            this.exitTimes = exitTimes;
        }

    }

    public static class TTok<T> {
        MetaData md;
        T token;

        TTok(MetaData md, T token) {
            this.md = md;
            this.token = token;
        }
    }

    public static <T> Function<TTok<T>, Pair<TTok<T>, T>> extract() {
        return input -> {
            input.md.entryTimes.add(System.currentTimeMillis());
            return new Pair<>(input, input.token);
        };
    }

    public static <T,F> Function<Pair<TTok<T>, F>, TTok<F>> combine(String tag) {
        return input -> {

            MetaData md = input.first.md;
            F res = input.second;

            md.tags.add(tag);
            md.threads.add(Thread.currentThread().getName());
            md.exitTimes.add(System.currentTimeMillis());

            return new TTok<>(md, res);
        };
    }

    public static <T> Function<T, TTok<T>> srcTag(String tag) {
        return src -> {
            List<String> tags = new ArrayList<>();
            List<String> threads = new ArrayList<>();
            List<Long> entryTimes = new ArrayList<>();
            List<Long> exitTimes = new ArrayList<>();

            tags.add(tag);
            threads.add(Thread.currentThread().getName());

            entryTimes.add(System.currentTimeMillis());
            exitTimes.add(System.currentTimeMillis());

            return new TTok<>(new MetaData(tags, threads, entryTimes, exitTimes), src);
        };
    }



}
