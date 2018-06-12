package com.numericcal.androidsensors;

import android.Manifest;
import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.view.CameraView;
import static io.fotoapparat.selector.LensPositionSelectorsKt.back;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.functions.Function;
import io.reactivex.subjects.CompletableSubject;


/**
 * Set up and use camera streams.
 */
public class Camera {
    private static final String TAG = "AS.Camera";

    /**
     * RxPermissions seems to require calling this in onCreate.
     * @param act - calling activity
     * @param rxp - RxPermissions manager
     * @return We annoy the user until they give the permission. Complete when granted.
     */
    public static Completable getPermission(AppCompatActivity act, RxPermissions rxp, TextView statusText) {
        CompletableSubject sig = CompletableSubject.create();
        rxp
                .request(Manifest.permission.CAMERA)
                .map(grant -> { if (grant) return ""; else throw new Exception("");})
                .retry(5)
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(act)))
                .subscribe(__ -> sig.onComplete(),
                        thr -> statusText.setText("We need CAMERA permission! Please restart."));

        return sig;
    }

    /**
     * Set up preview and Frame stream using Fotoapparat. Close Fotoapparat instance on disposal.
     * @param act - activity
     * @param preview - Fotoapparat view
     * @param permission - completable obtaining CAMERA permission
     * @return a stream of frames grabbed by Fotoapparat
     */
    public static Flowable<Frame> getFeed(
            AppCompatActivity act, CameraView preview, Completable permission) {

        Observable<Frame> obs = Observable.create(emitter -> {
            Fotoapparat fotoapparat = Fotoapparat
                    .with(act)
                    .into(preview)
                    .previewScaleType(ScaleType.CenterCrop)
                    .lensPosition(back())
                    .frameProcessor(emitter::onNext)
                    .build();
            fotoapparat.start();
            emitter.setCancellable(() -> {
                Log.i(TAG, "REMOVING CAMERA!");
                fotoapparat.stop();
            });
        });
        return permission
                .andThen(obs)
                .toFlowable(BackpressureStrategy.LATEST);
    }

    /**
     * Performs center crop. Does not check if sizes are reasonable.
     * @param w - desired width
     * @param h - desired height
     * @return return new bitmap flowable
     */
    public static Function<Bitmap, Bitmap> centerCropTo(int w, int h) {
        return bmp -> {
            int upper = (bmp.getHeight() - h)/2;
            int left = (bmp.getWidth() - w)/2;
            return Bitmap.createBitmap(bmp, left, upper, w, h);
        };
    }

    public static Function<Bitmap, Bitmap> scaleTo(int w, int h) {
        return bmp -> Bitmap.createScaledBitmap(bmp, w, h, true);
    }


}
