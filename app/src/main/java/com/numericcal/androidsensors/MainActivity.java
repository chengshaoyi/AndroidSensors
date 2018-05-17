package com.numericcal.androidsensors;

import android.Manifest;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.result.BitmapPhoto;
import io.fotoapparat.result.PhotoResult;
import io.fotoapparat.result.adapter.rxjava2.ObservableAdapter;
import io.fotoapparat.result.adapter.rxjava2.SingleAdapter;
import io.fotoapparat.view.CameraView;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.AsyncSubject;
import io.reactivex.subjects.PublishSubject;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import static io.fotoapparat.selector.LensPositionSelectorsKt.back;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AS.Main";

    TextView statusText;
    CameraView cameraView;

    Fotoapparat fotoapparat;
    PublishSubject<Frame> frames = PublishSubject.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = (TextView) findViewById(R.id.statusText);
        cameraView = (CameraView) findViewById(R.id.cameraView);

        fotoapparat = Fotoapparat
                .with(this)
                .into(cameraView)
                .previewScaleType(ScaleType.CenterCrop)
                .lensPosition(back())
                .frameProcessor((Frame f) -> frames.onNext(f))
                .build();

        /*
        rxPerm = new RxPermissions(this);
        rxPerm.request(Manifest.permission.CAMERA)
                .map(grant -> { // abuse reconnect until we get permission
                    if (grant) return "";
                    else throw new Exception("");
                })
                .retry()
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(x -> {
                    camPerm.onNext(true);
                    camPerm.onComplete();

                    Log.d(TAG, "Got the CAMERA permission.");
                }); // must be done here (see lib docs)
                */
    }

    @Override
    protected void onResume() {
        super.onResume();

        frames
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(f -> {
                    Log.wtf(TAG, "Got a frame!");
                });
        statusText.setText("Hello!");
        fotoapparat.start();

        /*
        Observable.combineLatest(camViewEvent.toObservable(), camPerm, (e, __) -> {return e;})
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(tr -> {
                    Log.wtf(TAG, "I did get it " + tr.width);
                });
                */



    }

    @Override
    protected void onPause() {

        fotoapparat.stop();

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }
}
