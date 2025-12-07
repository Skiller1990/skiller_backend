package com.videowebsite.VideoWebsite.controller;

import com.videowebsite.VideoWebsite.Entities.CourseProgressDTO;
import com.videowebsite.VideoWebsite.Entities.model.Course;
import com.videowebsite.VideoWebsite.Entities.model.Video;
import com.videowebsite.VideoWebsite.Entities.VideoProgressResponse;
import com.videowebsite.VideoWebsite.Entities.VideoRequest;
import com.videowebsite.VideoWebsite.services.CourseService;
import com.videowebsite.VideoWebsite.services.JwtService;
import com.videowebsite.VideoWebsite.services.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/videos")
@CrossOrigin("*")
public class VideoController {

    private final VideoService videoService;
    @Autowired
    private CourseService courseService;
    //private final GumletService gumletService;
    private final JwtService jwtService;

    public VideoController(VideoService videoService, JwtService jwtService) {
        this.videoService = videoService;
        //this.gumletService = gumletService;
        this.jwtService = jwtService;
    }

    @PostMapping("/save")
    private ResponseEntity<Course> saveVideo(@RequestBody VideoRequest video) throws URISyntaxException, ExecutionException, InterruptedException {
        //return videoService.saveVideo(video);
        return courseService.saveCourseMetaData();
        //return videoService.saveVideo(video);
    }

    @GetMapping("/stream/{fileName}")
    public ResponseEntity<String> streamVideo(
            @PathVariable String fileName,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) throws Exception {
        //return gumletService.streamVideo(fileName, rangeHeader);
        return videoService.getSignedUrlByVideoId(fileName);
        //return videoService.streamVideo(fileName, rangeHeader);
    }

    @PostMapping("/{courseId}")
    public ResponseEntity<?> initializeCourse(@PathVariable String courseId,
                                         @RequestHeader("Authorization") String token) throws Exception {
        return videoService.initializeCourseProgress(token, courseId);
    }

    @GetMapping("/{videoId}/launch")
    public ResponseEntity<?> launchVideo(@PathVariable String videoId,
                                         @RequestHeader("Authorization") String token) throws Exception {
        String userId = jwtService.extractUserName(token.substring(7));
        VideoProgressResponse progress = videoService.getUserProgress(userId, videoId);
        String signedUrl = videoService.getSignedUrlByVideoId(videoId).getBody();
        return ResponseEntity.ok().body(Map.of("signedUrl",signedUrl, "progress",progress));
    }

    // POST: Save user video progress
    @PostMapping("/saveProgress/{videoId}")
    public ResponseEntity<?> saveProgress(@PathVariable String videoId,
                                          @RequestHeader("Authorization") String token,
                                          @RequestParam int seconds) throws Exception {
        System.out.println("User progress : "+seconds);

        //return videoService.saveVideoProgress(token, videoId, seconds);
        videoService.saveVideoProgress(token, videoId, seconds);
        return ResponseEntity.ok().body(Map.of("message","Progress saved successfully"));
    }

    // GET: Get user video progress
    @GetMapping("/getProgress/{courseId}")
    public ResponseEntity<?> getCourseProgress(@PathVariable String courseId,
                                                             @RequestHeader("Authorization") String token) throws Exception {
//        String userId = jwtService.extractUserName(token.substring(7));
//        VideoProgressResponse response = videoService.getUserProgress(userId, videoId);
        return videoService.getUserCourseProgress(token, courseId);
    }


    @GetMapping("/getCourseCertificate/{courseId}")
    public ResponseEntity<?> getCourseCertificate(@PathVariable String courseId,
                                               @RequestHeader("Authorization") String token) throws Exception {
        return videoService.getCourseCertificate(token, courseId);
    }

}
