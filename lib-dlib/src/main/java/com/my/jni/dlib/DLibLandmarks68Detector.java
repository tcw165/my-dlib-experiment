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
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;
import com.my.jni.dlib.data.DLibFace;
import com.my.jni.dlib.data.DLibFace68;
import com.my.jni.dlib.data.Messages;

import java.util.ArrayList;
import java.util.List;

public class DLibLandmarks68Detector implements IDLibFaceDetector {

    private boolean mIsEnabled = true;

    public DLibLandmarks68Detector() {
        // TODO: Load library in worker thread?
        try {
            System.loadLibrary("c++_shared");
            Log.d("jni", "libc++_shared.so is loaded");
        } catch (UnsatisfiedLinkError error) {
            throw new RuntimeException(
                "\"c++_shared\" not found; check that the correct native " +
                "libraries are present in the APK.");
        }

        // TODO: Load library in worker thread?
        try {
            System.loadLibrary("protobuf-lite-3.2.0");
            Log.d("jni", "libprotobuf-lite-3.2.0.so is loaded");
        } catch (UnsatisfiedLinkError error) {
            throw new RuntimeException(
                "\"protobuf-lite-3.2.0\" not found; check that the correct " +
                "native libraries are present in the APK.");
        }

        // TODO: Load library in worker thread?
        try {
            System.loadLibrary("dlib");
            Log.d("jni", "libdlib.so is loaded");
        } catch (UnsatisfiedLinkError error) {
            throw new RuntimeException(
                "\"dlib\" not found; check that the correct native libraries " +
                "are present in the APK.");
        }

        // TODO: Load library in worker thread?
        try {
            System.loadLibrary("dlib_jni");
            Log.d("jni", "libdlib_jni.so is loaded");
        } catch (UnsatisfiedLinkError error) {
            throw new RuntimeException(
                "\"dlib_jni\" not found; check that the correct native " +
                "libraries are present in the APK.");
        }
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
    }

    @Override
    public native boolean isFaceDetectorReady();

    @Override
    public native boolean isFaceLandmarksDetectorReady();

    @Override
    public native void prepareFaceDetector();

    @Override
    public native void prepareFaceLandmarksDetector(String path);

    @Override
    public List<DLibFace> findFaces(Bitmap bitmap)
        throws InvalidProtocolBufferException {

        // Call detector JNI.
        final byte[] rawData = detectFaces(bitmap);
        final Messages.FaceList rawFaces = Messages.FaceList.parseFrom(rawData);

        // Convert raw data to my data structure.
        final List<DLibFace> faces = new ArrayList<>();
        for (int i = 0; i < rawFaces.getFacesCount(); ++i) {
            final Messages.Face rawFace = rawFaces.getFaces(i);
            final DLibFace face  = new DLibFace68(rawFace);

            faces.add(face);
        }

        return faces;
    }

    @Override
    public List<DLibFace.Landmark> findLandmarksFromFace(Bitmap bitmap,
                                                         Rect bound)
        throws InvalidProtocolBufferException {
        // Call detector JNI.
        final byte[] rawData = detectLandmarksFromFace(
            bitmap, bound.left, bound.top, bound.right, bound.bottom);
        final Messages.LandmarkList rawLandmarks = Messages.LandmarkList.parseFrom(rawData);
        Log.d("xyz", "Detect " + rawLandmarks.getLandmarksCount() +
                     " landmarks in the face");

        // Convert raw data to my data structure.
        final List<DLibFace.Landmark> landmarks = new ArrayList<>();
        for (int i = 0; i < rawLandmarks.getLandmarksCount(); ++i) {
            final Messages.Landmark rawLandmark = rawLandmarks.getLandmarks(i);
            final DLibFace.Landmark landmark  = new DLibFace.Landmark(rawLandmark);

            landmarks.add(landmark);
        }

        return landmarks;
    }

    @Override
    public List<DLibFace> findLandmarksFromFaces(Bitmap bitmap,
                                                 List<Rect> faceBounds)
        throws InvalidProtocolBufferException {

        // Convert face bounds to protobuf message.
        final List<Messages.RectF> msgBounds = new ArrayList<>();
        for (Rect bound : faceBounds) {
            msgBounds.add(Messages.RectF
                              .newBuilder()
                              .setLeft(bound.left)
                              .setTop(bound.top)
                              .setRight(bound.right)
                              .setBottom(bound.bottom)
                              .build());
        }

        // Detect landmarks.
        final byte[] rawData = detectLandmarksFromFaces(
            bitmap,
            Messages.RectFList
                .newBuilder()
                .addAllRects(msgBounds)
                .build()
                .toByteArray());

        // Convert the returned message to our structure.
        final Messages.FaceList msgFaces = Messages.FaceList.parseFrom(rawData);
        final List<DLibFace> faces = new ArrayList<>();
        for (int i = 0; i < msgFaces.getFacesCount(); ++i) {
            faces.add(new DLibFace68(msgFaces.getFaces(i)));
        }

        return faces;
    }

    @Override
    public List<DLibFace> findFacesAndLandmarks(Bitmap bitmap)
        throws InvalidProtocolBufferException {
        // Do the face landmarks detection.
        final byte[] rawData = detectFacesAndLandmarks(bitmap);
        final Messages.FaceList rawFaces = Messages.FaceList.parseFrom(rawData);

        // Convert raw data to my data structure.
        final List<DLibFace> faces = new ArrayList<>();
        for (int i = 0; i < rawFaces.getFacesCount(); ++i) {
            final Messages.Face rawFace = rawFaces.getFaces(i);
            final DLibFace face = new DLibFace68(rawFace);

            faces.add(face);
        }

        return faces;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected / Private Methods ////////////////////////////////////////////

    /**
     * Detect all the faces from the given photo.
     *
     * @param bitmap The photo.
     * @return The byte array of serialized {@link List< DLibFace >}.
     */
    private native byte[] detectFaces(Bitmap bitmap);

    /**
     * Detect landmarks for one face.
     *
     * @param bitmap The small bitmap right covering a face.
     * @return The byte array of serialized {@link DLibFace}.
     */
    private native byte[] detectLandmarksFromFace(Bitmap bitmap,
                                                  long left,
                                                  long top,
                                                  long right,
                                                  long bottom);

    /**
     * Detect landmarks for the given faces.
     *
     * @param bitmap The small bitmap right covering a face.
     * @return The byte array of serialized {@link DLibFace}.
     */
    private native byte[] detectLandmarksFromFaces(Bitmap bitmap,
                                                   byte[] faceBounds);

    /**
     * Find the faces and landmarks from the given Bitmap.
     * <br/>
     * Before calling this method, make sure the face and landmarks detectors
     * are both initialized. Otherwise a {@link RuntimeException} would be fired.
     *
     * @param bitmap The bitmap.
     * @return The byte array of serialized {@link List< DLibFace >}.
     */
    private native byte[] detectFacesAndLandmarks(Bitmap bitmap);
}
