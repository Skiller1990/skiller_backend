package com.videowebsite.VideoWebsite.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @PostMapping(path = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadFile(@org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                                          jakarta.servlet.http.HttpServletRequest request) {
        try {
            // ensure uploads directory exists
            String uploadsDir = System.getProperty("user.dir") + System.getProperty("file.separator") + "uploads" + System.getProperty("file.separator");
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(uploadsDir));
            String original = org.springframework.util.StringUtils.cleanPath(file.getOriginalFilename());
            String filename = java.util.UUID.randomUUID().toString() + "_" + original;
            java.nio.file.Path target = java.nio.file.Paths.get(uploadsDir).resolve(filename);
            try (java.io.InputStream in = file.getInputStream()) {
                java.nio.file.Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            String baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            String url = baseUrl + "/uploads/" + filename;
            return ResponseEntity.ok(java.util.Map.of("url", url, "name", original));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
