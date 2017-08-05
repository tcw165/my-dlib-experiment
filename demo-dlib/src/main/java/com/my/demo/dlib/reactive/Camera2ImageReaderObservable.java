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

import android.media.ImageReader;
import android.os.Handler;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public class Camera2ImageReaderObservable extends Observable<Camera2ImageReaderObservable.OnImageAvailableEvent> {

    // Given...
    private final ImageReader mImageReader;
    private final Handler mWorkerThreadHandler;

    public Camera2ImageReaderObservable(ImageReader reader,
                                        Handler workerThreadHandler) {
        mImageReader = reader;
        mWorkerThreadHandler = workerThreadHandler;
    }

    @Override
    protected void subscribeActual(Observer<? super OnImageAvailableEvent> observer) {
        final DisposableListener listener = new DisposableListener(observer,
                                                                   mImageReader);

        mImageReader.setOnImageAvailableListener(listener, mWorkerThreadHandler);
        observer.onSubscribe(listener);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////

    public static class OnImageAvailableEvent {
        public final ImageReader reader;

        OnImageAvailableEvent(ImageReader reader) {
            this.reader = reader;
        }
    }

    private static class DisposableListener
        implements Disposable,
                   ImageReader.OnImageAvailableListener {

        // Given...
        Observer<? super OnImageAvailableEvent> mObserver;
        private final ImageReader mImageReader;

        // State.
        final AtomicBoolean mUnsubscribed = new AtomicBoolean();

        DisposableListener(Observer<? super OnImageAvailableEvent> observer,
                           ImageReader reader) {
            mObserver = observer;
            mImageReader = reader;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            if (isDisposed()) return;

            mObserver.onNext(new OnImageAvailableEvent(reader));
        }

        @Override
        public void dispose() {
            if (mUnsubscribed.compareAndSet(false, true)) {
                mImageReader.setOnImageAvailableListener(null, null);
                mImageReader.close();
            }
        }

        @Override
        public boolean isDisposed() {
            return mUnsubscribed.get();
        }
    }
}
