package com.musicbox.jukebox;

import java.util.List;

public class PlaylistStorage {
    private List<String> playList = List.of("sample.mp3", "sample_2.mp3", "sample_3.mp3", "sample_4.mp3");

    private int indexOfCurrentSong = 0;

    public String getNameOfCurrentStreamingFile() {
        return playList.get(indexOfCurrentSong);
    }

    public void proceedWithNextFile() {
        if (indexOfCurrentSong == playList.size() - 1) {
            indexOfCurrentSong = 0;
        } else {
            indexOfCurrentSong++;
        }
    }
}
