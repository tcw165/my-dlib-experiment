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
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.my.core.protocol.IProgressBarView;
import com.my.demo.dlib.protocol.ICameraMetadata;
import com.my.demo.dlib.view.AutoFitTextureView;
import com.my.demo.dlib.view.FaceLandmarksOverlayView;
import com.my.demo.dlib.view.TextureViewObservable;
import com.my.jni.dlib.DLibLandmarks68Detector;
import com.my.jni.dlib.IDLibFaceDetector;
import com.tbruyelle.rxpermissions2.RxPermissions;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public class SampleOfFacesAndLandmarksActivity3
    extends AppCompatActivity
    implements ICameraMetadata,
               IProgressBarView {

    // View.
    @BindView(R.id.texture)
    AutoFitTextureView mCameraView;
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

        // TODO: Check if the Google Play Service is present.
        mDisposables = new CompositeDisposable();
        mDisposables.add(
            grantPermission()
                .flatMap(new Function<Boolean, ObservableSource<Boolean>>() {
                    @Override
                    public ObservableSource<Boolean> apply(Boolean granted)
                        throws Exception {
                        if (granted) {
                            return new TextureViewObservable(mCameraView);
                        } else {
                            throw new IllegalStateException("Permission is not granted.");
                        }
                    }
                })
//                .onErrorReturn(new Function<Throwable, UiModel>() {
//                    @Override
//                    public UiModel apply(Throwable throwable) throws Exception {
//                        return UiModel.failed(throwable);
//                    }
//                })
                .ofType(Object.class)
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object anything)
                        throws Exception {
                        Log.d("xyz", anything.toString());
                    }
                }));
    }

    @Override
    protected void onPause() {
        super.onPause();

        hideProgressBar();

        mDisposables.clear();
    }

    @Override
    public boolean isFacingFront() {
        return false;
    }

    @Override
    public boolean isFacingBack() {
        return false;
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

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////
}
