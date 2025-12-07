package com.videowebsite.VideoWebsite.Entities;

public class VideoProgressResponse {
    private int progressSeconds;
    private int duration;

    public VideoProgressResponse() {}

    public VideoProgressResponse(int progressSeconds, int duration) {
        this.progressSeconds = progressSeconds;
        this.duration = duration;
    }

    public int getProgressSeconds() {
        return progressSeconds;
    }

    public void setProgressSeconds(int progressSeconds) {
        this.progressSeconds = progressSeconds;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}

