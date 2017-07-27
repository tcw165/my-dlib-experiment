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

package com.my.demo.dlib.util;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.my.core.util.FileUtil;
import com.my.core.util.PrefUtil;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

public class DlibModelHelper {

    private static final String BASE_URL = "http://dlib.net/files/";
    private static final String FACE68_ZIP_FILE = "shape_predictor_68_face_landmarks.dat.bz2";
    private static final String FACE68_FILE = "shape_predictor_68_face_landmarks.dat";

    private static final String PREF_KEY_FACE68_ZIP = FACE68_ZIP_FILE;

    private static DlibModelHelper sSingleton = null;

    public static DlibModelHelper getService() {
        if (sSingleton == null) {
            synchronized (DlibModelHelper.class) {
                if (sSingleton == null) {
                    sSingleton = new DlibModelHelper();
                }
            }
        }

        return sSingleton;
    }

    public Observable<File> downloadFace68Model(final Context context,
                                                final String dirName) {
        // Get DownloadManager
        final DownloadManager downloadManager = (DownloadManager)
            context.getSystemService(Context.DOWNLOAD_SERVICE);
        // Get the task ID from shared-preference.
        long downloadId = PrefUtil.getLong(context, PREF_KEY_FACE68_ZIP);
        // Create a subject for later emitting the download status.
        final PublishSubject<File> subject = PublishSubject.create();

        if (downloadId == -1) {
            // Create a new download request.
            downloadId = downloadManager.enqueue(getFace68Request(dirName));

            // Set the task in the share-preference.
            Log.d("xyz", "Set new downloadId=" + downloadId);
            PrefUtil.setLong(context, PREF_KEY_FACE68_ZIP, downloadId);
        }

        // Register a new download broadcast receiver.
        final DownloadStatusReceiver receiver = new DownloadStatusReceiver(downloadId, subject);
        IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        context.registerReceiver(receiver, intentFilter);
        Log.d("xyz", "Register the download broadcast receiver.");

        // Create a cold observable subscribing to the download subject.
        final Observable<File> ob = subject
            // Unregister the broadcast receiver once the stream chain is
            // disposed.
            .doOnDispose(new Action() {
                @Override
                public void run() throws Exception {
                    // Unregister the download broadcast receiver.
                    context.unregisterReceiver(receiver);
                    Log.d("xyz", "Unregister the download broadcast receiver.");
                }
            })
            .doOnComplete(new Action() {
                @Override
                public void run() throws Exception {
                    // Unregister the download broadcast receiver.
                    context.unregisterReceiver(receiver);
                    Log.d("xyz", "Unregister the download broadcast receiver.");
                }
            })
            .doOnError(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable)
                    throws Exception {
                    // Set the task in the share-preference.
                    Log.d("xyz", "Unset download task ID.");
                    PrefUtil.setLong(context, PREF_KEY_FACE68_ZIP, -1);
                }
            })
            // Unpack the bz2 file.
            // TODO: Run the unpacking in a IntentService so that the process
            // TODO: continues when app enters background.
            .observeOn(Schedulers.io())
            .map(new Function<File, File>() {
                @Override
                public File apply(File downloadFile)
                    throws Exception {
                    final File unpackFile = new File(downloadFile.getParent(), FACE68_FILE);

                    if (!unpackFile.exists()) {
                        Log.d("xyz", "Unpacking the archived model...");
                        final BZip2CompressorInputStream bzIs =
                            new BZip2CompressorInputStream(
                                new BufferedInputStream(
                                    new FileInputStream(downloadFile)));
                        FileUtil.copy(bzIs, new FileOutputStream(unpackFile));
                        Log.d("xyz", "Unpacking the archived model... done");

                        // Register the unpacked file to DownloadManager.
                        downloadManager.addCompletedDownload(
                            unpackFile.getName(), unpackFile.getName(), true, "*/*",
                            unpackFile.getAbsolutePath(), unpackFile.length(), true);
                    }

                    return unpackFile;
                }
            });

        if (downloadId != -1) {
            // Check the download task if the task ID is present:
            // Case 1. The download is completed already. Calling this function
            //         make the subject emit the result.
            // Case 2. The download is yet completed. However, I register a
            //         broadcast receiver. Calling this function won't harm.
            checkDownloadTask(downloadManager, downloadId, subject);
        }

        return ob;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected / Private Methods ////////////////////////////////////////////

    private DlibModelHelper() {
        // DO NOTHING.
    }

    private static void checkDownloadTask(final DownloadManager downloadManager,
                                          final long taskId,
                                          final Subject<File> subject) {
        if (downloadManager == null || subject == null) return;

        Observable
            .fromCallable(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    // The task cursor.
                    final Cursor cursor = downloadManager.query(
                        new DownloadManager.Query().setFilterById(taskId));

                    if (cursor != null && cursor.moveToFirst()) {
                        try {
                            final int statusCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS);
                            final int uriCol = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI);

                            if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusCol)) {
                                final File file = new File(Uri.parse(cursor.getString(uriCol))
                                                              .getPath());
                                Log.d("xyz", "" + file + " is downloaded.");
                                subject.onNext(file);
                                subject.onComplete();
                            }
                        } catch (Throwable err) {
                            // Remove the task from DownloadManager.
                            downloadManager.remove(taskId);

                            subject.onError(err);
                        } finally {
                            cursor.close();
                        }
                    } else {
                        // Remove the task from DownloadManager.
                        downloadManager.remove(taskId);

                        subject.onError(new RuntimeException("Empty cursor."));
                    }

                    return true;
                }
            })
            .subscribeOn(Schedulers.io())
            // Capture any error where the callable block doesn't catch.
            .doOnError(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable err)
                    throws Exception {
                    subject.onError(err);
                }
            })
            .subscribe();
    }

    /**
     * Get {@link DownloadManager.Request} for getting face68 model.
     */
    private DownloadManager.Request getFace68Request(final String dirName) {
        return new DownloadManager.Request(Uri.parse(BASE_URL + FACE68_ZIP_FILE))
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                                               dirName + File.separator + FACE68_ZIP_FILE)
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE |
                                    DownloadManager.Request.NETWORK_WIFI)
            .setTitle("Downloading " + FACE68_ZIP_FILE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setVisibleInDownloadsUi(true);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////

    // TODO: Refactor this in case of that too many services are registered.
    private static class DownloadStatusReceiver extends BroadcastReceiver {

        private final long mTaskId;
        private final Subject<File> mSubject;

        public DownloadStatusReceiver(final long taskId,
                                      final Subject<File> subject) {
            mTaskId = taskId;
            mSubject = subject;
        }

        @Override
        public void onReceive(Context context,
                              Intent intent) {
            final DownloadManager downloadManager = (DownloadManager)
                context.getSystemService(Context.DOWNLOAD_SERVICE);
            // The task ID.
            final long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
            if (mTaskId != id) return;

            DlibModelHelper.checkDownloadTask(downloadManager, id, mSubject);
        }
    }
}
