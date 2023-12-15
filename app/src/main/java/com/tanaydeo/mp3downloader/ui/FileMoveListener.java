package com.tanaydeo.mp3downloader.ui;

public interface FileMoveListener {
    void onProgressUpdate(int progress);

    void onFileMoveComplete(boolean success);
}
