package com.numericcal.androidsensors;

import android.graphics.Canvas;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import io.fotoapparat.parameter.Resolution;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.view.CameraView;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import com.numericcal.edge.Dnn;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AS.Main";

    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final int TOP_LABELS = 3;

    TextView statusText;
    CameraView cameraView;

    ImageView imgView;

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
                .toFlowable()
                .flatMap(handle -> {
                    int inputWidth = handle.info.inputShape().get(1);
                    int inputHeight = handle.info.inputShape().get(2);
                    int outputLen = handle.info.outputShape().get(1);

                    Observable<Frame> cameraFeed = Camera.setupCamera(this, cameraView, camPerm);

                    return cameraFeed
                            .toFlowable(BackpressureStrategy.LATEST)
                            .compose(Camera.yuv2bmp())
                            .compose(Camera.bmpRotate(90))
                            .compose(Camera.scaleTo(inputWidth, inputHeight))
                            .compose(Camera.bmpToFloatHWC(IMAGE_MEAN, IMAGE_STD))
                            .compose(handle.runInference())
                            .compose(Camera.lpf(outputLen, 0.75f))
                            .map(probs -> {
                                List<String> topLabels = new ArrayList<>();
                                for(int k=0; k<TOP_LABELS; k++) {
                                    int maxPos = Utils.argmax(probs);
                                    probs[maxPos] = 0.0f;
                                    topLabels.add(handle.info.labels().get(maxPos));
                                }
                                return topLabels;
                            });
                })
                .observeOn(AndroidSchedulers.mainThread())
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(
                        res -> {
                            Log.wtf(TAG, "Got a result " + res.toString());
                            statusText.setText(res.get(0));
                            },
                        (Throwable err) -> { Log.wtf(TAG, "Got an ERROR " + err.toString()); });

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
