// Copyright (c) 2017-present boyw165
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
//    The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
//    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.my.demo.dlib;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;

import com.my.core.protocol.IProgressBarView;
import com.my.core.util.ProfilerUtil;
import com.my.demo.dlib.reactive.Camera2CaptureSessionObservable;
import com.my.demo.dlib.reactive.Camera2ImageReaderObservable;
import com.my.demo.dlib.reactive.Camera2ImageReaderObservable.OnImageAvailableEvent;
import com.my.demo.dlib.reactive.Camera2Observable;
import com.my.demo.dlib.reactive.TextureViewObservable;
import com.my.demo.dlib.view.AutoFitTextureView;
import com.my.demo.dlib.view.FaceLandmarksOverlayView;
import com.my.jni.dlib.DLibLandmarks68Detector;
import com.my.jni.dlib.IDLibFaceDetector;
import com.my.reactive.uiModel.UiModel;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public class SampleOfCamera2ApiActivity1
    extends AppCompatActivity
    implements IProgressBarView {

    /**
     * The camera preview size will be chosen to be the smallest frame by pixel
     * size capable of containing a DESIRED_SIZE x DESIRED_SIZE square.
     */
    private static final int MINIMUM_PREVIEW_SIZE = 320;

    // View.
    @BindView(R.id.texture)
    AutoFitTextureView mCameraView;
    @BindView(R.id.overlay)
    FaceLandmarksOverlayView mOverlayView;
    ProgressDialog mProgressDialog;
    AlertDialog mAlertDialog;

    // Butter Knife.
    Unbinder mUnbinder;

    // Camera configuration.
    int mSensorOrientation;
    Size mPreviewSize;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    // DLibFace Detector.
    IDLibFaceDetector mLandmarksDetector;

    // Data.
    CompositeDisposable mDisposables;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sample_of_faces_and_landmarks_3);

        // Init view binding.
        mUnbinder = ButterKnife.bind(this);

        // The progress bar.
        mProgressDialog = new ProgressDialog(this);

        // Init the face detector.
        mLandmarksDetector = new DLibLandmarks68Detector();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Starts a background thread
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        mDisposables = new CompositeDisposable();
        mDisposables.add(
            grantPermission()
                .compose(waitForTextureSurfaceReady())
                .compose(openCameraToGetCameraDevice())
                .compose(createCaptureSession())
                .compose(setCaptureRequestAndObserveAvailableImage())
                .onErrorReturn(new Function<Throwable, Object>() {
                    @Override
                    public Object apply(Throwable throwable) throws Exception {
                        return UiModel.failed(throwable);
                    }
                })
                .ofType(UiModel.class)
                .subscribe(new Consumer<UiModel>() {
                    @Override
                    public void accept(UiModel viewModel)
                        throws Exception {
                        // Error handling.
                        if (viewModel.error != null) {
                            showAlertDialog(viewModel.error);
                        }
                    }
                }));
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop every observables:
        // Will stop: CameraDevice -> CameraCaptureSession -> ImageReader
        // in this order as the same order adding the observables.
        mDisposables.clear();

        // Force to hide the progress bar and alert-dialog.
        hideProgressBar();
        hideAlertDialog();

        // Stops the background thread
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void showProgressBar() {
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMessage(getString(R.string.loading));
        mProgressDialog.show();
    }

    @Override
    public void showProgressBar(String msg) {
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMessage(msg);
        mProgressDialog.show();
    }

    @Override
    public void hideProgressBar() {
        mProgressDialog.dismiss();
    }

    @Override
    public void updateProgress(int progress) {
        mProgressDialog.setProgress(progress);
        mProgressDialog.show();
    }

    public void showAlertDialog(Throwable throwable) {
        if (mAlertDialog == null) {
            mAlertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.alert_common_error)
                .create();
        }

        if (mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }

        mAlertDialog.setMessage(throwable.getMessage());
        mAlertDialog.show();
    }

    public void hideAlertDialog() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected / Private Methods ////////////////////////////////////////////

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // View binding.
        mUnbinder.unbind();
    }

    private View.OnClickListener onClickToBack() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        };
    }

    private Observable<?> grantPermission() {
        return new RxPermissions(this)
            .request(Manifest.permission.READ_EXTERNAL_STORAGE,
                     Manifest.permission.WRITE_EXTERNAL_STORAGE,
                     Manifest.permission.ACCESS_NETWORK_STATE,
                     Manifest.permission.CAMERA)
            .flatMap(new Function<Boolean, ObservableSource<Object>>() {
                @Override
                public ObservableSource<Object> apply(Boolean granted)
                    throws Exception {
                    if (!granted) {
                        throw new IllegalStateException("Permission is not granted.");
                    }

                    return Observable.just(true).ofType(Object.class);
                }
            });
    }

    private ObservableTransformer<Object, ?> waitForTextureSurfaceReady() {
        return new ObservableTransformer<Object, Object>() {
            @Override
            public ObservableSource<Object> apply(Observable<Object> upstream) {
                return upstream
                    .ofType(Boolean.class)
                    .flatMap(new Function<Boolean, ObservableSource<Object>>() {
                        @Override
                        public ObservableSource<Object> apply(Boolean granted)
                            throws Exception {
                            if (granted) {
                                return new TextureViewObservable(mCameraView).ofType(Object.class);
                            } else {
                                throw new IllegalStateException("Permission is not granted.");
                            }
                        }
                    });
            }
        };
    }

    private ObservableTransformer<Object, ?> openCameraToGetCameraDevice() {
        return new ObservableTransformer<Object, Object>() {
            @Override
            public ObservableSource<Object> apply(Observable<Object> upstream) {
                return upstream
                    .ofType(Boolean.class)
                    .flatMap(new Function<Boolean, ObservableSource<?>>() {
                        @Override
                        public ObservableSource<?> apply(Boolean ok)
                            throws Exception {
                            final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

                            // Configure the camera preview size and transform.
                            final String cameraId = configureCamera(manager);

                            return new Camera2Observable(manager,
                                                         cameraId,
                                                         mBackgroundHandler);
                        }
                    })
                    .ofType(Object.class);
            }
        };
    }

    private ObservableTransformer<Object, ?> createCaptureSession() {
        return new ObservableTransformer<Object, Object>() {
            @Override
            public ObservableSource<Object> apply(Observable<Object> upstream) {
                return upstream
                    .ofType(CameraDevice.class)
                    .flatMap(new Function<CameraDevice, ObservableSource<?>>() {
                        @Override
                        public ObservableSource<?> apply(CameraDevice device)
                            throws Exception {
                            // Configure the preview.
                            SurfaceTexture texture = mCameraView.getSurfaceTexture();
                            // Configure the size of default buffer to be
                            // the size of camera preview we want.
                            texture.setDefaultBufferSize(mPreviewSize.getWidth(),
                                                         mPreviewSize.getHeight());
                            final Surface previewSurface = new Surface(texture);

                            // Configure the reader for the preview frames.
                            final ImageReader previewImageReader = ImageReader.newInstance(
                                mPreviewSize.getWidth(),
                                mPreviewSize.getHeight(),
                                ImageFormat.YUV_420_888, 2);
                            final Surface readerSurface = previewImageReader.getSurface();

                            // Prepare output surfaces for capture session.
                            final List<Surface> captureOutputs = new ArrayList<>();
                            captureOutputs.add(previewSurface);
                            captureOutputs.add(readerSurface);

                            return Observable.mergeArray(
                                // Ready to create the capture session.
                                new Camera2CaptureSessionObservable(device,
                                                                    captureOutputs,
                                                                    mBackgroundHandler),
                                // Because we also want to get the preview image
                                // for post processing, create an observable for
                                // emitting the available image.
                                new Camera2ImageReaderObservable(previewImageReader,
                                                                 mBackgroundHandler));
                        }
                    })
                    .ofType(Object.class);
            }
        };
    }

    private ObservableTransformer<Object, ?> setCaptureRequestAndObserveAvailableImage() {
        return new ObservableTransformer<Object, Object>() {
            @Override
            public ObservableSource<Object> apply(Observable<Object> upstream) {
                return upstream
                    // Make the upstream shared among several downstream:
                    //
                    //                   +----> downstream 1
                    // .----------.      |
                    // | upstream |------+----> downstream 2
                    // '----------'      |
                    //                   +----> downstream 3
                    //                   |
                    //                   ... or more
                    .publish(new Function<Observable<Object>, ObservableSource<Object>>() {
                        @Override
                        public ObservableSource<Object> apply(Observable<Object> shared)
                            throws Exception {
                            return Observable.mergeArray(
                                shared.compose(setRepeatedCaptureRequest()),
                                shared.compose(onImageAvailable()));
                        }
                    });
            }
        };
    }

    private ObservableTransformer<Object, ?> setRepeatedCaptureRequest() {
        return new ObservableTransformer<Object, Object>() {
            @Override
            public ObservableSource<Object> apply(Observable<Object> upstream) {
                return upstream
                    .ofType(Camera2CaptureSessionObservable.SessionBlob.class)
                    .flatMap(new Function<Camera2CaptureSessionObservable.SessionBlob, ObservableSource<?>>() {
                        @Override
                        public ObservableSource<?> apply(Camera2CaptureSessionObservable.SessionBlob sessionBlob)
                            throws Exception {
                            final CameraCaptureSession session = sessionBlob.captureSession;
                            final CameraDevice device = sessionBlob.cameraDevice;
                            final List<Surface> captureOutputs = sessionBlob.captureOutputs;

                            // Init the request builder.
                            final CaptureRequest.Builder requestBuilder = device
                                .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            // Auto focus should be continuous for camera preview.
                            requestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                            // Add output surfaces.
                            for (Surface outputSurface : captureOutputs) {
                                requestBuilder.addTarget(outputSurface);
                            }

                            // Finally, we start displaying the camera preview.
                            session.setRepeatingRequest(requestBuilder.build(), null, mBackgroundHandler);

                            // IGNORED.
                            return Observable.just(0);
                        }
                    })
                    .ofType(Object.class);
            }
        };
    }

    private ObservableTransformer<Object, ?> onImageAvailable() {
        return new ObservableTransformer<Object, Object>() {
            @Override
            public ObservableSource<Object> apply(Observable<Object> upstream) {
                return upstream
                    .ofType(OnImageAvailableEvent.class)
                    // Sample every N milliseconds because the following
                    // computation might be longer than N milliseconds.
                    // However sampling every 40 milliseconds is close to
                    // 24fps.
                    .sample(40, TimeUnit.MILLISECONDS)
                    .map(new Function<OnImageAvailableEvent, Object>() {
                        @Override
                        public Object apply(OnImageAvailableEvent event)
                            throws Exception {

                            ProfilerUtil.startProfiling();

                            // TODO: Convert YUV_420_888 to RGB or BGR.

                            Log.d("xyz", String.format("YUV_420_888 to RGB (took %.3f)",
                                                       ProfilerUtil.stopProfiling()));

                            return 0;
                        }
                    });
            }
        };
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param width   The minimum desired width
     * @param height  The minimum desired height
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private Size chooseOptimalSize(Size[] choices,
                                   int width,
                                   int height) {
        final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
        final Size desiredSize = new Size(width, height);

        // Collect the supported resolutions that are at least as big as the preview Surface
        boolean exactSizeFound = false;
        final List<Size> bigEnough = new ArrayList<>();
        final List<Size> tooSmall = new ArrayList<>();
        for (final Size option : choices) {
            if (option.equals(desiredSize)) {
                // Set the size but don't return yet so that remaining sizes will still be logged.
                exactSizeFound = true;
            }

            if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }

        Log.d("xyz", "Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
        Log.d("xyz", "Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
        Log.d("xyz", "Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

        if (exactSizeFound) {
            Log.d("xyz", "Exact size match found.");
            return desiredSize;
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, mCompareSizesByArea);
            Log.d("xyz", "Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            Log.d("xyz", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private Comparator<Size> mCompareSizesByArea = new Comparator<Size>() {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                               (long) rhs.getWidth() * rhs.getHeight());
        }
    };

    private String configureCamera(@NonNull CameraManager manager)
        throws CameraAccessException,
               NullPointerException {
        // Back camera.
        String cameraId = manager.getCameraIdList()[0];

        // Choose the sizes for camera preview and video recording
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            throw new RuntimeException("Cannot get available preview/video sizes");
        }
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        // TODO: Make it an util method.
        mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                                         mCameraView.getWidth(),
                                         mCameraView.getHeight());

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mCameraView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        } else {
            mCameraView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }

        configureTransform(mPreviewSize.getWidth(),
                           mPreviewSize.getHeight());

        return cameraId;
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mCameraView || null == mPreviewSize) {
            return;
        }

        // TODO: Cases for font and back camera.

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                (float) viewHeight / mPreviewSize.getHeight(),
                (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }

        mCameraView.setTransform(matrix);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////
}
