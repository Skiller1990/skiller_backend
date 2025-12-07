package com.videowebsite.VideoWebsite.controller;

import com.videowebsite.VideoWebsite.Entities.model.Course;
import com.videowebsite.VideoWebsite.services.CourseService;
import com.videowebsite.VideoWebsite.services.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@CrossOrigin(origins = "*")
public class CourseController {
    @Autowired
    private CourseService courseService;
    @Autowired
    private JwtService jwtService;

    @GetMapping("/my-courses")
    public ResponseEntity<List<Course>> getUserCourses(@RequestHeader("Authorization") String token) {
        String userEmail = jwtService.extractUserName(token.substring(7));
        return ResponseEntity.ok(courseService.getCoursesForUser(userEmail));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<Course> getCourseDetails(@PathVariable String courseId) {
        return ResponseEntity.ok(courseService.getCourseDetails(courseId));
    }

    @GetMapping("")
    public ResponseEntity<List<Course>> getAllCourses() {
        List<Course> list = courseService.getAllCourses();
        return ResponseEntity.ok(list);
    }
    
    @GetMapping("/access/{courseId}")
    public ResponseEntity<Map<String, Object>> checkCourseAccess(
            @PathVariable String courseId,
            @RequestParam String userEmail) {
        
        boolean hasAccess = courseService.hasUserAccessToCourse(userEmail, courseId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("hasAccess", hasAccess);
        response.put("courseId", courseId);
        response.put("userEmail", userEmail);
        
        return ResponseEntity.ok(response);
    }
}

