package com.numericcal.androidsensors;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.jakewharton.rxbinding2.view.RxView;
import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import io.fotoapparat.view.CameraView;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;

import com.numericcal.edge.Dnn;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AS.Main";

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

        // set up numericcal DNN manager
        dnnManager = Dnn.createManager(getApplicationContext());

        // request a network
        Single<Dnn.Handle> faceDet = dnnManager.createHandle(Dnn.configBuilder
                .fromAccount("MLDeployer")
                .withAuthToken("41fa5c144a7f7323cfeba5d2416aeac3")
                .getModel("faceDetector-by-MLDeployer"))
                // & display some info in the UI
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess(handle -> {
                    statusText.setText(handle.info.engine);
                });

        Observable<Bitmap> frames = faceDet
                .flatMapObservable(__ -> {
                    // source stream cycles through .jpg files in assets/examples
                    // we do it here to make sure input stream starts after DNN is ready
                    List<String> files = Files.getAssetFileNames(this.getApplicationContext(),
                            "examples", "jpg");
                    List<Bitmap> bmps = Files.loadFromAssets(
                            this.getApplicationContext(), files, BitmapFactory::decodeStream);

                    return Observable.interval(4000L, TimeUnit.MILLISECONDS)
                            .map(idx -> bmps.get(idx.intValue() % bmps.size()));
                });

        // display frames
        frames.observeOn(AndroidSchedulers.mainThread())
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(bmp -> {
                    // set the new image
                    overlayView.setImageBitmap(bmp);
                    // clear the box overlay
                    extraOverlay.setImageResource(android.R.color.transparent);
                });

        // set up the DNN processing
        Observable<Tags.TTok<Bitmap>> boxStream = Examples.MobileSSDFaceDet.demo(faceDet, frames, extraOverlay);




        // finally filter out TTok logs and display boxes + timings in the UI
        boxStream.compose(Utils.mkOT(Utils.lpfTT(0.5f)))
                .observeOn(AndroidSchedulers.mainThread())
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(this::updateUI, Throwable::printStackTrace );

    }

    private <T> void updateUI(Pair<Bitmap,List<Pair<String, Float>>> report) {
        extraOverlay.setImageBitmap(report.first);

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
