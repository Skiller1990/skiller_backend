package com.videowebsite.VideoWebsite.Entities.model;

import lombok.Data;
import java.util.Map;

import java.time.Instant;

@Data
public class CourseProgress {
    private String userId;
    private String courseId;
    private int courseProgress;
    private double duration;
    private Instant updatedAt;
    private Map<String , VideoProgress> video;
    
    @Data
    public static class VideoProgress{
        private String videoId;
        private String title;
        private int videoProgress;
        private double duration;
        private Instant updatedAt;
    }
}

