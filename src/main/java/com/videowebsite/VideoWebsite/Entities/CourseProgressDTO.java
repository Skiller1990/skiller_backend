package com.videowebsite.VideoWebsite.Entities;

import lombok.Data;

import java.util.List;

@Data
public class CourseProgressDTO {
    private  String userId;
    private String courseId;
    private String title;
    private int totalProgress;
    private double totalPercentage;
    private boolean isCompleted;
    private double totalDuration;
    private String lastWatchedVideoId;
    private int lastWatchedSeconds;
    private List<VideoProgressDTO> videos;
}
