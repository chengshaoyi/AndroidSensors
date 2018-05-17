package com.numericcal.androidsensors;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.TextureView;

import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;

public class Texture {
    private static final String TAG = "AS.Texture";

    public static class TextureRecord {
        public final AutoFitTextureView texture;
        public final SurfaceTexture surface;
        public final int width;
        public final int height;
        TextureRecord(AutoFitTextureView texture, SurfaceTexture surface, int width, int height) {
            this.texture = texture;
            this.surface = surface;
            this.width = width;
            this.height = height;
        }
    }

    public static Single<TextureRecord> setTextureListener(Activity act, int textureId) {
        AutoFitTextureView texture = (AutoFitTextureView) act.findViewById(textureId);

        Single<TextureRecord> result = Single.create(emitter -> {
            TextureView.SurfaceTextureListener surfaceCallback = new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    emitter.onSuccess(new TextureRecord(texture, surface, width, height));
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            };
            texture.setSurfaceTextureListener(surfaceCallback);
        });

        return result.cache();
    }


}
