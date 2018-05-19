package com.numericcal.androidsensors;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import io.fotoapparat.view.CameraView;
import io.reactivex.Completable;
import io.reactivex.Single;

import com.numericcal.edge.Dnn;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AS.Main";

    TextView statusText;
    CameraView cameraView;

    RxPermissions rxPerm;
    Completable camPerm;

    Dnn.Manager dnnManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = (TextView) findViewById(R.id.statusText);
        cameraView = (CameraView) findViewById(R.id.cameraView);

        rxPerm = new RxPermissions(this);
        camPerm = Camera.getPermission(this, rxPerm); // must run in onCreate, see RxPermissions

    }

    @Override
    protected void onResume() {
        super.onResume();

        dnnManager = Dnn.createManager(getApplicationContext());

        Single<Dnn.Handle> dnn = dnnManager.createHandle(
                Dnn.configBuilder
                        .setAccount("rrs")
                        .setModel("poets2-by-rrs"));
        dnn
                .doOnSuccess(__ -> {Log.wtf(TAG, "GOT IT!");})
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(
                        handle -> { Log.wtf(TAG, "Got a handle " + handle.info.task() + " " + handle.info.labels().size()); },
                        (Throwable err) -> { Log.wtf(TAG, "Got an ERROR " + err.toString()); });
        
        Camera.setupCamera(this, cameraView, camPerm)
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(__ -> {
                    //Log.wtf(TAG, "Frame!");
                });

    }

    @Override
    protected void onPause() {

        // for the demo, release all DNNs when not on top
        if (!isChangingConfigurations() && dnnManager != null) {
            Log.i(TAG, "seems to be going in background ...");
            dnnManager.release();
            dnnManager = null;
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }
}
