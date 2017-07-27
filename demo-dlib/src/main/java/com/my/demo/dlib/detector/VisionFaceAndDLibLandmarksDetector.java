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
 * A detector using Google Vision face detector and DLib landmarks detector.
 */
public class VisionFaceAndDLibLandmarksDetector extends Detector<DLibFace> {

    // State.
    private final SparseArray<DLibFace> mDetFaces = new SparseArray<>();

    private final ICameraMetadata mCameraMetadata;
    private final Detector<Face> mFaceDetector;
    private final IDLibFaceDetector mLandmarksDetector;

    public VisionFaceAndDLibLandmarksDetector(final ICameraMetadata cameraMetadata,
                                              final Detector<Face> faceDetector,
                                              final IDLibFaceDetector landmarksDetector,
                                              final IDLibFaceOverlay overlay) {
        mCameraMetadata = cameraMetadata;
        mFaceDetector = faceDetector;
        mLandmarksDetector = landmarksDetector;

        setProcessor(new PostProcessor(overlay));
    }

    @Override
    public SparseArray<DLibFace> detect(Frame frame) {
        if (mCameraMetadata == null ||
            mFaceDetector == null ||
            mLandmarksDetector == null) {
            throw new IllegalStateException(
                "Invalid detector.");
        }

        mDetFaces.clear();

        ProfilerUtil.startProfiling();

        // Use Google Vision face detector to get face bounds.
        ProfilerUtil.startProfiling();
        final SparseArray<Face> faces = mFaceDetector.detect(frame);
        Log.d("xyz", String.format("Detect %d faces (took %.3f ms)",
                                   faces.size(),
                                   ProfilerUtil.stopProfiling()));
        if (faces.size() == 0) return mDetFaces;

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

        // Translate the face bounds into something that DLib detector knows.
        final List<Rect> faceBounds = new ArrayList<>();
        for (int i = 0; i < faces.size(); ++i) {
            final Face face = faces.get(faces.keyAt(i));

            final float x;
            if (mCameraMetadata.isFacingFront()) {
                // The facing-front preview is horizontally mirrored and it's
                // no harm for the algorithm to find the face bound, but it's
                // critical for the algorithm to align the landmarks. I need
                // to mirror it again.
                //
                // For example:
                //
                // <-------------+ (1) The mirrored coordinate.
                // +-------------> (2) The not-mirrored coordinate.
                // |       |-----| This is x in the (1) system.
                // |   |---|       This is w in both (1) and (2) systems.
                // |   ?           This is what I want in the (2) system.
                // |   .---.
                // |   | F |
                // |   '---'
                // |
                // v
                x = ow - face.getPosition().x - face.getWidth();
            } else {
                x = face.getPosition().x;
            }
            final float y = face.getPosition().y;
            final float w = face.getWidth();
            final float h = face.getHeight();
            final Rect bound = new Rect((int) (x),
                                        (int) (y),
                                        (int) (x + w),
                                        (int) (y + h));

            // The face bound that DLib landmarks algorithm needs is slightly
            // different from the one given by Google Vision API, so I change
            // it a bit from the experience of try-and-error.
            bound.inset(bound.width() / 10,
                        bound.height() / 6);
            bound.offset(0, bound.height() / 4);

//            Log.d("xyz", String.format("#%d face x=%f, y=%f, w=%f, h=%f",
//                                       faces.keyAt(i),
//                                       face.getPosition().x,
//                                       face.getPosition().y,
//                                       face.getWidth(),
//                                       face.getHeight()));
            faceBounds.add(bound);
        }


        // Detect landmarks.
        try {
            ProfilerUtil.startProfiling();
            List<DLibFace> detFaces = mLandmarksDetector.findLandmarksFromFaces(
                bitmap,
                faceBounds);
            mDetFaces.clear();
            for (int i = 0; i < detFaces.size(); ++i) {
                mDetFaces.put(i, detFaces.get(i));
            }
            Log.d("xyz", String.format("Detect %d face with landmarks (took %.3f ms)",
                                       detFaces.size(),
                                       ProfilerUtil.stopProfiling()));
//            Log.d("xyz", String.format("input rect=%s, output rect=%s",
//                                       new RectF((float) faceBounds.get(0).left / ow,
//                                                 (float) faceBounds.get(0).top / oh,
//                                                 (float) faceBounds.get(0).right / ow,
//                                                 (float) faceBounds.get(0).bottom / oh),
//                                       detFaces.get(0).getBound()));

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

    private static class PostProcessor implements Detector.Processor<DLibFace> {

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
