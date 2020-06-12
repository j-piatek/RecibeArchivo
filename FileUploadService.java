/*
 *  This file is part of AntMonitor <https://athinagroup.eng.uci.edu/projects/antmonitor/>.
 *  Copyright (C) 2018 Anastasia Shuba and the UCI Networking Group
 *  <https://athinagroup.eng.uci.edu>, University of California, Irvine.
 *
 *  AntMonitor is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, version 2 of the License.
 *
 *  AntMonitor is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with AntMonitor. If not, see <http://www.gnu.org/licenses/>.
 */
package edu.uci.calit2.anteater.client.android.analysis;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.uci.calit2.anteater.R;
import edu.uci.calit2.anteater.client.android.activity.AntMonitorMainActivity;

/**
 * NOTE: This service is disabled in the open source version of the code. Adjust it as needed to
 * upload data to your own servers.
 *
 * Service for uploading network traffic log files to the remote data store.
 * Uploads are handled on a single worker thread in FIFO manner (see {@link android.app.IntentService} documentation).
 * Due to the fact that this Service is implemented as an intent service, we create a new connection for each file to upload.
 * For each request, a "Connection : close" header is added to effectively ensure that each connection is closed, as it is not going to be re-used.
 *
 * @author Simon Langhoff, Janus Varmarken, Anastasia Shuba
 */
public class FileUploadService extends IntentService {

    /** The address (including port) of the server where traffic log files are to be uploaded. */
    //private static final String SERVER_ADDRESS = "http://192.168.1.133:82/RecibeArchivo/recibePcapng.php";
    private static final String SERVER_ADDRESS = "https://recibir-archivo.appspot.com/RecibirArchivoServlet";
    public static final String SERVER_SECRET = "";
    private static int serverResponseCode = 0;


    /**
     * Tag used for analysis.
     */
    private static final String TAG = FileUploadService.class.getSimpleName();

    /**
     * Any {@link android.content.Intent} for starting a {@code FileUploadService} is supposed to contain a string value for the key specified by this string.
     * The value is the full path (including file name) to the file that is to be uploaded by the {@code FileUploadService}.
     */
    public static final String EXTRA_FILE = "EXTRA_FILE";

    private final FileUploadServiceBinder mBinder = new FileUploadServiceBinder();

    /**
     * Maps a fully qualified file name to an upload status object.
     */
    private final ConcurrentHashMap<String, FileUploadStatus> mStatusMap = new ConcurrentHashMap<String, FileUploadStatus>();

    /**
     * ID for upload status notification.
     * This allows this service to bundle status for all pending and done uploads within a single
     * notification.
     */
    private final int mNotificationId = 42424242;

    /**
     * Handler tied to main thread.
     */
    private Handler mHandler;

    /**
     * Creates a {@link FileUploadService} for uploading network traffic files to the remote data store.
     */
    public FileUploadService() {
        super(FileUploadService.class.getSimpleName());
        setIntentRedelivery(false);
    }

