package com.tanaydeo.mp3downloader.ui;

import static android.content.ContentValues.TAG;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Config;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.LogCallback;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.SessionState;
import com.arthenica.ffmpegkit.Statistics;
import com.arthenica.ffmpegkit.StatisticsCallback;
import com.bumptech.glide.Glide;
import com.tanaydeo.mp3downloader.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class DownloadAndConvertorActivity extends AppCompatActivity {

    private ImageView thumbnailImageView;
    private TextView titleTextView, viewCountTextView, likeCountTextView, tvProgressUpdate, tvProgressTypeIndicator, tvInfoDisplay;
    private ProgressBar progressBar;
    private Button buttonNewDownload;
    private String outputFileName;
    private static final String SELECTED_FOLDER_URI_KEY = "selectedDownloadFolderUri";

    private int DOWNLOAD_OR_CONVERTION_STAGE = 0;
    File dest;
    private long durationTotal;

    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_and_convertor);

        thumbnailImageView = findViewById(R.id.iv_thumbnail);
        titleTextView = findViewById(R.id.tv_video_title);
        viewCountTextView = findViewById(R.id.tv_view_count);
        likeCountTextView = findViewById(R.id.tv_like_count);
        tvProgressUpdate = findViewById(R.id.tv_progress_update);
        progressBar = findViewById(R.id.progressbar_download);
        tvProgressTypeIndicator = findViewById(R.id.tvProgressTypeIndicator);
        tvInfoDisplay = findViewById(R.id.tvInfoDisplay);
        buttonNewDownload = findViewById(R.id.buttonNewDownload);

        handler = new Handler();

        // Retrieve the JSON output from the intent
        String jsonOutput = getIntent().getStringExtra("video_to_be_processed");

        buttonNewDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DownloadAndConvertorActivity.this, MainActivity.class));
            }
        });


        if(jsonOutput != null){
            JSONObject videoInfo = null;
            try {
                videoInfo = new JSONObject(jsonOutput);
                titleTextView.setText(videoInfo.getString("title"));
                viewCountTextView.setText(formatNumberToShortForm(videoInfo.getString("views")) + " Views");
                likeCountTextView.setText(formatNumberToShortForm(videoInfo.getString("likes")) + " Likes");
                // Saving the sanitised file name as per the ffmpeg guidelines to avoid errors
                outputFileName = videoInfo.getString("title").replace(" ","_").replaceAll("[^a-zA-Z0-9.-]", "_");
                outputFileName = outputFileName.substring(0, Math.min(60, outputFileName.length()));
                Glide.with(this).load(videoInfo.getString("thumbnail")).placeholder(R.drawable.loading).into(thumbnailImageView);
                downloadVideoMedia(videoInfo.getString("media_stream"));

                JSONObject finalVideoInfo = videoInfo;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            durationTotal = MediaPlayer.create(getApplicationContext(), Uri.parse(finalVideoInfo.getString("media_stream"))).getDuration();
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }).start();
                } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Download media starts
    private void downloadVideoMedia(String mediaUrl) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(mediaUrl)
                .build();

        final long[] downloadedSize = {0};
        final long[] totalSize = {0};

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Get the video file name from the media URL
                    String fileName = getFileNameFromUrl(mediaUrl);

                    // Create a File object to save the downloaded video
                    SharedPreferences sharedPreferences = getSharedPreferences("YtMP3Downloader", Context.MODE_PRIVATE);
                    String savedFolderUri = sharedPreferences.getString(SELECTED_FOLDER_URI_KEY, "");

                    File folder = new File(savedFolderUri);

                    if (!folder.exists()) {
                        folder.mkdir();
                    }

                    String fileExtention = ".mp4";
                    String outputFileName = fileName + fileExtention;

                    // Get the total file size
                    totalSize[0] = response.body().contentLength();

                    // Create a temporary file to save the video
                    File tempVideoFile = File.createTempFile("temp", ".mp4");
                    tempVideoFile.deleteOnExit();

                    // Initialize variables for progress tracking
                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    // Create a BufferedSink to write the video data to the temporary file
                    BufferedSink bufferedSink = Okio.buffer(Okio.sink(tempVideoFile));

                    // Read the video data from the response and write it to the temporary file
                    while ((bytesRead = response.body().source().read(buffer)) != -1) {
                        bufferedSink.write(buffer, 0, bytesRead);

                        // Update the downloaded size
                        downloadedSize[0] += bytesRead;

                        // Calculate the progress percentage
                        int progress = (int) ((downloadedSize[0] * 100) / totalSize[0]);

                        // Update the progress bar on the UI thread
                        runOnUiThread(() -> {
                            progressBar.setProgress(progress);
                            if (progress >= 100 && DOWNLOAD_OR_CONVERTION_STAGE == 0) {

                                tvProgressTypeIndicator.setText(getString(R.string.converting_progress_text));
                                progressBar.setProgress(1);
                                changeProgressBarIndicatorColor(progressBar, R.color.yellow);

                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        newConvertVideotoMp3(tempVideoFile.getPath());
                                    }
                                }, 1000);
                            }
                        });

                        // Update the downloaded size on the UI thread
                        runOnUiThread(() -> {
                            String downloadedText = String.format(Locale.getDefault(), "%.2f MB / %.2f MB",
                                    downloadedSize[0] / (1024f * 1024f), totalSize[0] / (1024f * 1024f));
                            tvProgressUpdate.setText(downloadedText);

                        });
                    }

                    // Close the BufferedSink
                    bufferedSink.close();

                    // Move the temporary video file to the destination folder with the specified file name
                    File videoFile = new File(folder, outputFileName);
                    tempVideoFile.renameTo(videoFile);

                } else {
                    // Handle unsuccessful response
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            displayError();
                        }
                    });

                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                // Handle network failure
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayError();
                    }
                });

            }
        });
    }

    private String createTempFilePath() {
        // Generate a unique file name for the temporary file
        String fileName = "temp_video_" + outputFileName + ".mp3";

        // Get the directory path for temporary files (e.g., cache directory)
        File tempDir = getCacheDir();

        // Create the temporary file path
        String tempFilePath = tempDir.getAbsolutePath() + File.separator + fileName;

        return tempFilePath;
    }
    private String getFileNameFromUrl(String url) {
        return  outputFileName;
    }
    // Download media ends

    // MP3 convertion starts using ffmpeg
    private void newConvertVideotoMp3(String videoFilePath) {
        progressBar.setProgress(0);
        DOWNLOAD_OR_CONVERTION_STAGE = 1;
        SharedPreferences sharedPreferences = getSharedPreferences("YtMP3Downloader", Context.MODE_PRIVATE);
        String savedFolderPath = sharedPreferences.getString(SELECTED_FOLDER_URI_KEY, "");

        File folder = new File(savedFolderPath);

        if(!folder.canWrite()){
            Log.d("Cannot write", "Failed");
        }

        if (!folder.exists()) {
            folder.mkdirs();
        }

        new File(folder, "test").mkdir();


        String fileExtension = ".mp3";

        File dest = new File(Environment.getExternalStorageDirectory() + savedFolderPath, outputFileName + fileExtension);
        String originalPath = videoFilePath;
        String filePath = dest.getAbsolutePath();
        String tempFilePath = createTempFilePath();

        // Enclose the paths in quotation marks
        String quotedOriginalPath = "\"" + originalPath + "\"";
        String quotedTempFilePath = "\"" + tempFilePath + "\"";
        String quotedFilePath = "\"" + filePath + "\"";

        Log.d("Location", filePath +  " || " + originalPath);



        String newCommand = "-y -i " + quotedOriginalPath + " -vn -ar 44100 -ac 2 -b:a 256k " + quotedFilePath;
        FFmpegKit.executeAsync(newCommand, new FFmpegSessionCompleteCallback() {

            @Override
            public void apply(FFmpegSession session) {
                // This callback method will be called when FFmpeg execution finishes
                if (ReturnCode.isSuccess(session.getReturnCode())) {
                    // Conversion success
                    android.util.Log.d("SUCCESS", filePath + " ");
                } else if (ReturnCode.isCancel(session.getReturnCode())) {
                    // Conversion canceled
                } else {
                    // Conversion failure
                    android.util.Log.d(TAG, String.format("Command failed with state %s and rc %s.%s", session.getState(), session.getReturnCode(), session.getFailStackTrace()));

                    // Display Error
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            displayError();
                        }
                    });
                }
            }
        }, new LogCallback() {
            @Override
            public void apply(com.arthenica.ffmpegkit.Log log) {
                // This callback method will be called when new logs are available
                Log.d("FFMPEG LOG", log.getMessage());
            }
        }, new StatisticsCallback() {
            @Override
            public void apply(Statistics statistics) {
                // This callback method will be called to provide progress updates
                if(durationTotal > 0){
                    long timeInMilliseconds = statistics.getTime(); // Total execution time in milliseconds
                    int progress = (int) (timeInMilliseconds *100 / durationTotal);

                    // Update the progress UI on the main thread
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvProgressTypeIndicator.setText(getString(R.string.converting_progress_text));
                            String progressText = formatTime(timeInMilliseconds) + " / " + formatTime(durationTotal);
                            progressBar.setProgress(progress);
                            tvProgressUpdate.setText(progressText);

                            Log.d("Progress convert", progressText);

                            if (progress == 100 ) {

                                tvProgressTypeIndicator.setText(getString(R.string.savinging_progress_text));
                                changeProgressBarIndicatorColor(progressBar, R.color.blue_light);



//                                FileMoveTask fileMoveTask = new FileMoveTask(new FileMoveListener() {
//                                    @Override
//                                    public void onProgressUpdate(int progress) {
//                                        // Update the progress bar
//                                        tvProgressUpdate.setText(progress + " % ");
//                                        progressBar.setProgress(progress);
//                                    }
//
//                                    @Override
//                                    public void onFileMoveComplete(boolean success) {
//                                        // Handle the file move completion
//                                        if (success) {
//                                            // File move succeeded
//                                            progressBar.setProgress(100);
//                                            tvProgressTypeIndicator.setText(getString(R.string.saved_progress_text));
//                                            changeProgressBarIndicatorColor(progressBar, R.color.green);
//                                            displaySuccess();
//                                            tvProgressUpdate.setText("✅    ");
//                                        } else {
//                                            // File move failed
//                                            // Handle the error
//                                        }
//                                    }
//                                });
//
//                                fileMoveTask.execute(tempFilePath, filePath);

                                handler.postDelayed(new Runnable() {
                                    int progress = 0;
                                    long startTime = System.currentTimeMillis();

                                    @Override
                                    public void run() {
                                        tvProgressUpdate.setText("100%");
                                        long currentTime = System.currentTimeMillis();
                                        long elapsedTime = currentTime - startTime;

                                        if (elapsedTime >= 500) {
                                            // Animation finished, set progress to 100
                                            progress = 100;
                                            progressBar.setProgress(100);
                                            tvProgressTypeIndicator.setText(getString(R.string.saved_progress_text));
                                            changeProgressBarIndicatorColor(progressBar, R.color.green);
                                            displaySuccess();
                                            tvProgressUpdate.setText("✅    ");
                                        } else {
                                            // Calculate the progress based on elapsed time
                                            progress = (int) (elapsedTime * 100 / 500);


                                            // Schedule the next update after a short delay
                                            handler.postDelayed(this, 10);
                                        }
                                    }
                                }, 10);

                            }
                        }
                    });

                }

            }
        });

    }


    // Change ProgressBar color
    private void changeProgressBarIndicatorColor(ProgressBar progressBar, int colorResId) {
        Drawable progressDrawable = progressBar.getProgressDrawable();

        if (progressDrawable instanceof LayerDrawable) {
            LayerDrawable layerDrawable = (LayerDrawable) progressDrawable;
            int indicatorIndex = 1; // Adjust this index if necessary

            Drawable indicatorDrawable = layerDrawable.getDrawable(indicatorIndex);
            int newColor = ContextCompat.getColor(this, colorResId);

            indicatorDrawable.setColorFilter(newColor, PorterDuff.Mode.SRC_IN);
        }

        progressBar.invalidate();
    }


    private void displayError() {
        tvInfoDisplay.setVisibility(View.VISIBLE);
        buttonNewDownload.setVisibility(View.VISIBLE);
        tvInfoDisplay.setText(getString(R.string.error_text));
        tvProgressTypeIndicator.setText(getString(R.string.failed_type));
        // Set the start drawable
        Drawable startDrawable = getResources().getDrawable(R.drawable.ic_error);
        tvInfoDisplay.setCompoundDrawablesRelativeWithIntrinsicBounds(startDrawable, null, null, null);
        progressBar.setProgress(100);
        changeProgressBarIndicatorColor(progressBar, R.color.red);
        tvProgressUpdate.setText("❌    ");
        tvProgressTypeIndicator.setTextColor(getColor(R.color.red));
    }

    private void displaySuccess() {
        tvInfoDisplay.setVisibility(View.VISIBLE);
        buttonNewDownload.setVisibility(View.VISIBLE);
        tvInfoDisplay.setText(getString(R.string.success_text));
        // Set the start drawable
        Drawable startDrawable = getResources().getDrawable(R.drawable.ic_star);
        tvInfoDisplay.setCompoundDrawablesRelativeWithIntrinsicBounds(startDrawable, null, null, null);
        progressBar.setProgress(100);
        tvProgressTypeIndicator.setText(getString(R.string.saved_progress_text));
        changeProgressBarIndicatorColor(progressBar, R.color.green);
    }

    // Formating numbers and time as per UI
    private String formatNumberToShortForm(String s) {
        if (s.length() >= 10) {
            return s.substring(0, s.length() - 9) + "." + s.substring(s.length() - 9, s.length() - 7) + " B";
        } else if (s.length() >= 7) {
            return s.substring(0, s.length() - 6) + "." + s.substring(s.length() - 6, s.length() - 4) + " M";
        } else if (s.length() >= 4) {
            return s.substring(0, s.length() - 3) + "." + s.substring(s.length() - 3, s.length() - 1) + " K";
        } else {
            return s;
        }
    }
    private String formatTime(long milliseconds) {
        long seconds = (milliseconds / 1000) % 60;
        long minutes = (milliseconds / (1000 * 60)) % 60;
        long hours = (milliseconds / (1000 * 60 * 60)) % 24;

        if(hours > 0)
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        else return String.format("%02d:%02d", minutes, seconds);
    }

//    private String getSelectedFolderPath(Uri uri) {
//        String folderPath = "";
//
//        if (DocumentsContract.isDocumentUri(this, uri)) {
//            DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
//            if (documentFile != null && documentFile.isDirectory()) {
//                Uri treeUri = documentFile.getUri();
//                if (treeUri != null) {
//                    DocumentFile treeDocumentFile = DocumentFile.fromTreeUri(this, treeUri);
//                    if (treeDocumentFile != null) {
//                        folderPath = treeDocumentFile.getUri().getPath();
//                    }
//                }
//            }
//        }
//
//        return folderPath;
//    }



}