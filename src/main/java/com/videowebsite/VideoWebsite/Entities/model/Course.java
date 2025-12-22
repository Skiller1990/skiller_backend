package com.videowebsite.VideoWebsite.Entities.model;

import lombok.Data;

import java.util.List;

@Data
public class Course {
    // Keep courseId for backward compatibility
    private String courseId;
    private String id;
    private String title;
    private String description;
    private String image; // front-end field
    private String thumbnailUrl;
    private int originalPrice;
    private int currentPrice;
    private String badge;
    private String level;
    private double rating;
    private int reviews;
    private String duration; // human readable like "20h"
    private int modules; // number of modules
    private String category;
    private List<String> features;
    private boolean enrolled;

    private double totalDuration;
    private List<String> videoIds;

    private List<Module> modulesStructure;
    private boolean published;

    @Data
    public static class Module {
        private String id;
        private String title;
        private String description;
        private List<Subcategory> subcategories;
    }

    @Data
    public static class Subcategory {
        private String id;
        private String title;
        private List<VideoItem> videos;
    }

    @Data
    public static class VideoItem {
        private String id;
        private String title;
        private String duration; // e.g. "12:30"
        private double durationSeconds; // optional numeric duration
        private String description;
        private String embedUrl;
        private String attachment; // optional per-video attachment link (e.g., Google Drive URL)
    }
}
