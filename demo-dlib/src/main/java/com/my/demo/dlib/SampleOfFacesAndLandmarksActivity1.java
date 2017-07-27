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
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.my.core.protocol.IProgressBarView;
import com.my.demo.dlib.detector.DLibFaceAndLandmarksDetector;
import com.my.demo.dlib.protocol.ICameraMetadata;
import com.my.demo.dlib.util.DlibModelHelper;
import com.my.demo.dlib.view.CameraSourcePreview;
import com.my.demo.dlib.view.FaceLandmarksOverlayView;
import com.my.jni.dlib.DLibLandmarks68Detector;
import com.my.jni.dlib.IDLibFaceDetector;
import com.my.jni.dlib.data.DLibFace;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class SampleOfFacesAndLandmarksActivity1
    extends AppCompatActivity
    implements ICameraMetadata,
               IProgressBarView {

    // View.
    @BindView(R.id.camera)
    CameraSourcePreview mCameraView;
    @BindView(R.id.overlay)
    FaceLandmarksOverlayView mOverlayView;
    ProgressDialog mProgressDialog;

    // Butter Knife.
    Unbinder mUnbinder;

    // DLibFace Detector.
    IDLibFaceDetector mLandmarksDetector;

    // Data.
    CompositeDisposable mDisposables;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sample_of_faces_and_landmarks_1);

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

        // TODO: Check if the Google Play Service is present.
        mDisposables = new CompositeDisposable();
        mDisposables.add(
            grantPermission()
                // Show the progress-bar.
                .map(new Function<Boolean, Boolean>() {
                    @Override
                    public Boolean apply(Boolean granted) throws Exception {
                        showProgressBar("Preparing the model...");
                        return granted;
                    }
                })
                // DLib Face landmarks detection.
                .observeOn(Schedulers.io())
                .flatMap(new Function<Boolean, ObservableSource<?>>() {
                    @Override
                    public ObservableSource<?> apply(Boolean granted)
                        throws Exception {
                        if (granted) {
                            return initFaceLandmarksDetector();
                        } else {
                            return Observable.just(false);
                        }
                    }
                })
                // Open the camera.
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Function<Object, Object>() {
                    @Override
                    public Object apply(Object value) throws Exception {
                        final Detector<DLibFace> detector = new DLibFaceAndLandmarksDetector(
                            SampleOfFacesAndLandmarksActivity1.this,
                            mLandmarksDetector, mOverlayView);

                        // The camera preview is 90 degree clockwise rotated.
                        //  height
                        // .------.
                        // |      |
                        // |      | width
                        // |      |
                        // '------'
                        final int previewWidth = 320;
                        final int previewHeight = 240;

                        // Set the preview config.
                        if (isPortraitMode()) {
                            mOverlayView.setCameraPreviewSize(previewHeight,
                                                              previewWidth);
                        } else {
                            mOverlayView.setCameraPreviewSize(previewWidth,
                                                              previewHeight);
                        }

                        // Create camera source.
                        final CameraSource source = new CameraSource.Builder(getContext(), detector)
                            .setRequestedPreviewSize(previewWidth, previewHeight)
                            .setFacing(CameraSource.CAMERA_FACING_FRONT)
                            .setAutoFocusEnabled(true)
                            .setRequestedFps(30f)
                            .build();

                        // Open the camera.
                        mCameraView.start(source);

                        return value;
                    }
                })
                // Back to UI.
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    new Consumer<Object>() {
                        @Override
                        public void accept(Object o)
                            throws Exception {
                            // DO NOTHING.
                        }
                    },
                    new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable err)
                            throws Exception {
                            Log.e("xyz", err.getMessage());

                            hideProgressBar();

                            Toast.makeText(SampleOfFacesAndLandmarksActivity1.this,
                                           err.getMessage(), Toast.LENGTH_SHORT)
                                 .show();
                        }
                    },
                    new Action() {
                        @Override
                        public void run() throws Exception {
                            hideProgressBar();
                        }
                    }));
    }

    @Override
    protected void onPause() {
        super.onPause();

        hideProgressBar();

        // Close camera.
        mCameraView.release();

        mDisposables.clear();
    }

    @Override
    public boolean isFacingFront() {
        return CameraSource.CAMERA_FACING_FRONT ==
               mCameraView.getCameraSource()
                          .getCameraFacing();
    }

    @Override
    public boolean isFacingBack() {
        return CameraSource.CAMERA_FACING_BACK ==
               mCameraView.getCameraSource()
                          .getCameraFacing();
    }

    @Override
    public boolean isPortraitMode() {
        int orientation = getResources().getConfiguration().orientation;
        return orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean isLandscapeMode() {
        int orientation = getResources().getConfiguration().orientation;
        return orientation == Configuration.ORIENTATION_LANDSCAPE;
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

    private Observable<Boolean> grantPermission() {
        return new RxPermissions(this)
            .request(Manifest.permission.READ_EXTERNAL_STORAGE,
                     Manifest.permission.WRITE_EXTERNAL_STORAGE,
                     Manifest.permission.ACCESS_NETWORK_STATE,
                     Manifest.permission.CAMERA);
    }

    private Observable<?> initFaceLandmarksDetector() {
        return DlibModelHelper
            .getService()
            // Download the trained model.
            .downloadFace68Model(
                this,
                getApplicationContext().getPackageName())
            // Update progressbar message.
            .observeOn(AndroidSchedulers.mainThread())
            .map(new Function<File, File>() {
                @Override
                public File apply(File face68ModelPath) throws Exception {
                    showProgressBar("Initializing face detectors...");
                    return face68ModelPath;
                }
            })
            // Deserialize the detector.
            .observeOn(Schedulers.io())
            .map(new Function<File, Boolean>() {
                @Override
                public Boolean apply(File face68ModelPath)
                    throws Exception {
                    if (face68ModelPath == null || !face68ModelPath.exists()) {
                        throw new RuntimeException(
                            "The face68 model is invalid.");
                    }

                    if (!mLandmarksDetector.isFaceDetectorReady()) {
                        mLandmarksDetector.prepareFaceDetector();
                    }
                    if (!mLandmarksDetector.isFaceLandmarksDetectorReady()) {
                        mLandmarksDetector.prepareFaceLandmarksDetector(
                            face68ModelPath.getAbsolutePath());
                    }

                    return true;
                }
            });
    }

    private Context getContext() {
        return this;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////
}
