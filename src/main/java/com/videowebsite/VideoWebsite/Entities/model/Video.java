package com.videowebsite.VideoWebsite.Entities.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class Video {

    private String courseId;
    private String videoId;
    private String title;
    private String description;
    private double length;
    private double duration;

    public Video(String courseId, String videoId, String title, String description, double length, double duration) {
        this.courseId =courseId;
        this.videoId = videoId;
        this.title = title;
        this.description = description;
        this.length = length;
        this.duration = duration;
    }

    public Video() {
    }
}
