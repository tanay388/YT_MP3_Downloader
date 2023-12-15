package com.tanaydeo.mp3downloader.ui;

import android.os.AsyncTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileMoveTask extends AsyncTask<String, Integer, Boolean> {

    private FileMoveListener listener;

    public FileMoveTask(FileMoveListener listener) {
        this.listener = listener;
    }

    @Override
    protected Boolean doInBackground(String... paths) {
        String sourcePath = paths[0];
        String destinationPath = paths[1];

        File sourceFile = new File(sourcePath);
        File destinationFile = new File(destinationPath);

        // Get the total file size
        long totalSize = sourceFile.length();

        // Create a buffer for reading/writing file data
        byte[] buffer = new byte[1024];
        int bytesRead;
        long totalBytesCopied = 0;

        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(destinationFile)) {

            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesCopied += bytesRead;

                // Calculate the progress in percentage
                int progress = (int) ((totalBytesCopied * 100) / totalSize);

                // Publish the progress
                publishProgress(progress);
            }

            // File move is complete
            return true;
        } catch (IOException e) {
            // Error occurred while moving the file
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        super.onProgressUpdate(progress);

        // Notify the listener of the progress
        listener.onProgressUpdate(progress[0]);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        // File move operation is complete
        // Notify the listener of the result
        listener.onFileMoveComplete(result);
    }

}
