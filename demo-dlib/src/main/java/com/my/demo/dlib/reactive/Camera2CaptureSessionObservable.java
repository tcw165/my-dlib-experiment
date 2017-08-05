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

package com.my.demo.dlib.reactive;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.Surface;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public class Camera2CaptureSessionObservable extends Observable<Camera2CaptureSessionObservable.SessionBlob> {

    // Given...
    private final CameraDevice mCameraDevice;
    private final List<Surface> mOutputs;
    private final Handler mWorkerThreadHandler;

    public Camera2CaptureSessionObservable(CameraDevice cameraDevice,
                                           List<Surface> outputs,
                                           Handler workerThreadHandler) {
        mCameraDevice = cameraDevice;
        mOutputs = outputs;
        mWorkerThreadHandler = workerThreadHandler;
    }

    @Override
    protected void subscribeActual(Observer<? super SessionBlob> observer) {
        final DisposableListener listener = new DisposableListener(observer,
                                                                   mCameraDevice,
                                                                   mOutputs);

        try {
            mCameraDevice.createCaptureSession(mOutputs, listener, mWorkerThreadHandler);
            observer.onSubscribe(listener);
        } catch (SecurityException | CameraAccessException ex) {
            observer.onError(ex);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////

    public static class SessionBlob {
        public final CameraDevice cameraDevice;
        public final CameraCaptureSession captureSession;
        public final List<Surface> captureOutputs;

        SessionBlob(CameraDevice cameraDevice,
                    CameraCaptureSession captureSession,
                    List<Surface> captureOutputs) {
            this.cameraDevice = cameraDevice;
            this.captureSession = captureSession;
            this.captureOutputs = captureOutputs;
        }
    }

    private static class DisposableListener
        extends CameraCaptureSession.StateCallback
        implements Disposable {

        final Object mMutex = new Object();

        // Given...
        Observer<? super SessionBlob> mObserver;
        private final CameraDevice mCameraDevice;
        public final List<Surface> mCaptureOutputs;

        // State.
        final AtomicBoolean mUnsubscribed = new AtomicBoolean();

        // Resource.
        CameraCaptureSession mCaptureSession;

        DisposableListener(Observer<? super SessionBlob> observer,
                           CameraDevice cameraDevice,
                           List<Surface> captureOutputs) {
            mObserver = observer;
            mCameraDevice = cameraDevice;
            mCaptureOutputs = captureOutputs;
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (isDisposed()) return;

            mCaptureSession = session;

            mObserver.onNext(new SessionBlob(mCameraDevice, session, mCaptureOutputs));
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            session.close();
            mCaptureSession = null;

            if (!isDisposed()) {
                dispose();
                mObserver.onError(new RuntimeException("Fail to configure session."));
            }
        }

        @Override
        public void dispose() {
            if (mUnsubscribed.compareAndSet(false, true)) {
                synchronized (mMutex) {
                    if (mCaptureSession != null) {
                        mCaptureSession.close();
                    }
                    mCaptureSession = null;
                }
            }
        }

        @Override
        public boolean isDisposed() {
            return mUnsubscribed.get();
        }
    }
}
