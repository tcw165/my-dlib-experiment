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

package com.my.demo.dlib.view;

import android.graphics.SurfaceTexture;
import android.view.TextureView;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.MainThreadDisposable;

public class TextureViewObservable extends Observable<Boolean> {

    // Given...
    private final TextureView mTarget;

    public TextureViewObservable(TextureView target) {
        mTarget = target;
    }

    @Override
    protected void subscribeActual(Observer<? super Boolean> observer) {
        final DisposableListener listener = new DisposableListener(mTarget, observer);

        mTarget.setSurfaceTextureListener(listener);
        if (mTarget.isAvailable()) {
            observer.onNext(true);
        }

        observer.onSubscribe(listener);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////

    private static class DisposableListener
        extends MainThreadDisposable
        implements TextureView.SurfaceTextureListener {

        // Given...
        private final TextureView mTarget;
        Observer<? super Boolean> mObserver;

        public DisposableListener(TextureView target,
                                  Observer<? super Boolean> observer) {
            mTarget = target;
            mObserver = observer;
        }

        @Override
        protected void onDispose() {
            mTarget.setSurfaceTextureListener(null);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture,
                                              int width,
                                              int height) {
            mObserver.onNext(true);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture,
                                                int width,
                                                int height) {
            // IGNORED.
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            mObserver.onNext(false);
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            // IGNORED.
        }
    }
}
