package com.numericcal.androidsensors;

import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import io.fotoapparat.view.CameraView;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;

import com.numericcal.edge.Dnn;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AS.Main";

    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final int TOP_LABELS = 3;

    TextView statusText;
    CameraView cameraView;

    RxPermissions rxPerm;
    Completable camPerm;

    // debug stuff
    ImageView imgView;

    Dnn.Manager dnnManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = (TextView) findViewById(R.id.statusText);
        cameraView = (CameraView) findViewById(R.id.cameraView);

        rxPerm = new RxPermissions(this);
        camPerm = Camera.getPermission(this, rxPerm, statusText); // must run in onCreate, see RxPermissions

        imgView = (ImageView) findViewById(R.id.imgView);
    }

    @Override
    protected void onResume() {
        super.onResume();

        dnnManager = Dnn.createManager(getApplicationContext());

        dnnManager.createHandle(
                Dnn.configBuilder
                        .fromAccount("MLDeployer")
                        .withAuthToken("41fa5c144a7f7323cfeba5d2416aeac3")
                        .getModelFromCollection("tinyYolo2Deploy-by-MLDeployer"))
                .toFlowable()
                .flatMap(yolo -> {

                    /** BEGIN MODEL PARAM SECTION **/
                    int inputWidth = yolo.info.inputShape().get(1);
                    int inputHeight = yolo.info.inputShape().get(2);
                    int outputLen = yolo.info.outputShape().get(1);

                    /** NOTE: this should be yolo.params.get("S"), yolo.params.get("C") or something alike **/
                    int S = 13;
                    int C = 20;
                    int B = 5;

                    float scaleX = (float) inputWidth / S;
                    float scaleY = (float) inputHeight / S;

                    List<Yolo.AnchorBox> anchors = new ArrayList<>();
                    anchors.add(new Yolo.AnchorBox(1.08f, 1.19f));
                    anchors.add(new Yolo.AnchorBox(3.42f, 4.41f));
                    anchors.add(new Yolo.AnchorBox(6.63f, 11.38f));
                    anchors.add(new Yolo.AnchorBox(9.42f, 5.11f));
                    anchors.add(new Yolo.AnchorBox(16.62f, 10.52f));

                    List<String> labels = Arrays.asList(
                            "aeroplane", "bicycle", "bird", "boat", "bottle",
                            "bus", "car", "cat", "chair", "cow",
                            "dining table", "dog", "horse", "motorbike", "person",
                            "potted plant", "sheep", "sofa", "train", "tv monitor");
                    /** END MODEL PARAM SECTION **/

                    Log.wtf(TAG, "inputWidth " + inputWidth);
                    Log.wtf(TAG, "inputHeight " + inputHeight);
                    Log.wtf(TAG, "scaleX " + scaleX);
                    Log.wtf(TAG, "scaleY " + scaleY);

                    //// Camera.getFeed(this, cameraView, camPerm, inputWidth, inputHeight)

                    return Flowable.fromIterable(Assets.loadAssets(this.getApplicationContext().getAssets(),
                                    Arrays.asList("examples/dog.jpg"), BitmapFactory::decodeStream))
                            .delay(1, TimeUnit.SECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnNext(bmp -> {
                                Log.wtf(TAG, "pic: " + bmp.getHeight() + " x " + bmp.getWidth());
                                imgView.setImageBitmap(bmp);
                            })
                            .compose(Camera.scaleTo(inputWidth, inputHeight))
                            .compose(Yolo.v2Normalize())
                            .compose(yolo.runInference())
                            .compose(Yolo.splitCells(S, B, C))
                            .compose(Yolo.thresholdAndBox(0.3f, anchors, scaleX, scaleY))
                            .compose(Yolo.suppressNonMax(0.3f))
                            .doOnNext(boxList -> {
                                Log.wtf(TAG, "After cleanup " + boxList.size() + " boxes!");
                                for(int i=0; i<boxList.size(); i++) {
                                    Yolo.BBox bbox = boxList.get(i);
                                    Log.wtf(TAG, "\t " + bbox + " " + labels.get(bbox.maxClassArg));
                                }
                            });
                })
                .observeOn(AndroidSchedulers.mainThread())
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe();

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
