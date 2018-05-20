package com.numericcal.androidsensors;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import java.io.ByteArrayOutputStream;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.view.CameraView;
import static io.fotoapparat.selector.LensPositionSelectorsKt.back;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.FlowableTransformer;
import io.reactivex.Observable;
import io.reactivex.subjects.CompletableSubject;


public class Camera {
    private static final String TAG = "AS.Camera";

    /**
     * RxPermissions seems to require calling this in onCreate.
     * @param act - calling activity
     * @param rxp - RxPermissions manager
     * @return We annoy the user until they give the permission. Complete when granted.
     */
    public static Completable getPermission(AppCompatActivity act, RxPermissions rxp, TextView statusText) {
        CompletableSubject sig = CompletableSubject.create();
        rxp
                .request(Manifest.permission.CAMERA)
                .map(grant -> { if (grant) return ""; else throw new Exception("");})
                .retry(5)
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(act)))
                .subscribe(__ -> sig.onComplete(),
                        thr -> statusText.setText("We need CAMERA permission! Please restart."));

        return sig;
    }

    /**
     * Set up preview and Frame stream using Fotoapparat. Close Fotoapparat instance on disposal.
     * @param act - activity
     * @param preview - Fotoapparat view
     * @param permission - completable obtaining CAMERA permission
     * @param width - desired bitmap width
     * @param height - desired bitmap height
     * @return a stream of frames grabbed by Fotoapparat
     */
    public static Flowable<Bitmap> getFeed(
            AppCompatActivity act, CameraView preview, Completable permission,
            int width, int height) {

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
                Log.i(TAG, "REMOVING CAMERA!");
                fotoapparat.stop();
            });
        });
        return permission
                .andThen(obs)
                .toFlowable(BackpressureStrategy.LATEST)
                .compose(yuv2bmp())
                .compose(bmpRotate(90))
                .compose(scaleTo(width, height));
    }

    /**
     * Performs center crop. Does not check if sizes are reasonable.
     * @param w - desired width
     * @param h - desired height
     * @return return new bitmap flowable
     */
    public static FlowableTransformer<Bitmap, Bitmap> centerCropTo(int w, int h) {
        return upstream ->
                upstream
                .map(bmp -> {
                    int upper = (bmp.getHeight() - h)/2;
                    int left = (bmp.getWidth() - w)/2;
                    return Bitmap.createBitmap(bmp, left, upper, w, h);
                });
    }

    public static FlowableTransformer<Bitmap, Bitmap> scaleTo(int w, int h) {
        return upstream ->
                upstream
                .map(bmp -> {
                    return Bitmap.createScaledBitmap(bmp, w, h, true);
                });
    }

    static int red(int pix) { return (pix >> 16) & 0xFF; }
    static int green(int pix) { return (pix >> 8) & 0xFF; }
    static int blue(int pix) { return (pix) & 0xFF; }

    /**
     * Turn a Bitmap into CHW float buffer.
     * TODO: check bmp.getPixels()
     * @param mean - average for normalization
     * @param std - standard dev for normalization
     * @return float array flowable
     */
    public static FlowableTransformer<Bitmap, float[]> bmpToFloatHWC(int mean, float std) {
        return upstream ->
                upstream
                        .map(bmp -> {
                            int height = bmp.getHeight();
                            int width = bmp.getWidth();
                            int size = height * width;

                            int[] ibuff = new int[size];
                            float[] fbuff = new float[3 * size]; // rgb, each a float

                            bmp.getPixels(ibuff, 0, width, 0, 0, width, height);

                            for (int i = 0; i < ibuff.length; ++i) {
                                int val = ibuff[i];
                                fbuff[i * 3 + 0] = (red(val) - mean) / std;
                                fbuff[i * 3 + 1] = (green(val) - mean) / std;
                                fbuff[i * 3 + 2] = (blue(val) - mean) / std;
                            }

                            return fbuff;

                        });
    }

    /**
     * Convert YUV NV21 to Bitmap. Fotoapparat will produce NV21 but we need Bitmap for DNN.
     * @return new bitmap flowable
     */
    public static FlowableTransformer<Frame, Bitmap> yuv2bmp() {
        return upstream ->
            upstream
                    .map((Frame f) -> {
                        int width = f.getSize().width;
                        int height = f.getSize().height;
                        YuvImage yuv = new YuvImage(f.getImage(), ImageFormat.NV21, width, height, null);
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        yuv.compressToJpeg(new Rect(0, 0, width, height), 100, os);
                        byte[] jpegByteArray = os.toByteArray();
                        return BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);
                    });
    }

    /**
     * Simple bitmap rotation.
     * @param angle - clockwise angle to rotate.
     * @return rotated bitmap
     */
    public static FlowableTransformer<Bitmap, Bitmap> bmpRotate(float angle) {
        return upstream ->
                upstream
                .map(bmp -> {
                    Matrix mat = new Matrix();
                    mat.postRotate(angle);
                    return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mat, true);
                });
    }

    /**
     * Simple IIR filter. Gain of 1.
     * @param vectorSize - number of dimensions to filter
     * @param discount - the pole of the IIR filter
     * @return filtered vector
     */
    public static FlowableTransformer<float[], float[]> lpf(int vectorSize, float discount) {
        return upstream -> Flowable.defer(() -> {
            float[] acc = new float[vectorSize]; // state
            return upstream.map(sample -> {
                float[] res = new float[vectorSize]; // result

                for(int i=0; i<vectorSize; i++) {
                    acc[i] = acc[i]*discount + sample[i];
                    res[i] = (1-discount) * acc[i]; // DC gain
                }
                return res;
            });
        });
    }

}
