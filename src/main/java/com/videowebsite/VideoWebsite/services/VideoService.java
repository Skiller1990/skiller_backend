package com.videowebsite.VideoWebsite.services;

import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.videowebsite.VideoWebsite.Entities.CourseProgressDTO;
import com.videowebsite.VideoWebsite.Entities.VideoProgressDTO;
import com.videowebsite.VideoWebsite.Entities.model.User;
import com.videowebsite.VideoWebsite.Entities.model.Video;
import com.videowebsite.VideoWebsite.Entities.VideoRequest;
import com.videowebsite.VideoWebsite.Entities.VideoProgressResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class VideoService {

    private final GumletService gumletService;
    @Autowired private JwtService jwtService;

    @Value("${gumlet.collection.value}")
    private String collection;

    @Value("${signed.url.secret}")
    private String secretKey;

    private static final long CHUNK_SIZE = 1024 * 1024; // 1MB

    public VideoService(GumletService gumletService) {
        this.gumletService = gumletService;
    }

    private Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }

    private Video extractVideo(Map<String, Object> asset) {
        String assetId = (String) asset.get("asset_id");
        String colId = (String) asset.get("collection_id");
        Object inputObj = asset.get("input");

        String title = "";
        String description = "";
        double fileLength = 0.0;
        double duration = 0.0;

        if (inputObj instanceof Map inputMap) {
            title = (String) inputMap.getOrDefault("title", "");
            description = (String) inputMap.getOrDefault("description", "");
            fileLength = parseToDouble(inputMap.get("size"));
            duration = parseToDouble(inputMap.get("duration"));
        }

        return new Video(colId, assetId, title, description, fileLength, duration);
    }

    private double parseToDouble(Object obj) {
        try {
            if (obj instanceof Number) {
                return ((Number) obj).doubleValue();
            }
            if (obj instanceof String str && !str.isEmpty()) {
                return Double.parseDouble(str);
            }
        } catch (NumberFormatException ignored) {}
        return 0.0;
    }


    public ResponseEntity<String> getSignedUrlByVideoId(String videoId) throws Exception {
        if (videoId == null || videoId.isBlank())
            return ResponseEntity.badRequest().body("Video ID is required.");

        Video video = findById(videoId);

        if (video == null) {
            Map<String, Object> gumResponse = gumletService.getVideoById(videoId);
            if (gumResponse == null || gumResponse.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found in Gumlet");
            }

            Video newVideo = extractVideo(gumResponse);
            getFirestore().collection("videoMetadata").document(newVideo.getVideoId()).set(newVideo).get();
        }

    String signedUrl = GumletSignedUrl.getSignedUrl(videoId, secretKey);
        return ResponseEntity.ok(signedUrl);
    }

    public Video findById(String id) throws ExecutionException, InterruptedException {
        QuerySnapshot snapshot = getFirestore().collection("videoMetadata")
                .whereEqualTo("videoId", id).get().get();
        if (!snapshot.isEmpty()) {
            return snapshot.getDocuments().get(0).toObject(Video.class);
        }
        return null;
    }

    public VideoProgressResponse getUserProgress(String userId, String videoId) throws ExecutionException, InterruptedException {
        if (userId == null || videoId == null) return new VideoProgressResponse(0, 0);

        DocumentSnapshot snapshot = getFirestore().collection("userVideoProgress")
                .document(userId + "_" + videoId).get().get();

        if (snapshot.exists()) {
            int progress = getSafeInt(snapshot.get("progress"));
            Video video = findById(videoId);
            int duration = video != null ? (int) video.getDuration() : 0;
            return new VideoProgressResponse(progress, duration);
        }
        return new VideoProgressResponse(0, 0);
    }

    public ResponseEntity<?> initializeCourseProgress(String token, String courseId)
            throws ExecutionException, InterruptedException {

        String userId = jwtService.extractUserName(token.substring(7));
        if (userId == null || courseId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing user or course ID"));
        }
        // Ensure a course progress document exists for this user/course (create if missing)
        ensureCourseProgressExists(userId, courseId);

        return getUserCourseProgress(token, courseId);
    }

    /**
     * Ensure that a userCourseProgress document exists for the given user and course.
     * If it does not exist, create it based on the course metadata.
     */
    private void ensureCourseProgressExists(String userId, String courseId) throws ExecutionException, InterruptedException {
        if (userId == null || courseId == null) return;

        DocumentReference courseProgressRef = getFirestore().collection("userCourseProgress")
                .document(userId + "_" + courseId);

        if (!courseProgressRef.get().get().exists()) {
            DocumentSnapshot courseDoc = getFirestore().collection("courses").document(courseId).get().get();
            if (!courseDoc.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");

            String title = courseDoc.getString("title");
            List<String> videoIds = extractVideoIdsFromCourseDoc(courseDoc);
            Object totalDuration = courseDoc.get("totalDuration");

            if (videoIds == null || videoIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course has no videos");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("userId", userId);
            data.put("courseId", courseId);
            data.put("title", title);
            data.put("totalVideos", videoIds.size());
            data.put("totalProgress", 0);
            data.put("completedVideos", 0);
            data.put("totalDuration", getSafeInt(totalDuration));
            data.put("totalPercentage", 0.0);
            data.put("isCompleted", false);
            data.put("lastWatchedVideoId", videoIds.get(0));
            data.put("lastWatchedSeconds", 0);
            data.put("lastWatchedAt", Instant.now().toString());
            data.put("updatedAt", Instant.now().toString());
            System.out.println("Course progress set");
            courseProgressRef.set(data).get();
        }
    }

    /**
     * Extract video IDs from a course document. Supports both legacy `videoIds` list
     * and new `modulesStructure` -> subcategories -> videos -> id nested structure.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractVideoIdsFromCourseDoc(DocumentSnapshot courseDoc) {
        List<String> videoIds = new ArrayList<>();
        Object legacy = courseDoc.get("videoIds");
        if (legacy instanceof List) {
            try {
                List<Object> list = (List<Object>) legacy;
                for (Object o : list) {
                    if (o != null) videoIds.add(o.toString());
                }
            } catch (Exception ignored) {}
        }

        if (!videoIds.isEmpty()) return videoIds;

        // Try to read modulesStructure nested layout
        Object modulesObj = courseDoc.get("modulesStructure");
        if (modulesObj instanceof List) {
            try {
                List<Map<String, Object>> modules = (List<Map<String, Object>>) modulesObj;
                for (Map<String, Object> module : modules) {
                    Object subsObj = module.get("subcategories");
                    if (subsObj instanceof List) {
                        List<Map<String, Object>> subs = (List<Map<String, Object>>) subsObj;
                        for (Map<String, Object> sub : subs) {
                            Object vidsObj = sub.get("videos");
                            if (vidsObj instanceof List) {
                                List<Map<String, Object>> vids = (List<Map<String, Object>>) vidsObj;
                                for (Map<String, Object> v : vids) {
                                    Object idObj = v.get("id");
                                    if (idObj != null) videoIds.add(idObj.toString());
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // fall through
            }
        }

        return videoIds;
    }

    public ResponseEntity<Map<String, Object>> saveVideoProgress(String token, String videoId, int progress) throws ExecutionException, InterruptedException {
        try {
            // Validate Authorization header
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Missing or malformed Authorization header"));
            }

            String userId;
            try {
                userId = jwtService.extractUserName(token.substring(7));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid token"));
            }

            DocumentSnapshot videoDoc = getFirestore().collection("videoMetadata").document(videoId).get().get();

            // If video metadata is missing in Firestore, try to fetch it from Gumlet
            if (videoDoc == null || !videoDoc.exists()) {
                Map<String, Object> gumResponse = gumletService.getVideoById(videoId);
                if (gumResponse == null || gumResponse.isEmpty()) {
                    // Return a clear 404 instead of throwing an NPE which becomes a 500
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Video metadata not found"));
                }
                Video newVideo = extractVideo(gumResponse);
                // Persist the discovered metadata for future calls
                getFirestore().collection("videoMetadata").document(newVideo.getVideoId()).set(newVideo).get();
                videoDoc = getFirestore().collection("videoMetadata").document(videoId).get().get();
            }

            String courseId = videoDoc.getString("courseId");
            if (courseId == null || courseId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Video metadata is missing courseId"));
            }

            double duration = getSafeDouble(videoDoc.get("duration"));
            // protect against zero duration
            if (duration <= 0) duration = 1;

            double percentage = (progress * 100.0) / duration;
            boolean existingCompletion = false;
            DocumentSnapshot existingProgressDoc = getFirestore().collection("userVideoProgress").document(userId + "_" + videoId).get().get();
            if (existingProgressDoc.exists()) {
                existingCompletion = Boolean.TRUE.equals(existingProgressDoc.getBoolean("isCompleted"));
            }
            boolean isCompleted = existingCompletion || percentage >= 95;

            Map<String, Object> videoProgress = new HashMap<>();
            videoProgress.put("userId", userId);
            videoProgress.put("videoId", videoId);
            videoProgress.put("courseId", courseId);
            videoProgress.put("title", videoDoc.getString("title"));
            videoProgress.put("duration", (int) duration);
            videoProgress.put("progress", progress);
            videoProgress.put("percentage", percentage);
            videoProgress.put("isCompleted", isCompleted);
            videoProgress.put("lastWatchedAt", Instant.now().toString());

            getFirestore().collection("userVideoProgress").document(userId + "_" + videoId).set(videoProgress).get();

            DocumentSnapshot courseDoc = getFirestore().collection("courses").document(courseId).get().get();
            if (courseDoc == null || !courseDoc.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Course metadata not found for courseId: " + courseId));
            }

            List<String> videoIds = extractVideoIdsFromCourseDoc(courseDoc);
            if (videoIds == null) videoIds = new ArrayList<>();

            int totalProgress = 0, completedVideos = 0;
            for (String vid : videoIds) {
                DocumentSnapshot prog = getFirestore().collection("userVideoProgress").document(userId + "_" + vid).get().get();
                if (prog.exists()) {
                    totalProgress += getSafeInt(prog.get("progress"));
                    if (Boolean.TRUE.equals(prog.getBoolean("isCompleted"))) completedVideos++;
                }
            }

            int totalDuration = getSafeInt(courseDoc.get("totalDuration"));
            if (totalDuration <= 0) totalDuration = 1; // avoid divide-by-zero
            double totalPercent = (totalProgress * 100.0) / totalDuration;

            Map<String, Object> update = new HashMap<>();
            update.put("totalProgress", totalProgress);
            update.put("completedVideos", completedVideos);
            update.put("totalPercentage", totalPercent);
            boolean existingCourseCompletion = false;
            DocumentSnapshot existingCorurseProgressDoc = getFirestore().collection("userCourseProgress").document(userId + "_" + courseId).get().get();
            if (existingCorurseProgressDoc.exists()) {
                existingCourseCompletion = Boolean.TRUE.equals(existingCorurseProgressDoc.getBoolean("isCompleted"));
            }
            update.put("isCompleted", existingCourseCompletion || totalPercent >= 99);
            update.put("lastWatchedVideoId", videoId);
            update.put("lastWatchedSeconds", progress);
            update.put("lastWatchedAt", Instant.now().toString());
            update.put("updatedAt", Instant.now().toString());

            getFirestore().collection("userCourseProgress").document(userId + "_" + courseId).update(update).get();
            return ResponseEntity.ok(update);
        } catch (Exception e) {
            // Log stacktrace and return a clearer 500 response for the client
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error saving progress", "error", e.getMessage()));
        }
    }

    /**
     * Explicitly set/unset the completion flag for a user's video and update course aggregates.
     */
    public ResponseEntity<?> setVideoCompletion(String token, String videoId, boolean completed) throws ExecutionException, InterruptedException {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Missing or malformed Authorization header"));
            }
            String userId = jwtService.extractUserName(token.substring(7));
            if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","Invalid token"));

            DocumentSnapshot videoDoc = getFirestore().collection("videoMetadata").document(videoId).get().get();
            if (videoDoc == null || !videoDoc.exists()) {
                Map<String, Object> gumResponse = gumletService.getVideoById(videoId);
                if (gumResponse == null || gumResponse.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Video metadata not found"));
                }
                Video newVideo = extractVideo(gumResponse);
                getFirestore().collection("videoMetadata").document(newVideo.getVideoId()).set(newVideo).get();
                videoDoc = getFirestore().collection("videoMetadata").document(videoId).get().get();
            }

            String courseId = videoDoc.getString("courseId");
            if (courseId == null || courseId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Video metadata is missing courseId"));
            }

            // read existing progress if any
            DocumentReference progRef = getFirestore().collection("userVideoProgress").document(userId + "_" + videoId);
            DocumentSnapshot existingProg = progRef.get().get();
            Map<String, Object> videoProgress = new HashMap<>();
            int existingProgress = 0;
            if (existingProg.exists()) {
                existingProgress = getSafeInt(existingProg.get("progress"));
            }

            // if marking completed, set progress to duration (best-effort)
            int duration = (int) getSafeDouble(videoDoc.get("duration"));
            if (duration <= 0) duration = Math.max(1, existingProgress);

            videoProgress.put("userId", userId);
            videoProgress.put("videoId", videoId);
            videoProgress.put("courseId", courseId);
            videoProgress.put("title", videoDoc.getString("title"));
            videoProgress.put("duration", duration);
            videoProgress.put("progress", completed ? duration : existingProgress);
            double percent = duration > 0 ? ((double) (completed ? duration : existingProgress) * 100.0) / duration : 0.0;
            videoProgress.put("percentage", percent);
            videoProgress.put("isCompleted", completed);
            videoProgress.put("lastWatchedAt", Instant.now().toString());

            progRef.set(videoProgress).get();

            // Recompute course-level aggregates similar to saveVideoProgress
            DocumentSnapshot courseDoc = getFirestore().collection("courses").document(courseId).get().get();
            List<String> videoIds = extractVideoIdsFromCourseDoc(courseDoc);
            if (videoIds == null) videoIds = new ArrayList<>();

            int totalProgress = 0, completedVideos = 0;
            for (String vid : videoIds) {
                DocumentSnapshot prog = getFirestore().collection("userVideoProgress").document(userId + "_" + vid).get().get();
                if (prog.exists()) {
                    totalProgress += getSafeInt(prog.get("progress"));
                    if (Boolean.TRUE.equals(prog.getBoolean("isCompleted"))) completedVideos++;
                }
            }

            int totalDuration = getSafeInt(courseDoc.get("totalDuration"));
            if (totalDuration <= 0) totalDuration = 1;
            double totalPercent = (totalProgress * 100.0) / totalDuration;

            Map<String, Object> update = new HashMap<>();
            update.put("totalProgress", totalProgress);
            update.put("completedVideos", completedVideos);
            update.put("totalPercentage", totalPercent);
            boolean existingCourseCompletion = false;
            DocumentSnapshot existingCourseProg = getFirestore().collection("userCourseProgress").document(userId + "_" + courseId).get().get();
            if (existingCourseProg.exists()) existingCourseCompletion = Boolean.TRUE.equals(existingCourseProg.getBoolean("isCompleted"));
            update.put("isCompleted", existingCourseCompletion || totalPercent >= 99);
            update.put("lastWatchedVideoId", videoId);
            update.put("lastWatchedSeconds", completed ? duration : existingProgress);
            update.put("lastWatchedAt", Instant.now().toString());
            update.put("updatedAt", Instant.now().toString());

            getFirestore().collection("userCourseProgress").document(userId + "_" + courseId).update(update).get();

            return ResponseEntity.ok(Map.of("message", "Completion updated", "isCompleted", completed));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error updating completion", "error", e.getMessage()));
        }
    }

    public ResponseEntity<CourseProgressDTO> getUserCourseProgress(String token, String courseId) throws ExecutionException, InterruptedException {
        String userId = jwtService.extractUserName(token.substring(7));
        // Ensure the user's course progress document exists (create lazily if needed)
        try {
            ensureCourseProgressExists(userId, courseId);
        } catch (Exception e) {
            // log and continue — we'll return an empty DTO if creation failed
            System.out.println("ensureCourseProgressExists failed: " + e.getMessage());
        }

        DocumentSnapshot courseProgDoc = getFirestore().collection("userCourseProgress")
                .document(userId + "_" + courseId).get().get();
        System.out.println("Get user progress called");
        if (!courseProgDoc.exists()) {
            // If the document still doesn't exist, return an empty progress DTO instead of 404
            CourseProgressDTO empty = new CourseProgressDTO();
            empty.setUserId(userId);
            empty.setCourseId(courseId);
            empty.setTitle("");
            empty.setTotalProgress(0);
            empty.setTotalPercentage(0.0);
            empty.setCompleted(false);
            empty.setTotalDuration(0);
            empty.setLastWatchedVideoId("");
            empty.setLastWatchedSeconds(0);
            empty.setVideos(new ArrayList<>());
            return ResponseEntity.ok(empty);
        }
        System.out.println("Get user progress called after");

        CourseProgressDTO dto = new CourseProgressDTO();
        dto.setUserId(userId);
        dto.setCourseId(courseId);
        dto.setTitle(courseProgDoc.getString("title"));
        dto.setTotalProgress(getSafeInt(courseProgDoc.get("totalProgress")));
        dto.setTotalPercentage(getSafeDouble(courseProgDoc.get("totalPercentage")));
        dto.setCompleted(Boolean.TRUE.equals(courseProgDoc.getBoolean("isCompleted")));
        dto.setTotalDuration(getSafeInt(courseProgDoc.get("totalDuration")));
        dto.setLastWatchedVideoId(courseProgDoc.getString("lastWatchedVideoId"));
        dto.setLastWatchedSeconds(getSafeInt(courseProgDoc.get("lastWatchedSeconds")));

        DocumentSnapshot courseDoc = getFirestore().collection("courses").document(courseId).get().get();
        //List<String> videoIds = (List<String>) courseDoc.get("videoIds");
        List<String> videoIds = extractVideoIdsFromCourseDoc(courseDoc);

        List<VideoProgressDTO> videos = new ArrayList<>();
        for (String vid : videoIds) {
            DocumentSnapshot vidDoc = getFirestore().collection("videoMetadata").document(vid).get().get();
            DocumentSnapshot progDoc = getFirestore().collection("userVideoProgress")
                    .document(userId + "_" + vid).get().get();

            VideoProgressDTO v = new VideoProgressDTO();
            v.setVideoId(vid);
            v.setTitle(vidDoc.getString("title"));
            v.setDuration(getSafeDouble(vidDoc.get("duration")));
            v.setProgress(progDoc.exists() ? getSafeInt(progDoc.get("progress")) : 0);
            v.setPercentage(progDoc.exists() ? getSafeDouble(progDoc.get("percentage")) : 0.0);
            v.setCompleted(progDoc.exists() && Boolean.TRUE.equals(progDoc.getBoolean("isCompleted")));
            v.setLastWatchedAt(progDoc.exists() && progDoc.get("lastWatchedAt") != null
                    ? Instant.parse(progDoc.getString("lastWatchedAt")) : null);
            videos.add(v);
        }

        dto.setVideos(videos);
        return ResponseEntity.ok(dto);
    }

    public ResponseEntity<?> getCourseCertificate(String token, String courseId) throws ExecutionException, InterruptedException {
        String userId = jwtService.extractUserName(token.substring(7));
        String docId = userId + "_" + courseId;

        DocumentSnapshot snap = getFirestore().collection("userCourseProgress").document(docId).get().get();
        if (!snap.exists() || !Boolean.TRUE.equals(snap.getBoolean("isCompleted"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Course not yet completed"));
        }

        DocumentReference certRef = getFirestore().collection("courseCertificates").document(docId);
        DocumentSnapshot certSnap = certRef.get().get();

        String certificateId;
        if (certSnap.exists()) {
            certificateId = certSnap.getString("certificateId");
        } else {
            certificateId = UUID.randomUUID().toString();
            Map<String, Object> certData = new HashMap<>();
            certData.put("userId", userId);
            certData.put("courseId", courseId);
            certData.put("certificateId", certificateId);
            certData.put("issuedAt", Instant.now().toString());
            certRef.set(certData).get();
        }

        User user = findByEmail(userId);
        return ResponseEntity.ok(Map.of(
                "courseId", courseId,
                "certificateId", certificateId,
                "userName", user.getUserName()
        ));
    }


    private int getSafeInt(Object value) {
        try {
            if (value instanceof Number) return ((Number) value).intValue();
            if (value instanceof String) return (int) Double.parseDouble((String) value);
        } catch (Exception ignored) {}
        return 0;
    }

    private double getSafeDouble(Object value) {
        try {
            if (value instanceof Number) return ((Number) value).doubleValue();
            if (value instanceof String) return Double.parseDouble((String) value);
        } catch (Exception ignored) {}
        return 0.0;
    }

    public User findByEmail(String email) throws ExecutionException, InterruptedException {
        QuerySnapshot snapshot = getFirestore().collection("users").whereEqualTo("email", email).get().get();
        return !snapshot.isEmpty() ? snapshot.getDocuments().get(0).toObject(User.class) : null;
    }
}

