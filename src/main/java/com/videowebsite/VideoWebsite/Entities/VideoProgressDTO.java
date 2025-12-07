package com.videowebsite.VideoWebsite.Entities;

import lombok.Data;

import java.time.Instant;

@Data
public class VideoProgressDTO {
    private String videoId;
    private String title;
    private double duration;
    private int progress;
    private double percentage;
    private boolean isCompleted;
    private Instant lastWatchedAt;
}
