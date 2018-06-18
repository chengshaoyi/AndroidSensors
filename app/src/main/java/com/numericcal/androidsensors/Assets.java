package com.numericcal.androidsensors;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;

/**
 * Set up and use prepackaged files in assets.
 */
public class Assets {

    public static final String TAG = "AS.Assets";


    /**
     * Grab all filenames in assets/examples dir with a given suffix.
     * @param ctx - app context
     * @param suffix - suffix of files we're interested in
     * @return a list of file names
     */
    public static List<String> getAssetFileNames(Context ctx, String suffix) {
        String dir = "examples";
        List <String> sampleImages = new ArrayList<>();

        try {
            for (String fname: ctx.getAssets().list(dir)) {
                if (fname.endsWith(dir + "/" + suffix)) {
                    sampleImages.add(fname);
                }
            }
        } catch (IOException ioex) {
            Log.i(TAG, "No exaple " + suffix + " files.");
        }

        return sampleImages;
    }

    /**
     * Load data from file list given a decoder function.
     * @param ast - asset manager
     * @param names - list of file names
     * @param decoder - decoder function stream -> T
     * @param <T> - type of loaded asset
     * @return list of loaded assets
     */
    public static <T> List<T> loadAssets(AssetManager ast, List<String> names, Function<InputStream, T> decoder) {
        List<T> res = new ArrayList<>();
        for (String name: names) {
            try (InputStream ins = ast.open(name)) {
                //imgs.add(BitmapFactory.decodeStream(ins));
                res.add(decoder.apply(ins));
            } catch (IOException ioex) {
                // just drop non-existing files
                Log.i(TAG, "Missing: " + name);
            } catch (java.lang.Exception exc) {
                Log.i(TAG, "Decoder exception: " + exc.getMessage());
            }
        }
        return res;
    }

    public static <T> List<T> listAssets(Context ctx, String suffix, Function<InputStream, T> decoder) {
        List<String> fnames = getAssetFileNames(ctx, suffix);
        return loadAssets(ctx.getAssets(), fnames, decoder);
    }
    public static <T> Flowable<T> flowAssets(Context ctx, String suffix, Function<InputStream, T> decoder) {
        return Flowable.fromIterable(listAssets(ctx, suffix, decoder));
    }

}
