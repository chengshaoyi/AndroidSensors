package com.numericcal.androidsensors;

import android.Manifest;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.AsyncSubject;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AS.Main";

    TextView statusText;
    Single<Texture.TextureRecord> camViewEvent;

    RxPermissions rxPerm;
    final AsyncSubject<Boolean> camPerm = AsyncSubject.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = (TextView) findViewById(R.id.statusText);
        camViewEvent = Texture.setTextureListener(this, R.id.camView);

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
    }

    @Override
    protected void onResume() {
        super.onResume();

        Observable.combineLatest(camViewEvent.toObservable(), camPerm, (e, __) -> {return e;})
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(tr -> {
                    Log.wtf(TAG, "I did get it " + tr.width);
                });


    }

    @Override
    protected void onPause() {

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }
}
