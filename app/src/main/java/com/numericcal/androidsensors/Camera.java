package com.numericcal.androidsensors;

import android.Manifest;
import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.view.CameraView;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.CompletableSubject;

import static io.fotoapparat.selector.LensPositionSelectorsKt.back;

public class Camera {
    private static final String TAG = "AS.Camera";

    /**
     * We use RxPermissions, hence this should be called from onCreate.
     * @param act - calling activity
     * @param rxp - RxPermissions manager
     * @return We annoy the user until they give the permission. Complete action when granted.
     */
    public static Single<Boolean> getPermission(AppCompatActivity act, RxPermissions rxp) {
        CompletableSubject sig = CompletableSubject.create();
        rxp
                .request(Manifest.permission.CAMERA)
                .map(grant -> { if (grant) return ""; else throw new Exception("");})
                .retry()
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(act)))
                .subscribe(__ -> {
                    //Log.d(TAG, "Permission granted!");
                    sig.onComplete();
                });

        return sig.toSingleDefault(true);
    }

    public static Observable<Frame> setupCamera(AppCompatActivity act, CameraView preview) {

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
        return obs;
    }

}
