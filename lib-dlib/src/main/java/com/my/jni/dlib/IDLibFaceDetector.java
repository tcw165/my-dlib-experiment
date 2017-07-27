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

package com.my.jni.dlib;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.protobuf.InvalidProtocolBufferException;
import com.my.jni.dlib.data.DLibFace;

import java.util.List;

public interface IDLibFaceDetector {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    boolean isFaceDetectorReady();

    boolean isFaceLandmarksDetectorReady();

    /**
     * Prepare (deserialize the graph) the face detector.
     */
    void prepareFaceDetector();

    /**
     * Prepare the face landmarks detector.
     *
     * @param path The model (serialized graph) file.
     */
    void prepareFaceLandmarksDetector(String path);

    /**
     * Detect face bounds.
     *
     * @param bitmap The given photo.
     * @return A list of {@link DLibFace}.
     *
     * @throws InvalidProtocolBufferException Fired if the message cannot be
     * recognized
     */
    List<DLibFace> findFaces(Bitmap bitmap)
        throws InvalidProtocolBufferException;

    /**
     * Detect the face landmarks in the given face bound (single face).
     *
     * @param bitmap The given photo.
     * @param bound The boundary of the face.
     * @return A list of {@link DLibFace.Landmark}.
     *
     * @throws InvalidProtocolBufferException Fired if the message cannot be
     * recognized
     */
    List<DLibFace.Landmark> findLandmarksFromFace(Bitmap bitmap,
                                                  Rect bound)
        throws InvalidProtocolBufferException;

    /**
     * Detect the face landmarks in the given face bounds (multiple faces).
     *
     * @param bitmap The given photo.
     * @param faceBounds The list of face boundary.
     * @return A list of {@link DLibFace.Landmark}.
     *
     * @throws InvalidProtocolBufferException Fired if the message cannot be
     * recognized
     */
    List<DLibFace> findLandmarksFromFaces(Bitmap bitmap,
                                          List<Rect> faceBounds)
        throws InvalidProtocolBufferException;

    /**
     * Detect face bounds and then detect the face landmarks for every face.
     *
     * @param bitmap The given photo.
     * @return A list of {@link DLibFace.Landmark}.
     *
     * @throws InvalidProtocolBufferException Fired if the message cannot be
     * recognized
     */
    List<DLibFace> findFacesAndLandmarks(Bitmap bitmap)
        throws InvalidProtocolBufferException;
}
