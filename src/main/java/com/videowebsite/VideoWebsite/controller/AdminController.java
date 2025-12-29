package com.videowebsite.VideoWebsite.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import org.springframework.http.HttpMethod;
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

    @Autowired
    private Environment env;

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

    @GetMapping("/course-progress")
    public ResponseEntity<?> getUserCourseProgressForAdmin(@RequestParam String email, @RequestParam String courseId) {
        try {
            if (email == null || email.isBlank() || courseId == null || courseId.isBlank()) return ResponseEntity.badRequest().body(Map.of("message","email and courseId required"));
            // Use email as user identifier (same as JwtService.extractUserName uses email)
            String userId = email;

            var courseDocSnap = db().collection("courses").document(courseId).get().get();
            if (!courseDocSnap.exists()) return ResponseEntity.status(404).body(Map.of("message","Course not found"));

            // Extract video IDs from course document (supports videoIds list or modulesStructure nested)
            List<String> videoIds = new ArrayList<>();
            Object legacy = courseDocSnap.get("videoIds");
            if (legacy instanceof List) {
                try {
                    List<Object> list = (List<Object>) legacy;
                    for (Object o : list) if (o != null) videoIds.add(String.valueOf(o));
                } catch (Exception ignored) {}
            }
            if (videoIds.isEmpty()) {
                Object modulesObj = courseDocSnap.get("modulesStructure");
                if (modulesObj instanceof List) {
                    try {
                        List<Map<String,Object>> modules = (List<Map<String,Object>>) modulesObj;
                        for (Map<String,Object> module : modules) {
                            Object subsObj = module.get("subcategories");
                            if (subsObj instanceof List) {
                                List<Map<String,Object>> subs = (List<Map<String,Object>>) subsObj;
                                for (Map<String,Object> sub : subs) {
                                    Object vidsObj = sub.get("videos");
                                    if (vidsObj instanceof List) {
                                        List<Map<String,Object>> vids = (List<Map<String,Object>>) vidsObj;
                                        for (Map<String,Object> v : vids) {
                                            Object idObj = v.get("id");
                                            if (idObj != null) videoIds.add(String.valueOf(idObj));
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            int total = videoIds.size();
            int completed = 0;
            for (String vid : videoIds) {
                var progSnap = db().collection("userVideoProgress").document(userId + "_" + vid).get().get();
                if (progSnap.exists() && Boolean.TRUE.equals(progSnap.getBoolean("isCompleted"))) completed++;
            }

            double pct = total == 0 ? 0.0 : ((double) completed * 100.0) / total;
            Map<String,Object> out = new HashMap<>();
            out.put("email", email);
            out.put("courseId", courseId);
            out.put("totalVideos", total);
            out.put("completedVideos", completed);
            out.put("percentage", pct);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("message","Error computing progress","error", e.getMessage()));
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

    /**
     * Accepts certificate payload (studentName, courseTitle, date, to) and uses PDFMonkey
     * to generate a PDF, stores a record in Firestore and sends the PDF via Brevo.
     * Expected request body: { studentName, courseTitle, date, to }
     */
    @PostMapping("/send-certificate")
    public ResponseEntity<?> sendCertificate(@RequestBody Map<String, Object> body) {
        try {
            String to = (String) body.get("to");
            String studentName = (String) body.get("studentName");
            String courseTitle = (String) body.get("courseTitle");
            String date = (String) body.get("date");

            if (to == null || to.isBlank() || studentName == null || studentName.isBlank() || courseTitle == null || courseTitle.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "studentName, courseTitle and to are required"));
            }

            String certificateId = generateCertificateId();

            /* -------------------- 1. PDFMonkey PAYLOAD -------------------- */
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", studentName);
            payload.put("course", courseTitle);
            payload.put("date", date);
            payload.put("certificateId", certificateId);

            Map<String, Object> document = new HashMap<>();
            document.put("document_template_id", env.getProperty("pdfmonkey.template.id"));
            document.put("payload", payload);

            HttpHeaders pdfHeaders = new HttpHeaders();
            String pdfmonkeyKey = env.getProperty("pdfmonkey.api.key", System.getenv("PDFMONKEY_API_KEY"));
            if (pdfmonkeyKey == null || pdfmonkeyKey.isBlank()) {
                return ResponseEntity.status(500).body(Map.of("message", "PDFMonkey API key not configured"));
            }
            pdfHeaders.setBearerAuth(pdfmonkeyKey);
            pdfHeaders.setContentType(MediaType.APPLICATION_JSON);

            RestTemplate rt = new RestTemplate();

            ResponseEntity<Map> createResp = rt.postForEntity(
                "https://api.pdfmonkey.io/api/v1/documents",
                new HttpEntity<>(Map.of("document", document), pdfHeaders),
                Map.class
            );

            if (createResp == null || createResp.getBody() == null || createResp.getBody().get("document") == null) {
                return ResponseEntity.status(500).body(Map.of("message", "Failed to create PDF document"));
            }

            Map doc = (Map) createResp.getBody().get("document");
            String documentId = String.valueOf(doc.get("id"));

            /* -------------------- 2. POLL PDFMONKEY (RETRY) -------------------- */
            String status = "pending";
            int attempts = 0;

            while (!"success".equals(status) && attempts < 10) {
                Thread.sleep(2000);
                attempts++;

                ResponseEntity<Map> statusResp = rt.exchange(
                    "https://api.pdfmonkey.io/api/v1/documents/" + documentId,
                    HttpMethod.GET,
                    new HttpEntity<>(pdfHeaders),
                    Map.class
                );

                if (statusResp == null || statusResp.getBody() == null) break;
                Map d = (Map) statusResp.getBody().get("document");
                if (d == null) break;
                status = String.valueOf(d.get("status"));
            }

            if (!"success".equals(status)) {
                return ResponseEntity.status(500)
                    .body(Map.of("message", "PDF generation failed"));
            }

            /* -------------------- 3. DOWNLOAD PDF -------------------- */
            ResponseEntity<byte[]> pdfResp = rt.exchange(
                "https://api.pdfmonkey.io/api/v1/documents/" + documentId + "/download",
                HttpMethod.GET,
                new HttpEntity<>(pdfHeaders),
                byte[].class
            );

            byte[] pdfBytes = pdfResp == null ? null : pdfResp.getBody();
            if (pdfBytes == null) return ResponseEntity.status(500).body(Map.of("message", "Failed to download PDF"));
            String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);

            String pdfUrl = "https://api.pdfmonkey.io/api/v1/documents/" + documentId + "/download";

            /* -------------------- 4. STORE IN FIRESTORE -------------------- */
            Map<String, Object> certDoc = new HashMap<>();
            certDoc.put("certificateId", certificateId);
            certDoc.put("studentName", studentName);
            certDoc.put("courseTitle", courseTitle);
            certDoc.put("email", to);
            certDoc.put("pdfUrl", pdfUrl);
            certDoc.put("status", "generated");
            certDoc.put("createdAt", java.time.Instant.now().toString());

            db().collection("certificates").document(certificateId).set(certDoc).get();

            /* -------------------- 5. SEND EMAIL (BREVO) -------------------- */
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("content", pdfBase64);
            attachment.put("name", "certificate.pdf");

            Map<String, Object> emailPayload = new HashMap<>();
            emailPayload.put("subject", "Your Certificate of Completion");
            emailPayload.put("htmlContent",
                "<p>Congratulations <b>" + studentName +
                "</b>!<br/>Your certificate is attached.</p>");

            emailPayload.put("to", List.of(Map.of("email", to)));
            emailPayload.put("sender", Map.of(
                "email", env.getProperty("brevo.sender.email"),
                "name", env.getProperty("brevo.sender.name")
            ));
            emailPayload.put("attachment", List.of(attachment));

            HttpHeaders brevoHeaders = new HttpHeaders();
            brevoHeaders.setContentType(MediaType.APPLICATION_JSON);
            String brevoKey = env.getProperty("brevo.api.key", System.getenv("BREVO_API_KEY"));
            if (brevoKey == null || brevoKey.isBlank()) {
                return ResponseEntity.status(500).body(Map.of("message", "Brevo API key not configured"));
            }
            brevoHeaders.set("api-key", brevoKey);

            rt.postForEntity(
                "https://api.brevo.com/v3/smtp/email",
                new HttpEntity<>(emailPayload, brevoHeaders),
                String.class
            );

            /* -------------------- 6. UPDATE STATUS -------------------- */
            db().collection("certificates")
              .document(certificateId)
              .update("status", "sent").get();

            return ResponseEntity.ok(Map.of(
                "message", "Certificate emailed successfully",
                "certificateId", certificateId,
                "pdfUrl", pdfUrl
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("message", "Certificate process failed", "error", e.getMessage()));
        }
    }

    @GetMapping("/certificates/{id}")
    public ResponseEntity<?> getCertificateById(@PathVariable String id) {
        try {
            var docSnap = db().collection("certificates").document(id).get().get();
            if (!docSnap.exists()) return ResponseEntity.status(404).body(Map.of("message", "Certificate not found"));
            Map<String,Object> data = docSnap.getData();
            if (data == null) data = new HashMap<>();
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("message", "Failed to fetch certificate", "error", e.getMessage()));
        }
    }

    /**
     * Generate a short certificate id with prefix SKC- and 8 uppercase alphanumeric chars.
     */
    private String generateCertificateId() {
        // Generate an 8-digit numeric certificate id (10000000..99999999)
        int min = 10_000_000;
        int max = 99_999_999;
        int num = java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1);
        return String.valueOf(num);
    }
}
