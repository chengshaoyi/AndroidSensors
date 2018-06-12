package com.numericcal.androidsensors;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import io.fotoapparat.view.CameraView;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import com.numericcal.edge.Dnn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.numericcal.androidsensors.Tags.combine;
import static com.numericcal.androidsensors.Tags.extract;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AS.Main";

    // TODO: this should go into model parameters
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final int TOP_LABELS = 3;

    TextView statusText;
    TableLayout tableLayout;
    CameraView cameraView;

    RxPermissions rxPerm;
    Completable camPerm;

    ImageView overlayView;
    ImageView extraOverlay;

    ImageView dbgView;

    Dnn.Manager dnnManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = (TextView) findViewById(R.id.statusText);
        tableLayout = (TableLayout) findViewById(R.id.tableLayout);
        cameraView = (CameraView) findViewById(R.id.cameraView);

        rxPerm = new RxPermissions(this);
        camPerm = Camera.getPermission(this, rxPerm, statusText); // must run in onCreate, see RxPermissions

        overlayView = (ImageView) findViewById(R.id.overlayView);
        extraOverlay = (ImageView) findViewById(R.id.extraOverlay);

        dbgView = (ImageView) findViewById(R.id.dbgView);
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

                    Bitmap bmpOverlay = Bitmap.createBitmap(overlayView.getWidth(), overlayView.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvasOverlay = new Canvas(bmpOverlay);

                    float scaleWidth = (float) bmpOverlay.getWidth() / inputWidth;
                    float scaleHeight = (float) bmpOverlay.getHeight() / inputHeight;

                    //return Flowable.fromIterable(Assets.loadAssets(this.getApplicationContext().getAssets(), Arrays.asList("examples/dog.jpg", "examples/person.jpg"), BitmapFactory::decodeStream))
                    return Camera.getFeed(this, cameraView, camPerm)
                            .observeOn(Schedulers.computation(), false, 1)
                            .map(Tags.srcTag("assets"))
                            .compose(Utils.mkFT(Utils.yuv2bmp(), extract(), combine("yuv2bmp")))
                            .compose(Utils.mkFT(Utils.bmpRotate(90), extract(), combine("rotate")))
                            .compose(Utils.mkFT(Camera.scaleTo(inputWidth, inputHeight), extract(), combine("scaling")))
                            .compose(Utils.mkFT(Yolo.v2Normalize(), extract(), combine("normalize")))
                            .compose(yolo.runInference(extract(), combine("inference")))
                            .compose(Utils.mkFT(Yolo.splitCells(S, B, C), extract(), combine("splitcells")))
                            .compose(Utils.mkFT(Yolo.thresholdAndBox(0.3f, anchors, scaleX, scaleY), extract(), combine("threshold")))
                            .compose(Utils.mkFT(Yolo.suppressNonMax(0.3f), extract(), combine("suppress")))
                            .observeOn(AndroidSchedulers.mainThread(), false, 1)
                            .doOnNext(boxList -> {
                                canvasOverlay.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                                for (Yolo.BBox bbox: boxList.token) {
                                    Overlay.drawBox(Yolo.rescaleBBoxBy(bbox, scaleWidth, scaleHeight), new Overlay.Line(Color.RED, 2.0f), canvasOverlay);
                                    Log.i(TAG, "\t " + bbox + " " + labels.get(bbox.maxClassArg));
                                }
                                extraOverlay.setImageBitmap(bmpOverlay);
                            });
                })
                .compose(Utils.mkFT(Utils.lpfTT(0.95f)))
                .observeOn(AndroidSchedulers.mainThread())
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(this::populateTable, err -> { err.printStackTrace(); });

    }
    private void populateTable(List<Pair<String, Float>> tbl) {
        int cnt = 0;
        tableLayout.removeAllViews();

        for (Pair<String, Float> p: tbl) {
            TableRow row = new TableRow(this);
            TableRow.LayoutParams lp = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(lp);

            TextView key = new TextView(this);
            key.setText(p.first);
            key.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            TableRow.LayoutParams keyLPs = new TableRow.LayoutParams();
            keyLPs.weight = 1.0f;
            key.setLayoutParams(keyLPs);

            TextView val = new TextView(this);
            val.setText(p.second.toString());
            val.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            TableRow.LayoutParams valLPs = new TableRow.LayoutParams();
            valLPs.weight = 1.0f;
            val.setLayoutParams(valLPs);

            row.addView(key);
            row.addView(val);

            tableLayout.addView(row, cnt);
            cnt += 1;
        }
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