    @Override
    public void onCreate() {
        // Must call super in order to let IntentService manage its list of jobs and its worker threead.
        super.onCreate();
        // Create handler tied to main thread.
        // (onCreate runs on the main thread)
        mHandler = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // get the fully qualified name of the file that is to be uploaded.
        String filePath = intent.getStringExtra(EXTRA_FILE);
        if (filePath == null) {
            throw new NullPointerException("File name must be specified using the " + EXTRA_FILE + " extra.");
        }

        FileUploadStatus status = new FileUploadStatus(filePath);

        // Only create a new entry if no previous upload task scheduled for this file.
        if (mStatusMap.putIfAbsent(filePath, status) == null) {
            // There was no upload task present for this file.
            // Update notification to reflect the increased workload.
            updateNotification();
        }

        // Must call super in order to let IntentService manage its list of jobs and its worker threead.
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class FileUploadServiceBinder extends Binder {

        public void testStuff() {

        }
    }

    /**
     * Handles upload of <i>a single file</i>. The file to upload is located by reading the value for the {@link FileUploadService#EXTRA_FILE} extra of the {@code Intent} argument.
     *
     * @param intent An {@link android.content.Intent} that contains an extra key with key/value as described in the documentation for {@link FileUploadService#EXTRA_FILE}.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        String filePath = intent.getStringExtra(EXTRA_FILE);
        FileUploadStatus status = mStatusMap.get(filePath);

        if (status.isUploadJobProcessed()) {
            // If file upload was already handled by a previous task, do nothing.
            return;
        }

        boolean uploaded = false;
        //esto también lo haremos dentro del método de enviar
        File fileToUpload = new File(filePath);

        if (fileToUpload.exists()) {
            // File found, perform upload
            // TODO: note that this functionality has been removed. Implement as needed

            UploadMethod.uploadFile(filePath);

            uploaded = true;
        } else {
            // File not found.
            Log.e(TAG, "File specified in Intent was not found.");
            Log.e(TAG, "File: " + fileToUpload.getName());
            // Mark job as completed such that the notification will not halt due to the job with
            // an illegal file path.
            // Note: this is called below and is not needed here from a technical point of view,
            // but we include the call here just to be explicit, e.g. if you later decide to
            // restructure the code below.
            status.setUploadJobProcessed();
        }

        if (uploaded) {
            // File successfully uploaded.
            // Mark as uploaded.
            status.setFileUploadSuccessful();
        }
        // The task designated for the upload has run to completion, no matter if it is successful
        // or not.
        status.setUploadJobProcessed();
        Log.d(TAG, "Updating notification... uploaded = " + uploaded);

        // Notification updates are run on the main thread.
        // We mimic AsyncTask#onPostExecute(T result) behaviour using a Handler tied to the main thread.
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                // Update the notification to reflect the new state.
                FileUploadService.this.updateNotification();
            }
        });
    }

    /**
     * Updates the notification that displays pending and completed file upload jobs.
     */
    private void updateNotification() {
        //TODO (library modularization)
        //AnteaterApplication.ensureRunningOnMainThread(getClass().getSimpleName()+ ".updateNotification() in a thread other than the main thread.");


        // Update the progress notification...
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
        notificationBuilder.setSmallIcon(R.drawable.cloud_upload);
        // Uncomment below line when we have an actual non-stock icon for the app.
//        notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
        notificationBuilder.setContentTitle(getResources().getString(R.string.notification_title_uploading_files));
        notificationBuilder.setContentText(getResources().getString(R.string.notification_detail_text_uploading_files));
        notificationBuilder.setOngoing(true);
        // For now just open the Anteater home screen when the notification is pressed.
        // We plan to later add an activity displaying the status of upload(s) in progress.
        // TODO add activity that displays progress of upload(s) and create PendingIntent for it.

        Intent i = new Intent(this, AntMonitorMainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(pi);

        // TODO this might be slightly problematic if not locking the map prior to summing as there
        // may be less elements in the map when .size() is accessed below if allowing removals.
        // However, in the current implementation we only put data into the map and never remove it.
        // Moreover, we only manipulate the map on the main thread which is the only thread that
        // should call this method.
        // As such this should be a non issue for now.
        // Sum completed upload jobs.
        int completed = 0;
        for (FileUploadStatus status : mStatusMap.values()) {
            // We only check if completed and ignore successful/unsuccessful upload.
            // The job counts as processed even though it has failed.
            if (status.isUploadJobProcessed()) {
                completed++;
            }
        }
        // Update progress bar.
        notificationBuilder.setProgress(mStatusMap.size(), completed, false);


        NotificationManager notificationMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationMgr.notify(mNotificationId, notificationBuilder.build());

        // Are we done?
        if (completed == mStatusMap.size()) {
            // Remove the notification from the notification bar.
            notificationMgr.cancel(mNotificationId);
        }
    }

    /**
     * Models file upload status information.
     * @author Simon Langhoff (simlanghoff@gmail.com), Janus Varmarken (varmarken@gmail.com)
     */
    public static class FileUploadStatus {

        /**
         * Fully qualified name of the file for which this {@code FileUploadStatus} models upload
         * status information.
         */
        private final String mFileName;

        /**
         * Boolean indicating if the file was successfully uploaded.
         */
        private AtomicBoolean mUploadSuccessful;

        /**
         * Boolean indicating if the job/task designated to upload {@link #mFileName} has
         * been processed (i.e. run to completion, either successfully or unsuccessfully).
         *
         * Indica si la tarea a la que se la asignó el envio del archivo ya ha sido procesada
         */
        private AtomicBoolean mUploadJobProcessed;

        /**
         * Creates a FileUploadStatus.
         * @param fileName The fully qualified file name of the file that is to be uploaded.
         */
        public FileUploadStatus(String fileName) {
            mFileName = fileName;
            mUploadJobProcessed = new AtomicBoolean(false);
            mUploadSuccessful = new AtomicBoolean(false);
        }

        /**
         * Gets the fully qualified file name of the file for which this {@code FileUploadStatus}
         * models upload status information.
         * @return the fully qualified file name of the file for which this {@code FileUploadStatus}
         *         models upload status information.
         */
        public String getFileName() {
            return mFileName;
        }

        /**ñ
         * Was the file successfully uploaded?
         * @return {@code true} if the file was successfully uploaded.
         *         {@code false} if the file upload has not yet been processed or the file upload
         *         has been processed but an error occurred during the upload.
         */
        public boolean isFileUploadSuccessful() {
            return mUploadSuccessful.get();
        }

        /**
         * Has the job designated to upload
         * {@link FileUploadService.FileUploadStatus#getFileName()}
         * been processed (i.e. has it run to completion, either successfully or unsuccessfully)?
         * @return {@code true} if the job has completed, either successfully or unsuccessfully.
         *         {@code false} if the job has not yet completed.
         */
        public boolean isUploadJobProcessed() {
            return mUploadJobProcessed.get();
        }

        /**
         * Toggles a flag to indicate that the file at the path specified by
         * {@link FileUploadService.FileUploadStatus#getFileName()}
         * was successfully uploaded.
         */
        public void setFileUploadSuccessful() {
            mUploadSuccessful.set(true);
        }

        /**
         * Toggles a flag to indicate that the job designated to upload the file at the path
         * specified by
         * {@link FileUploadService.FileUploadStatus#getFileName()}
         * has completed.
         */
        public void setUploadJobProcessed() {
            mUploadJobProcessed.set(true);
        }

    }

    public static class UploadMethod {

        public static void uploadFile(String sourceFileUri) {

            String fileName = sourceFileUri;

            HttpURLConnection conn = null;
            DataOutputStream dos = null;
            String lineEnd = "\r\n";

            String twoHyphens = "--";
            String boundary = "*****";
            int bytesRead, bytesAvailable, bufferSize;
            byte[] buffer;
            int maxBufferSize = 1 * 1024 * 1024;

            Log.i(TAG,"Se esta enviando el archivo "+sourceFileUri);

            //creates a new File instance by converting the given file URI into an abstract pathname
            File fileToUpload = new File(sourceFileUri);

            //EL ARCHIVO EXISTE -> se procede al envio
            try {

                //open a connection to a file
                FileInputStream fileInputStream = new FileInputStream(fileToUpload);
                // open a URL connection to the Servlet
                URL url = new URL(SERVER_ADDRESS);

                // Open a HTTP  connection to  the URL
                conn = (HttpURLConnection) url.openConnection();

                Log.i(TAG, "Se ha establecido una conexion para enviar el archivo");

                conn.setDoInput(true); // Allow Inputs
                conn.setDoOutput(true); // Allow Outputs
                conn.setUseCaches(false); // Don't use a Cached Copy
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                conn.setRequestProperty("archivo", fileName);

                dos = new DataOutputStream(conn.getOutputStream());

                dos.writeBytes(twoHyphens + boundary + lineEnd);
                String content = "Content-Disposition: form-data; name=\"archivo\";filename=\"" + fileName + "\"" + lineEnd;
                dos.writeBytes(content);

                dos.writeBytes(lineEnd);

                // create a buffer of  maximum size
                bytesAvailable = fileInputStream.available();

                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                // read file and write it into form...
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {

                    dos.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                }

                // send multipart form data necesssary after file data...
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                // Responses from the server (code and message)
                serverResponseCode = conn.getResponseCode();
                String serverResponseMessage = conn.getResponseMessage();

                Log.i(TAG, "Server Response was : "
                        + serverResponseMessage + ": " + serverResponseCode);


                //close the streams
                fileInputStream.close();
                dos.flush();
                dos.close();
                Log.i(TAG,"Se ha completado el envio del archivo. Cierre del DataOutputStream");

            } catch (MalformedURLException ex) {

                Log.e(TAG, "error: " + ex.getMessage(), ex);

            } catch (Exception e) {

                Log.e(TAG, "Exception : " + e.getMessage(), e);
            } finally{
                if (conn != null){
                    conn.disconnect();
                    Log.i(TAG,"");
                }
            }
            //dialog.dismiss();
            //return serverResponseCode;

            // } // End else block
        }
    }
}
