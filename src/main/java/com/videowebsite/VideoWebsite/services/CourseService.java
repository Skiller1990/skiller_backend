package com.videowebsite.VideoWebsite.services;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.videowebsite.VideoWebsite.Entities.model.Course;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import com.videowebsite.VideoWebsite.Entities.model.Video;

import java.math.BigDecimal;
import java.util.*;

@Service
@Slf4j
public class CourseService {

    @Autowired
    private GumletService gumletService;


    public ResponseEntity<Course> saveCourseMetaData() {
        Object object = gumletService.getListOfPlayListsByCollectionId();

        if (!(object instanceof List<?> courseList)) {
            log.warn("Invalid response from Gumlet for playlists");
            return ResponseEntity.badRequest().build();
        }

        Firestore db = FirestoreClient.getFirestore();

        for (Object item : courseList) {
            if (!(item instanceof Map<?, ?> playlistMap)) continue;

            String courseId = (String) playlistMap.get("id");
            if (courseId == null || courseId.isBlank()) {
                log.warn("Skipping course with missing ID");
                continue;
            }

            String title = (String) playlistMap.get("title");
            String description = (String) playlistMap.get("description");

            double newDuration = 0.0;
            List<String> videoIds = new ArrayList<>();

            // Step 1: Fetch asset_list for this course (playlist)
            Object videoListResponse = gumletService.getListOfVideosByPlaylistId(courseId);

            if (videoListResponse instanceof Map<?, ?> videoResponseMap) {
                Object assetListObj = videoResponseMap.get("asset_list");

                if (assetListObj instanceof List<?> assetList) {
                    for (Object asset : assetList) {
                        if (!(asset instanceof Map<?, ?> assetMap)) continue;

                        String videoId = (String) assetMap.get("id");
                        if (videoId == null || videoId.isBlank()) {
                            log.warn("Skipping video with missing ID");
                            continue;
                        }
                        videoIds.add(videoId);

                        try {
                            if (db.collection("videoMetadata").document(videoId).get().get().exists()) {
                                log.info("Video already exists, skipping: {}", videoId);
                                continue;
                            }
                        } catch (Exception e) {
                            log.error("Failed to check video existence: {}", videoId, e);
                            continue;
                        }

                        // Step 2: Fetch metadata of the video
                        Object videoMetaResp = gumletService.getVideoMetaData(videoId);
                        if (videoMetaResp instanceof Map<?, ?> videoMetaMap) {
                            Object inputObj = videoMetaMap.get("input");
                            if (inputObj instanceof Map<?, ?> inputMap) {
                                Video video = new Video();
                                video.setCourseId(courseId);
                                video.setVideoId(videoId);
                                video.setTitle((String) inputMap.get("title"));
                                video.setDescription((String) inputMap.get("description"));

                                // Parse length
                                double length = parseToDouble(inputMap.get("size"));
                                video.setLength(length);

                                // Parse duration
                                double duration = parseToDouble(inputMap.get("duration"));
                                video.setDuration(duration);
                                newDuration += duration;

                                db.collection("videoMetadata").document(videoId).set(video);
                                log.info("Saved video metadata: {}", videoId);
                            }
                        }
                    }
                }
            }

            // Step 3: Get existing course and merge durations
            double existingDuration = 0.0;
            List<String> existingVideoIds = new ArrayList<>();

            try {
                DocumentSnapshot courseDoc = db.collection("courses").document(courseId).get().get();
                if (courseDoc.exists()) {
                    Course existingCourse = courseDoc.toObject(Course.class);
                    if (existingCourse != null) {
                        existingDuration = existingCourse.getTotalDuration();
                        if (existingCourse.getVideoIds() != null) {
                            existingVideoIds.addAll(existingCourse.getVideoIds());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to retrieve existing course: {}", courseId, e);
            }

            Set<String> allVideoIds = new LinkedHashSet<>(existingVideoIds);
            allVideoIds.addAll(videoIds);

            Course course = new Course();
            course.setCourseId(courseId);
            course.setTitle(title);
            course.setDescription(description);
            course.setTotalDuration(existingDuration + newDuration);
            course.setThumbnailUrl(null); // If available, set it
            course.setVideoIds(new ArrayList<>(allVideoIds));

            try {
                db.collection("courses").document(courseId).set(course);
                log.info("Saved/Updated course: {}", courseId);
            } catch (Exception e) {
                log.error("Failed to save/update course: {}", courseId, e);
                return ResponseEntity.internalServerError().build();
            }

            return ResponseEntity.ok(course);
        }

        return ResponseEntity.noContent().build();
    }



    private double parseToDouble(Object obj) {
        try {
            if (obj instanceof Number) return ((Number) obj).doubleValue();
            if (obj instanceof String str && !str.isBlank()) return Double.parseDouble(str);
        } catch (NumberFormatException ignored) {}
        return 0.0;
    }

    
    private BigDecimal parseBigDecimal(Object obj) {
        if (obj instanceof Number) return new BigDecimal(obj.toString());
        if (obj instanceof String str && !str.isEmpty()) return new BigDecimal(str);
        return BigDecimal.ZERO;
    }
    
    public List<Course> getCoursesForUser(String email) {
        Firestore db = FirestoreClient.getFirestore();
        DocumentSnapshot userSnap;
        try {
            var snapshot = db.collection("users").whereEqualTo("email", email).get().get();
            if (snapshot == null || snapshot.isEmpty()) return Collections.emptyList();

            userSnap = snapshot.getDocuments().get(0);
            List<String> purchased = (List<String>) userSnap.get("purchasedCourses");
            List<Course> userCourses = new ArrayList<>();
            if (purchased != null) {
                for (String courseId : purchased) {
                    if (courseId == null) continue;
                    DocumentSnapshot courseSnap = db.collection("courses").document(courseId).get().get();
                    if (courseSnap == null || !courseSnap.exists()) continue;
                    Course course = courseSnap.toObject(Course.class);
                    if (course != null) {
                        course.setCourseId(courseSnap.getId());
                        userCourses.add(course);
                    }
                }
            }
            return userCourses;
        } catch (Exception e) {
            throw new RuntimeException("Error fetching courses for user", e);
        }
    }

    public Course getCourseDetails(String courseId) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentSnapshot doc = db.collection("courses").document(courseId).get().get();
            Course course = doc.toObject(Course.class);
            if (course != null) course.setCourseId(doc.getId());
            return course;
        } catch (Exception e) {
            throw new RuntimeException("Error fetching course", e);
        }
    }

    /**
     * Return all courses stored in Firestore. Returns an empty list on error.
     */
    public List<Course> getAllCourses() {
        try {
            Firestore db = FirestoreClient.getFirestore();
            var snapshot = db.collection("courses").get().get();
            if (snapshot == null || snapshot.isEmpty()) return Collections.emptyList();
            List<Course> out = new ArrayList<>();
            for (var doc : snapshot.getDocuments()) {
                Course c = doc.toObject(Course.class);
                if (c != null) {
                    c.setCourseId(doc.getId());
                    out.add(c);
                }
            }
            return out;
        } catch (Exception e) {
            log.error("Error fetching all courses", e);
            return Collections.emptyList();
        }
    }
    
    public boolean hasUserAccessToCourse(String userEmail, String courseId) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            var snapshot = db.collection("users").whereEqualTo("email", userEmail).get().get();
            if (snapshot == null || snapshot.isEmpty()) return false;
            DocumentSnapshot userDoc = snapshot.getDocuments().get(0);
            List<String> purchasedCourses = (List<String>) userDoc.get("purchasedCourses");
            return purchasedCourses != null && purchasedCourses.contains(courseId);
        } catch (Exception e) {
            log.error("Error checking course access for user: {} and course: {}", userEmail, courseId, e);
            return false;
        }
    }
}
