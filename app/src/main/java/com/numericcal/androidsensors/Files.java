package com.numericcal.androidsensors;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.functions.Function;

/**
 * Set up and use prepackaged files in assets and in filesystem.
 */
public class Files {

    private static final String TAG = "AS.Files";

    /**
     * Grab all filenames with a given suffix in an assets dir.
     * @param ctx - app context
     * @param dir - the directory where to look
     * @param suffix - suffix of files we're interested in
     * @return a list of file names
     */
    public static List<String> getAssetFileNames(Context ctx, String dir, String suffix) {
        List <String> samples = new ArrayList<>();

        try {

            for (String fname: ctx.getAssets().list(dir)) {
                if (fname.endsWith(suffix)) {
                    samples.add(dir + "/" + fname);
                }
            }
        } catch (IOException ioex) {
            Log.i(TAG, "No exaple " + suffix + " files.");
        }

        Log.wtf(TAG, "" + samples.size());
        return samples;
    }

    /**
     * Grab all filenames in filesystem/dir with a given suffix.
     * @param ctx - application context (to determine file dirs)
     * @param dir - subdirectory of the app files dir to list
     * @param suffix - file suffix to list
     * @return
     */
    public static List<String> getFilesysFileNames(Context ctx, String dir, String suffix) {
        List<String> samples = new ArrayList<>();
        String filesDir = ctx.getApplicationContext().getFilesDir().toString();
        File fdir = new File(filesDir + "/" + dir + "/");

        for (File fle: fdir.listFiles()) {
            if (fle.toString().endsWith(suffix)) {
                samples.add(fle.toString());
            }
        }

        return samples;
    }

    /**
     * Load data from file list given a decoder function.
     * @param ast - asset manager
     * @param names - list of file names
     * @param decoder - decoder function stream -> T
     * @param <T> - type of loaded asset
     * @return list of loaded assets
     */
    private static <T> List<T> loadAssetsFiles(AssetManager ast, List<String> names, Function<InputStream, T> decoder) {
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

    /**
     * Load data from the list of file names using decoder function.
     * @param names
     * @param decoder
     * @param <T>
     * @return
     */
    private static <T> List<T> loadFilesysFiles(List<String> names, Function<InputStream, T> decoder) {
        List<T> res = new ArrayList<>();
        for (String name: names) {
            try (InputStream ins = new FileInputStream(name)) {
                res.add(decoder.apply(ins));
            } catch (IOException ioex) {
                Log.i(TAG, "Missing: " + name);
            } catch (Exception exc) {
                Log.i(TAG, "Decoder exception: " + exc.getMessage());
            }
        }
        return res;
    }

    public static <T> List<T> loadFromAssets(Context ctx, List<String> fnames, Function<InputStream, T> decoder) {
        return loadAssetsFiles(ctx.getAssets(), fnames, decoder);
    }

    public static <T> List<T> loadFromFilesys(Context ctx, String dir, String suffix, Function<InputStream, T> decoder) {
        List<String> fnames = getFilesysFileNames(ctx, dir, suffix);
        return loadFilesysFiles(fnames, decoder);
    }

}
