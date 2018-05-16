package com.numericcal.androidsensors;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.view.SurfaceView;
import android.view.TextureView;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class Texture {
    public static class TextureRecord {
        public final TextureView texture;
        public final SurfaceTexture surface;
        public final int width;
        public final int height;
        TextureRecord(TextureView texture, SurfaceTexture surface, int width, int height) {
            this.texture = texture;
            this.surface = surface;
            this.width = width;
            this.height = height;
        }
    }

    public static Single<TextureRecord> getTextureSurface(TextureView texture) {
        Single<TextureRecord> observable = Single.create(emitter -> {
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

        return observable.cache();
    }
}
