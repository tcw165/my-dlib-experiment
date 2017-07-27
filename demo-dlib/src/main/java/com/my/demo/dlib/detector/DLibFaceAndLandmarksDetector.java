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

package com.my.demo.dlib.detector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.protobuf.InvalidProtocolBufferException;
import com.my.core.util.ProfilerUtil;
import com.my.demo.dlib.protocol.ICameraMetadata;
import com.my.demo.dlib.protocol.IDLibFaceOverlay;
import com.my.jni.dlib.IDLibFaceDetector;
import com.my.jni.dlib.data.DLibFace;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A detector using DLib face detector and DLib landmarks detector.
 */
public class DLibFaceAndLandmarksDetector extends Detector<DLibFace> {

    // State.
    private final SparseArray<DLibFace> mDetFaces = new SparseArray<>();

    private final ICameraMetadata mCameraMetadata;
    private final IDLibFaceDetector mFaceDetector;

    public DLibFaceAndLandmarksDetector(final ICameraMetadata cameraMetadata,
                                        final IDLibFaceDetector faceDetector,
                                        final IDLibFaceOverlay overlay) {
        mCameraMetadata = cameraMetadata;
        mFaceDetector = faceDetector;

        setProcessor(new PostProcessor(overlay));
    }

    @Override
    public SparseArray<DLibFace> detect(Frame frame) {
        if (mCameraMetadata == null ||
            mFaceDetector == null) {
            throw new IllegalStateException(
                "Invalid detector.");
        }

        mDetFaces.clear();

        ProfilerUtil.startProfiling();

        // Camera preview dimension.
        final int fw = frame.getMetadata().getWidth();
        final int fh = frame.getMetadata().getHeight();
        // Overlay preview dimension.
        final int ow = getUprightPreviewWidth(frame);
        final int oh = getUprightPreviewHeight(frame);
        final Matrix transform = getCameraToViewTransform(frame);

        Log.d("xyz", String.format("frame (w=%d, h=%d), preview (w=%d, h=%d)",
                                   fw, fh,
                                   ow, oh));

        // Get bitmap from YUV frame.
        ProfilerUtil.startProfiling();
        final Bitmap bitmap = getBitmapFromFrame(frame, transform);
        Log.d("xyz", String.format("Extract bitmap (w=%d, h=%d) from YUV frame (took %.3f ms)",
                                   bitmap.getWidth(), bitmap.getHeight(),
                                   ProfilerUtil.stopProfiling()));

        // Detect faces and landmarks.
        try {
            ProfilerUtil.startProfiling();
            List<DLibFace> detFaces = mFaceDetector.findFacesAndLandmarks(bitmap);
            mDetFaces.clear();
            for (int i = 0; i < detFaces.size(); ++i) {
                mDetFaces.put(i, detFaces.get(i));
            }
            Log.d("xyz", String.format("Detect %d face with landmarks (took %.3f ms)",
                                       detFaces.size(),
                                       ProfilerUtil.stopProfiling()));

            Log.d("xyz", String.format(
                "Process of detecting faces and landmarks done (took %.3f ms)",
                ProfilerUtil.stopProfiling()));

            return mDetFaces;
        } catch (InvalidProtocolBufferException err) {
            err.printStackTrace();
            return null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected / Private Methods ////////////////////////////////////////////

    // TODO: This method could be an util method.
    // TODO: Rotation and facing are important parameters.
    private Matrix getCameraToViewTransform(final Frame frame) {
        final Matrix transform = new Matrix();
        switch (frame.getMetadata().getRotation()) {
            case Frame.ROTATION_90:
                transform.postRotate(90);
                break;
            case Frame.ROTATION_180:
                transform.postRotate(180);
                break;
            case Frame.ROTATION_270:
                transform.postRotate(270);
                break;
        }

        if (mCameraMetadata.isFacingFront()) {
            transform.postScale(-1, 1);
        }

        return transform;
    }

    private Bitmap getBitmapFromFrame(final Frame frame,
                                      final Matrix transform) {
        if (frame.getBitmap() != null) {
            return frame.getBitmap();
        } else {
            final int width = frame.getMetadata().getWidth();
            final int height = frame.getMetadata().getHeight();
            final YuvImage yuvImage = new YuvImage(
                frame.getGrayscaleImageData().array(),
                ImageFormat.NV21, width, height, null);

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 50, outputStream);

            final byte[] jpegArray = outputStream.toByteArray();
            final Bitmap rawBitmap = BitmapFactory.decodeByteArray(
                jpegArray, 0, jpegArray.length);

            final int bw = rawBitmap.getWidth();
            final int bh = rawBitmap.getHeight();

            return Bitmap.createBitmap(rawBitmap,
                                       0, 0, bw, bh,
                                       transform, false);
        }
    }

    private int getUprightPreviewWidth(Frame frame) {
        if (mCameraMetadata.isPortraitMode()) {
            return frame.getMetadata().getHeight();
        } else {
            return frame.getMetadata().getWidth();
        }
    }

    private int getUprightPreviewHeight(Frame frame) {
        if (mCameraMetadata.isPortraitMode()) {
            return frame.getMetadata().getWidth();
        } else {
            return frame.getMetadata().getHeight();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////

    private static class PostProcessor implements Processor<DLibFace> {

        final IDLibFaceOverlay mOverlay;

        // Data.
        final List<DLibFace> mFaces = new ArrayList<>();

        PostProcessor(IDLibFaceOverlay overlay) {
            mOverlay = overlay;
        }

        @Override
        public void release() {
            // DO NOTHING.
        }

        @Override
        public void receiveDetections(Detections<DLibFace> detections) {
            mFaces.clear();
            if (detections == null) {
                mOverlay.setFaces(mFaces);
                return;
            }

            final SparseArray<DLibFace> faces = detections.getDetectedItems();
            if (faces == null) return;

            for (int i = 0; i < faces.size(); ++i) {
                mFaces.add(faces.get(faces.keyAt(i)));
            }

            Log.d("xyz", String.format("Ready to render %d faces", faces.size()));
            mOverlay.setFaces(mFaces);
        }
    }
}
