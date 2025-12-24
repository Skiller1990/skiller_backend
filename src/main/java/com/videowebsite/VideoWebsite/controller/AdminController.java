package com.videowebsite.VideoWebsite.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.videowebsite.VideoWebsite.Entities.model.User;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private Firestore db() { return FirestoreClient.getFirestore(); }

    @GetMapping("/courses")
    public ResponseEntity<List<Map<String, Object>>> listCourses() {
        try {
            var snap = db().collection("courses").get().get();
            List<Map<String, Object>> out = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Map<String, Object> m = d.getData();
                if (m == null) m = new HashMap<>();
                m.put("id", d.getId());
                out.add(m);
            }
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/courses")
    public ResponseEntity<?> saveCourse(@RequestBody Map<String, Object> body) {
        try {
            System.out.println("Req body : "+body.toString());
            String id = (String) body.getOrDefault("id", UUID.randomUUID().toString());
            db().collection("courses").document(id).set(body).get();
            return ResponseEntity.ok(Map.of("id", id));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable String id) {
        try {
            db().collection("courses").document(id).delete().get();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> listUsers() {
        try {
            var snap = db().collection("users").get().get();
            List<User> users = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                User u = d.toObject(User.class);
                if (u != null) users.add(u);
            }
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/users/delete")
    public ResponseEntity<?> deleteUser(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            var snap = db().collection("users").whereEqualTo("email", email).get().get();
            if (snap.isEmpty()) return ResponseEntity.notFound().build();
            var doc = snap.getDocuments().get(0);
            db().collection("users").document(doc.getId()).delete().get();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<Map<String,Object>>> listSubscriptions(@RequestParam(required = false) String q) {
        try {
            var snap = db().collection("users").get().get();
            List<Map<String,Object>> out = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Map<String,Object> m = new HashMap<>();
                m.put("userName", d.getString("userName"));
                m.put("email", d.getString("email"));
                List<String> purchased = (List<String>) d.get("purchasedCourses");
                m.put("courses", purchased == null ? Collections.emptyList() : purchased);
                m.put("status", (purchased != null && !purchased.isEmpty())?"Active":"Expired");
                out.add(m);
            }
            if (q != null && !q.isBlank()) {
                String lower = q.toLowerCase();
                out.removeIf(x -> !(String.valueOf(x.get("userName")).toLowerCase().contains(lower) || String.valueOf(x.get("email")).toLowerCase().contains(lower) || String.valueOf(x.get("courses")).toLowerCase().contains(lower)));
            }
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/subscriptions/remove")
    public ResponseEntity<?> removeAccess(@RequestBody Map<String,String> body) {
        try {
            String email = body.get("email");
            String courseId = body.get("courseId");
            var snap = db().collection("users").whereEqualTo("email", email).get().get();
            if (snap.isEmpty()) return ResponseEntity.notFound().build();
            var userDoc = snap.getDocuments().get(0);
            List<String> purchased = (List<String>) userDoc.get("purchasedCourses");
            if (purchased != null) {
                purchased.removeIf(c -> c.equals(courseId));
                db().collection("users").document(userDoc.getId()).update("purchasedCourses", purchased).get();
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Return all course progress documents for a specific user (including per-video progress)
    @GetMapping("/users/{email}/progress")
    public ResponseEntity<List<Map<String,Object>>> listUserCourseProgress(@PathVariable String email) {
        try {
            // Path variables may be percent-encoded (sometimes double-encoded by proxies/builders)
            // e.g. khansajid0259%2540gmail.com -> decode twice -> khansajid0259@gmail.com
            String decodedEmail = email;
            for (int i = 0; i < 3; i++) {
                String tmp = URLDecoder.decode(decodedEmail, StandardCharsets.UTF_8);
                if (tmp.equals(decodedEmail)) break;
                decodedEmail = tmp;
            }

            final String userEmail = decodedEmail;

            var snap = db().collection("userCourseProgress").whereEqualTo("userId", userEmail).get().get();
            List<Map<String,Object>> out = new ArrayList<>();
            for (var d : snap.getDocuments()) {
                Map<String,Object> m = d.getData();
                if (m == null) m = new HashMap<>();
                // Safely derive courseId from document id which is stored as "userId_courseId"
                String docId = d.getId();
                String derivedCourseId = null;
                if (docId != null) {
                    String prefix = userEmail + "_";
                    if (docId.startsWith(prefix)) derivedCourseId = docId.substring(prefix.length());
                    else if (docId.contains("_")) derivedCourseId = docId.substring(docId.indexOf("_") + 1);
                }
                m.put("courseId", derivedCourseId);
                // attach per-video progress for convenience
                List<Map<String,Object>> videos = new ArrayList<>();
                Object totalVideosObj = m.get("totalVideos");
                // attempt to resolve video IDs from the referenced course document
                String courseId = (String) m.getOrDefault("courseId", null);
                if (courseId == null) {
                    // maybe document id suffix has course id
                    String id = d.getId();
                    if (id != null && id.contains("_")) courseId = id.substring(id.indexOf("_") + 1);
                }
                if (courseId != null) {
                    var courseDoc = db().collection("courses").document(courseId).get().get();
                    List<String> videoIds = new ArrayList<>();
                    if (courseDoc.exists()) {
                        Object vids = courseDoc.get("videoIds");
                        if (vids instanceof List) {
                            for (Object v : (List<?>) vids) if (v != null) videoIds.add(v.toString());
                        } else {
                            // try modulesStructure
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
                                                    List<Map<String, Object>> vids2 = (List<Map<String, Object>>) vidsObj;
                                                    for (Map<String, Object> v : vids2) {
                                                        Object idObj = v.get("id"); if (idObj != null) videoIds.add(idObj.toString());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception ignore) {}
                            }
                        }
                    }

                    for (String vid : videoIds) {
                        Map<String,Object> pv = new HashMap<>();
                        pv.put("videoId", vid);
                        var vm = db().collection("videoMetadata").document(vid).get().get();
                        if (vm.exists()) pv.put("title", vm.getString("title"));
                        var up = db().collection("userVideoProgress").document(userEmail + "_" + vid).get().get();
                        if (up.exists()) {
                            pv.put("progress", up.get("progress"));
                            pv.put("percentage", up.get("percentage"));
                            pv.put("isCompleted", up.getBoolean("isCompleted"));
                            pv.put("lastWatchedAt", up.getString("lastWatchedAt"));
                        } else {
                            pv.put("progress", 0);
                            pv.put("percentage", 0.0);
                            pv.put("isCompleted", false);
                        }
                        videos.add(pv);
                    }
                }

                m.put("videos", videos);
                out.add(m);
            }
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // Admin: generate a certificate for a specific user/course (idempotent)
    @PostMapping("/users/{email}/courses/{courseId}/generateCertificate")
    public ResponseEntity<?> generateCertificateForUser(@PathVariable String email, @PathVariable String courseId) {
        try {
            String docId = email + "_" + courseId;
            var progSnap = db().collection("userCourseProgress").document(docId).get().get();
            if (!progSnap.exists() || !Boolean.TRUE.equals(progSnap.getBoolean("isCompleted"))) {
                return ResponseEntity.badRequest().body(Map.of("message", "Course not yet completed"));
            }
            var certRef = db().collection("courseCertificates").document(docId);
            var certSnap = certRef.get().get();
            String certificateId;
            if (certSnap.exists()) {
                certificateId = certSnap.getString("certificateId");
            } else {
                certificateId = UUID.randomUUID().toString();
                Map<String, Object> certData = new HashMap<>();
                certData.put("userId", email);
                certData.put("courseId", courseId);
                certData.put("certificateId", certificateId);
                certData.put("issuedAt", Instant.now().toString());
                certRef.set(certData).get();
            }
            var userSnap = db().collection("users").whereEqualTo("email", email).get().get();
            String userName = email;
            if (!userSnap.isEmpty()) {
                var u = userSnap.getDocuments().get(0);
                userName = (String) u.get("userName");
            }
            return ResponseEntity.ok(Map.of("courseId", courseId, "certificateId", certificateId, "userName", userName));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
