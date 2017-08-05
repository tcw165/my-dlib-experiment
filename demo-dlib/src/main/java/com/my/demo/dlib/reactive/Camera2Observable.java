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
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.support.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public class Camera2Observable extends Observable<CameraDevice> {

    // Given...
    private final CameraManager mCameraManager;
    private final String mCameraId;
    private final Handler mWorkerThreadHandler;

    public Camera2Observable(CameraManager cameraManager,
                             String cameraId,
                             Handler workerThreadHandler) {
        mCameraManager = cameraManager;
        mCameraId = cameraId;
        mWorkerThreadHandler = workerThreadHandler;
    }

    @Override
    protected void subscribeActual(Observer<? super CameraDevice> observer) {
        final DisposableListener listener = new DisposableListener(observer);

        try {
            mCameraManager.openCamera(mCameraId, listener, mWorkerThreadHandler);
            observer.onSubscribe(listener);
        } catch (SecurityException | CameraAccessException ex) {
            observer.onError(ex);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////

    private static class DisposableListener
        extends CameraDevice.StateCallback
        implements Disposable {

        final Object mMutex = new Object();

        // Given...
        Observer<? super CameraDevice> mObserver;

        // State.
        final AtomicBoolean mUnsubscribed = new AtomicBoolean();

        // Resource.
        CameraDevice mCameraDevice;

        DisposableListener(Observer<? super CameraDevice> observer) {
            mObserver = observer;
        }

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            synchronized (mMutex) {
                mCameraDevice = cameraDevice;

                mObserver.onNext(cameraDevice);
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            synchronized (mMutex) {
                cameraDevice.close();
                dispose();

                mObserver.onError(new RuntimeException(
                    "The camera is disconnected."));
            }
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice,
                            int i) {
            synchronized (mMutex) {
                cameraDevice.close();
                dispose();

                mObserver.onError(new RuntimeException(
                    "The camera error."));
            }
        }

        @Override
        public void dispose() {
            if (mUnsubscribed.compareAndSet(false, true)) {
                synchronized (mMutex) {
                    if (mCameraDevice != null) {
                        mCameraDevice.close();
                    }
                    mCameraDevice = null;
                }
            }
        }

        @Override
        public boolean isDisposed() {
            return mUnsubscribed.get();
        }
    }
}
