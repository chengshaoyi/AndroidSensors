package com.numericcal.androidsensors;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Pair;
import android.view.Surface;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.functions.Function;

public class Camera {
    private static final String TAG = "AS.Camera";

    /*
     * iterate through all cameras and pick the one we want
     */

    public static String getCamera(@NonNull CameraManager manager, Function<CameraCharacteristics, Boolean> pred) throws CameraAccessException {
        String[] cameraList = manager.getCameraIdList();
        if (cameraList.length == 0) {
            throw new RuntimeException("No cameras on this device?!");
        }

        for (String camId : cameraList) {
            CameraCharacteristics chars = manager.getCameraCharacteristics(camId);

            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                continue;
            }

            try {
                if (pred.apply(chars)) {
                    return camId;
                }
            } catch (Exception ex) {
                throw new RuntimeException("Cam selection predicate throw?!");
            }
        }
        throw new RuntimeException("Cam selection predicate passed on all cams!");
    }

    /*
     * open the camera device and get a stream of CameraStateEvents
     */

    public enum CameraStateEvents {
        OPENED,
        CLOSED,
        DISCONNECTED
    }

    @SuppressWarnings("MissingPermission")
    public static Observable<Pair<CameraStateEvents, CameraDevice>> openCamera(
            @NonNull String camId, @NonNull CameraManager manager, @NonNull Observable<Boolean> rxp) {

        return rxp
                .flatMap(grant -> {
                    if (grant) {
                        return Observable.create(emitter -> {
                            CameraDevice.StateCallback openCallBack = new CameraDevice.StateCallback() {
                                @Override
                                public void onOpened(@NonNull CameraDevice camera) {
                                    emitter.onNext(new Pair<>(CameraStateEvents.OPENED, camera));
                                }

                                @Override
                                public void onDisconnected(@NonNull CameraDevice camera) {
                                    emitter.onNext(new Pair<>(CameraStateEvents.DISCONNECTED, camera));
                                    emitter.onComplete();
                                }

                                @Override
                                public void onError(@NonNull CameraDevice camera, int error) {
                                    emitter.onError(new RuntimeException("Received error: " + error));
                                }
                            };
                            manager.openCamera(camId, openCallBack, new Handler(Looper.getMainLooper()));
                        });
                    } else { // user declined
                        return Observable.empty(); // should we do Observable.error()?
                    }
                });
    }

    /*
     * get a stream of CaptureStateEvents
     */

    public enum CaptureStateEvents {
        CONFIGURED,
        READY,
        ACTIVE,
        CLOSED,
        SURFACE_PREPARED
    }

    @NonNull
    public static Observable<Pair<CaptureStateEvents, CameraCaptureSession>> createCaptureSession(
            @NonNull CameraDevice cameraDevice,
            @NonNull List<Surface> surfaceList) {
        return Observable.create(emitter -> {
            CameraCaptureSession.StateCallback stateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    emitter.onNext(new Pair<>(CaptureStateEvents.CONFIGURED, session));
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    emitter.onError(new RuntimeException("Capture session configuration failed."));
                }

                @Override
                public void onReady(@NonNull CameraCaptureSession session) {
                    emitter.onNext(new Pair<>(CaptureStateEvents.READY, session));
                }

                @Override
                public void onActive(@NonNull CameraCaptureSession session) {
                    emitter.onNext(new Pair<>(CaptureStateEvents.ACTIVE, session));
                }

                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    emitter.onNext(new Pair<>(CaptureStateEvents.CLOSED, session));
                }

                @Override
                public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
                    emitter.onNext(new Pair<>(CaptureStateEvents.SURFACE_PREPARED, session));
                }
            };

            cameraDevice.createCaptureSession(surfaceList, stateCallback, new Handler(Looper.getMainLooper()));
        });
    }

    /*
     * get request stream
     */

    public enum CaptureEvents {
        STARTED,
        PROGRESSED,
        COMPLETED,
        SEQUENCE_COMPLETED,
        SEQUENCE_ABORTED
    }

    public static class CaptureData {
        final CaptureEvents event;
        final CameraCaptureSession session;
        final CaptureRequest request;
        final CaptureResult result;

        CaptureData(CaptureEvents event, CameraCaptureSession session, CaptureRequest request, CaptureResult result) {
            this.event = event;
            this.session = session;
            this.request = request;
            this.result = result;
        }
    }

    @NonNull
    public static Observable<CaptureData> repeatingRequest(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request) {
        return Observable.create(emitter -> {
            CameraCaptureSession.CaptureCallback requestCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                }

                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    if (!emitter.isDisposed()) {
                        emitter.onNext(new CaptureData(CaptureEvents.COMPLETED, session, request, result));
                    }
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                    if (!emitter.isDisposed()) {
                        emitter.onError(new RuntimeException("Capture failed."));
                    }
                }

                @Override
                public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                }

                @Override
                public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                }

                @Override
                public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
                }
            };
            session.setRepeatingRequest(request, requestCallback, new Handler(Looper.getMainLooper()));;
        });
    }

    public static class Helpers {
        public static Function<CameraCharacteristics, Boolean> facingSelector(int requestedFacing) {
            return new Function<CameraCharacteristics, Boolean>() {
                @Override
                public Boolean apply(CameraCharacteristics cameraCharacteristics) throws Exception {
                    Integer actualFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                    return (actualFacing == requestedFacing);
                }
            };
        }
    }
}
