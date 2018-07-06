package com.numericcal.androidsensors;

import android.graphics.Bitmap;
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
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

import io.reactivex.schedulers.Schedulers;

import com.numericcal.edge.Dnn;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.numericcal.androidsensors.Examples.YoloV2.drawBBoxes;
import static com.numericcal.androidsensors.Examples.YoloV2.splitCellsTT;
import static com.numericcal.androidsensors.Examples.YoloV2.suppressNonMax;
import static com.numericcal.androidsensors.Examples.YoloV2.thresholdBoxesTT;
import static com.numericcal.androidsensors.Tags.combine;
import static com.numericcal.androidsensors.Tags.extract;
import static com.numericcal.androidsensors.Examples.YoloV2.rotateTT;
import static com.numericcal.androidsensors.Examples.YoloV2.scaleTT;
import static com.numericcal.androidsensors.Examples.YoloV2.yoloV2Normalize;
import static com.numericcal.androidsensors.Examples.YoloV2.yuv2bmpTT;

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

        Observable<Tags.TTok<Bitmap>> stream = dnnManager.createHandle(Dnn.configBuilder
                .fromAccount("MLDeployer")
                .withAuthToken("41fa5c144a7f7323cfeba5d2416aeac3")
                .getModelFromCollection("ncclYOLO"))
                //.getModelFromCollection("tinyYolo2LiteDeploy-by-MLDeployer"))
                .flatMapObservable(handle -> {

                    int dnnInputWidth = handle.info.inputShape.get(1);
                    int dnnInputHeight = handle.info.inputShape.get(2);

                    Yolo.ModelParams mp = new Yolo.ModelParams();

                    int canvasWidth = overlayView.getWidth();
                    int canvasHeight = overlayView.getHeight();

                    float drawScaleW = (float) canvasWidth / dnnInputWidth;
                    float drawScaleH = (float) canvasHeight / dnnInputHeight;

                    float modelScaleX = (float) dnnInputWidth / mp.S;
                    float modelScaleY = (float) dnnInputHeight / mp.S;

                    Observable<Long> interval = Observable.interval(220L, TimeUnit.MILLISECONDS);

                    return Camera.getFeed(this, cameraView, camPerm)
                            .sample(interval)
                            // add thread/entry/exit time tagging
                            .map(Tags.srcTag("camera"))
                            .observeOn(Schedulers.computation())
                            // convert colorspace and rotate
                            .compose(yuv2bmpTT()).compose(rotateTT(90.0f))
                            .observeOn(Schedulers.computation())
                            // resize bitmap to fit the DNN input tensor
                            .compose(scaleTT(dnnInputWidth, dnnInputHeight))
                            .observeOn(Schedulers.computation())
                            // normalize and lay out in memory
                            .compose(yoloV2Normalize())
                            .compose(handle.runInference(extract(), combine("inference")))
                            // pull out sub-tensors for each YOLOv2 cell
                            .compose(splitCellsTT(mp.S, mp.B, mp.C))
                            // pull out high-confidence boxes in each cell
                            .compose(thresholdBoxesTT(0.3f, mp, modelScaleX, modelScaleY))
                            // remove overlapping boxes
                            .compose(suppressNonMax(0.3f))
                            // create bounding box overlay bitmap
                            .compose(drawBBoxes(canvasWidth, canvasHeight, drawScaleW, drawScaleH));
                });

        // finally filter out TTok logs and display bitmap + timings in the UI
        stream.compose(Utils.mkOT(Utils.lpfTT(0.90f)))
                .observeOn(AndroidSchedulers.mainThread())
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(this::updateUI, err -> { err.printStackTrace(); });

    }

    private <T> void updateUI(Pair<Bitmap,List<Pair<String, Float>>> report) {
        overlayView.setImageBitmap(report.first);

        int cnt = 0;
        tableLayout.removeAllViews();

        List<Pair<String,Float>> tbl = report.second;

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
            val.setText(Long.valueOf(Math.round(p.second)).toString());
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
