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
import io.reactivex.FlowableTransformer;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
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
    public static Flowable<Bitmap> getFeed(
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
                .toFlowable(BackpressureStrategy.LATEST)
                .observeOn(Schedulers.computation(), false, 1)
                .doOnNext(__ -> {Log.wtf(TAG, "TICK! " + Thread.currentThread().getName());})
                .compose(Utils.yuv2bmp())
                .compose(Utils.bmpRotate(90));
    }

    /**
     * Performs center crop. Does not check if sizes are reasonable.
     * @param w - desired width
     * @param h - desired height
     * @return return new bitmap flowable
     */
    public static FlowableTransformer<Bitmap, Bitmap> centerCropTo(int w, int h) {
        return upstream ->
                upstream
                .map(bmp -> {
                    int upper = (bmp.getHeight() - h)/2;
                    int left = (bmp.getWidth() - w)/2;
                    return Bitmap.createBitmap(bmp, left, upper, w, h);
                });
    }

    public static FlowableTransformer<Bitmap, Bitmap> scaleTo(int w, int h) {
        return upstream ->
                upstream
                .map(bmp -> {
                    return Bitmap.createScaledBitmap(bmp, w, h, true);
                });
    }

    /**
     * Simple IIR filter. Gain of 1.
     * @param vectorSize - number of dimensions to filter
     * @param discount - the pole of the IIR filter
     * @return filtered vector
     */
    public static FlowableTransformer<float[], float[]> lpf(int vectorSize, float discount) {
        return upstream -> Flowable.defer(() -> {
            float[] acc = new float[vectorSize]; // state
            return upstream.map(sample -> {
                float[] res = new float[vectorSize]; // result

                for(int i=0; i<vectorSize; i++) {
                    acc[i] = acc[i]*discount + sample[i];
                    res[i] = (1-discount) * acc[i]; // DC gain
                }
                return res;
            });
        });
    }

}
