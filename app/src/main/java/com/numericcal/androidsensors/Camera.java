package com.numericcal.androidsensors;

import android.Manifest;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.view.CameraView;
import static io.fotoapparat.selector.LensPositionSelectorsKt.back;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.subjects.CompletableSubject;


public class Camera {
    private static final String TAG = "AS.Camera";

    /**
     * We use RxPermissions, hence this should be called from onCreate.
     * @param act - calling activity
     * @param rxp - RxPermissions manager
     * @return We annoy the user until they give the permission. Complete when granted.
     */
    public static Completable getPermission(AppCompatActivity act, RxPermissions rxp) {
        CompletableSubject sig = CompletableSubject.create();
        rxp
                .request(Manifest.permission.CAMERA)
                .map(grant -> { if (grant) return ""; else throw new Exception("");})
                .retry(10)
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(act)))
                .subscribe(__ -> {
                    //Log.d(TAG, "Permission granted!");
                    sig.onComplete();
                });

        return sig;
    }

    /**
     * Set up preview and Frame stream using Fotoapparat. Close Fotoapparat instance on disposal.
     * @param act - activity
     * @param preview - Fotoapparat view
     * @param permission - completable obtaining CAMERA permission
     * @return a stream of frames grabbed by Fotoapparat
     */
    public static Observable<Frame> setupCamera(AppCompatActivity act, CameraView preview, Completable permission) {

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
                Log.wtf(TAG, "REMOVING CAMERA!");
                fotoapparat.stop();
            });
        });
        return permission.andThen(obs);
    }

}
