package com.tanaydeo.mp3downloader.ui;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION.SDK_INT;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.tooling.data.ContextCache;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.paris.Paris;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.tanaydeo.mp3downloader.R;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "YtMP3Downloader";
    private static final String SELECTED_FOLDER_URI_KEY = "selectedDownloadFolderUri";

    private SharedPreferences sharedPreferences;

    private TextView selectFolderTextView;
    private EditText inputYoutubeLink;
    private Button downloadButton;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int FOLDER_SELECTION_REQUEST_CODE = 2, REQUEST_DIRECTORY = 123;




    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_MP3Downloader);
        setContentView(R.layout.activity_main);

        // Binding of UI
        selectFolderTextView = findViewById(R.id.tv_select_folder);
        inputYoutubeLink = findViewById(R.id.et_input_yt_link);
        downloadButton = findViewById(R.id.download_button);

        // Get the Shared preference doe download destination
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // Setting selected folder name in text view
        selectFolderTextView.setText(getSelectedFolderName());

        selectFolderTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Request storage permission
                if(!isPermissionGranted()){
                    requestPermission();
                }
                openFolderSelection();
            }
        });



        //Initialize Python
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(getApplicationContext()));
        }

        // Extract information starts
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Get the video URL from the EditText
                Log.d("PERMISSION", isPermissionGranted() + "");
                String videoUrl = inputYoutubeLink.getText().toString();

                if (videoUrl.contains("youtu.be") || videoUrl.contains("youtube.com")){
                    // Execute the AsyncTask to extract video information
                    new ExtractVideoInfoTask().execute(videoUrl);
                }else{
//                    startActivity(new Intent(MainActivity.this, PaymentActivity.class));
                    Toast.makeText(MainActivity.this, "Please provide us a correct url.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Clear Text
        inputYoutubeLink.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int DRAWABLE_RIGHT = 2;

                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if(event.getRawX() >= (inputYoutubeLink.getRight() - inputYoutubeLink.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        // your action here
                        inputYoutubeLink.setText("");
                    }
                }
                return false;
            }
        });
    }


    // Request Permission
    private void openFolderSelection() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        startActivityForResult(intent, REQUEST_DIRECTORY);
    }



    private void takePermissions(Uri uri) {
        getContentResolver().takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );

        // Save the selected folder URI in SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SELECTED_FOLDER_URI_KEY, getSelectedFolderPath(uri));
        editor.apply();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if(!(new File(getDirectoryPathFromUri(uri)).canWrite())){
                    Log.d("PERMISSION CHECK", "No permission " + getDirectoryPathFromUri(uri));
                    Toast.makeText(MainActivity.this, "No write permission: " + getDirectoryPathFromUri(uri), Toast.LENGTH_SHORT).show();
                }else{
                    Log.d("PERMISSION CHECK", "Granted " + getDirectoryPathFromUri(uri));
                }
            }
        }, 3000);


        // Update the folder selection button's text
        selectFolderTextView.setText(getSelectedFolderPath(uri));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DIRECTORY) {
            if (resultCode == RESULT_OK && data != null) {
                Uri selectedFolderUri = data.getData();
                Log.d("URI", selectedFolderUri.getPath());
                if (selectedFolderUri != null) {
                    takePermissions(selectedFolderUri);
                }else{
                    Toast.makeText(this, "URI error", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Failed to get request for read or write.", Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == 2296) {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // perform action when allow permission success
                } else {
                    Toast.makeText(this, "Allow permission for storage access!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0) {
                    boolean READ_EXTERNAL_STORAGE = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean WRITE_EXTERNAL_STORAGE = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                    if (READ_EXTERNAL_STORAGE && WRITE_EXTERNAL_STORAGE) {
                        // perform action when allow permission success
                    } else {
                        Toast.makeText(this, "Allow permission for storage access!", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    private String getDirectoryPathFromUri(Uri uri) {
        String documentId = DocumentsContract.getTreeDocumentId(uri);
        if (documentId != null) {
            String[] parts = documentId.split(":");
            if (parts.length >= 2 && "primary".equalsIgnoreCase(parts[0])) {
                String primaryVolume = Environment.getExternalStorageDirectory().getPath();
                String subPath = parts[1];
                return primaryVolume + "/" + subPath;
            }
        }
        return null;
    }


    private String getSelectedFolderPath(Uri uri) {
        if (DocumentsContract.isTreeUri(uri)) {
            String documentId = DocumentsContract.getTreeDocumentId(uri);
            return "/" + documentId.split(":")[1];
        }
        return "";
    }




    private boolean isPermissionGranted() {
        if(SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }else{
            int writeStorage = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE);
            int readStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            return writeStorage == PackageManager.PERMISSION_GRANTED && readStorage == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(Uri.parse(String.format("package:%s",getApplicationContext().getPackageName())));
                startActivityForResult(intent, 2296);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, 2296);
            }
        } else {
            //below android 11
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }


    private String getSelectedFolderName() {
        String selectedFolderUriString = sharedPreferences.getString(SELECTED_FOLDER_URI_KEY, null);
        if (selectedFolderUriString != null) {
            return selectedFolderUriString;
        }
        return "Select Folder";
    }
    //    Folder Selection Task Ends

    // Grabbing Video Start form Link
    private class ExtractVideoInfoTask extends AsyncTask<String, Void, PyObject> {

        @Override
        protected void onPreExecute() {
            // Disable buttons and enable progressbar
            disableButtonsAndEditTexts();

        }

        @Override
        protected PyObject doInBackground(String... params) {
            // Get the video URL from the parameters
            String videoUrl = params[0];

            // Execute the Python script and capture the output
            Python python = Python.getInstance();
            PyObject videoInfoExtractor = python.getModule("extract_info_from_yt_dlp");
            PyObject result = videoInfoExtractor.callAttr("extract_video_info", videoUrl);

            return result;
        }

        @Override
        protected void onPostExecute(PyObject result) {

            // Check if the result is valid
            if (result != null && !result.isEmpty()) {
                // Parse the JSON output
                String jsonOutput = result.toString();
                Log.d("EXTRACTED INFO", jsonOutput + "");

                // Enable buttons and edittexts
                enableButtonsAndEditTexts();

                // Handle the video information to download and convertion activity
                Intent nextStepIntent = new Intent(MainActivity.this, DownloadAndConvertorActivity.class);
                nextStepIntent.putExtra("video_to_be_processed", jsonOutput);
                nextStepIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(nextStepIntent);
            } else {
                // Display an error toast
                Toast.makeText(MainActivity.this, "Failed to grab video info. Try different URL.", Toast.LENGTH_SHORT).show();

                // Enable buttons and edittexts
                enableButtonsAndEditTexts();
            }
        }
    }

    // Disabling button and editText while grabbing
    private void disableButtonsAndEditTexts() {
        // Input Button disable
        inputYoutubeLink.setEnabled(false);

        // Select destination Disabled
        selectFolderTextView.setClickable(false);
        selectFolderTextView.setEnabled(false);

        // Download Button disabled
        downloadButton.setEnabled(false);
        downloadButton.setText(getString(R.string.grabbing_button_text));
        Paris.style(downloadButton).apply(R.style.button_primary_deactivated);
        // Get the background drawable and cast it to AnimatedVectorDrawable
        Drawable background = downloadButton.getBackground();
        if (background instanceof AnimatedVectorDrawable) {
            AnimatedVectorDrawable animatedDrawable = (AnimatedVectorDrawable) background;
            // Start the animation
            animatedDrawable.start();
        }
    }
    // Enabling button and editText while grabbing
    private void enableButtonsAndEditTexts() {
        // Input Button disable
        inputYoutubeLink.setEnabled(true);

        // Select destination Disabled
        selectFolderTextView.setClickable(true);
        selectFolderTextView.setEnabled(true);

        // Download Button disabled
        downloadButton.setEnabled(true);
        downloadButton.setText(getString(R.string.download_button_text));
        Paris.style(downloadButton).apply(R.style.button_primary);
    }



}