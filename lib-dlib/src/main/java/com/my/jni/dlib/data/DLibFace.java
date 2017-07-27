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

package com.my.jni.dlib.data;

import android.graphics.RectF;

import java.util.List;

public abstract class DLibFace {

    public abstract RectF getBound();

    public abstract List<Landmark> getAllLandmarks();

    public abstract List<Landmark> getLeftEyebrowLandmarks();

    public abstract List<Landmark> getRightEyebrowLandmarks();

    public abstract List<Landmark> getLeftEyeLandmarks();

    public abstract List<Landmark> getRightEyeLandmarks();

    public abstract List<Landmark> getNoseLandmarks();

    public abstract List<Landmark> getInnerLipsLandmarks();

    public abstract List<Landmark> getOuterLipsLandmarks();

    public abstract List<Landmark> getChinLandmarks();

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////

    public static class Landmark {

        public final float x;
        public final float y;

        public Landmark(float x,
                        float y) {
            this.x = x;
            this.y = y;
        }

        public Landmark(Messages.Landmark landmark) {
            this.x = landmark.getX();
            this.y = landmark.getY();
        }

        public Landmark(Landmark other) {
            this.x = other.x;
            this.y = other.y;
        }

        @Override
        public String toString() {
            return "Landmark{" +
                   "x=" + x +
                   ", y=" + y +
                   '}';
        }
    }
}
