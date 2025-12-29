package com.videowebsite.VideoWebsite.controller;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
import org.springframework.web.client.RestTemplate;

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

            // Create an initial certificate document with status "processing" so the frontend
            // can poll for updates without waiting for the long-running PDF generation.
            Map<String, Object> certDoc = new HashMap<>();
            certDoc.put("certificateId", certificateId);
            certDoc.put("studentName", studentName);
            certDoc.put("courseTitle", courseTitle);
            certDoc.put("email", to);
            certDoc.put("status", "processing");
            certDoc.put("createdAt", java.time.Instant.now().toString());

            db().collection("certificates").document(certificateId).set(certDoc).get();

            // Create the PDFMonkey document synchronously so we can surface the preview_url
            // immediately to the caller. The longer work (poll/download/email) continues
            // in the background using the returned PDFMonkey document id.
            try {
                // Build payload and headers (same validation as in the background thread)
                Map<String, Object> payload = new HashMap<>();
                payload.put("name", studentName);
                payload.put("course", courseTitle);
                payload.put("date", date);
                payload.put("certificateId", certificateId);

                Map<String, Object> document = new HashMap<>();
                String templateId = System.getenv("PDFMONKEY_TEMPLATE_ID");
                if (templateId == null || templateId.isBlank()) templateId = env.getProperty("pdfmonkey.template.id");
                if (templateId == null || templateId.isBlank()) templateId = env.getProperty("pdfmon.template.id");
                if (templateId == null || templateId.isBlank()) {
                    db().collection("certificates").document(certificateId).update(Map.of("status", "failed", "error", "PDFMonkey template ID not configured")).get();
                    return ResponseEntity.status(500).body(Map.of("message", "PDFMonkey template ID not configured"));
                }
                document.put("document_template_id", templateId);
                document.put("payload", payload);

                HttpHeaders pdfHeaders = new HttpHeaders();
                String pdfmonkeyKey = System.getenv("PDFMONKEY_API_KEY");
                if (pdfmonkeyKey == null || pdfmonkeyKey.isBlank()) pdfmonkeyKey = env.getProperty("pdfmonkey.api.key");
                if (pdfmonkeyKey == null || pdfmonkeyKey.isBlank()) {
                    db().collection("certificates").document(certificateId).update(Map.of("status", "failed", "error", "PDFMonkey API key not configured")).get();
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

                Object createBody = createResp == null ? null : createResp.getBody();
                if (createBody == null || ((createBody instanceof Map) && ((Map)createBody).get("document") == null)) {
                    db().collection("certificates").document(certificateId).update(Map.of("status", "failed", "pdfmonkeyCreateResponse", createBody)).get();
                    return ResponseEntity.status(500).body(Map.of("message", "Failed to create PDF document", "pdfmonkeyResponse", createBody));
                }

                Map createdDoc = (Map) ((Map)createBody).get("document");
                String documentId = String.valueOf(createdDoc.get("id"));
                String previewUrl = createdDoc.get("preview_url") == null ? null : String.valueOf(createdDoc.get("preview_url"));

                // Update the Firestore certificate record with the preview URL and pdfmon document id
                Map<String,Object> initialUpdate = new HashMap<>();
                if (previewUrl != null) initialUpdate.put("previewUrl", previewUrl);
                initialUpdate.put("pdfmonDocumentId", documentId);
                initialUpdate.put("pdfmonCreateResponse", createBody);
                db().collection("certificates").document(certificateId).update(initialUpdate).get();

                // If preview URL is available, fire off a quick background email containing the preview link
                if (previewUrl != null) {
                    final String previewForEmail = previewUrl;
                    new Thread(() -> {
                        try {
                            // prepare quick preview email (no attachment) and send asynchronously
                            Map<String, Object> emailPayloadPreview = new HashMap<>();
                            emailPayloadPreview.put("subject", "Your Certificate (preview)");
                            String html = "<p>Hi " + studentName + 
                                ",</p><p>Your certificate preview is available <a href=\"" + previewForEmail + "\">here</a>. We will email the final PDF shortly.</p>";
                            emailPayloadPreview.put("htmlContent", html);
                            emailPayloadPreview.put("to", List.of(Map.of("email", to)));
                            emailPayloadPreview.put("sender", Map.of(
                                "email", env.getProperty("brevo.sender.email"),
                                "name", env.getProperty("brevo.sender.name")
                            ));

                            HttpHeaders brevoHeadersPreview = new HttpHeaders();
                            brevoHeadersPreview.setContentType(MediaType.APPLICATION_JSON);
                            String brevoKeyPreview = env.getProperty("brevo.api.key", System.getenv("BREVO_API_KEY"));
                            if (brevoKeyPreview == null || brevoKeyPreview.isBlank()) {
                                // persist note but don't block
                                try { db().collection("certificates").document(certificateId).update(Map.of("previewEmailStatus", "no_brevo_key")).get(); } catch (Exception ignore) {}
                                return;
                            }
                            brevoHeadersPreview.set("api-key", brevoKeyPreview);
                            try {
                                RestTemplate rtPreview = new RestTemplate();
                                rtPreview.postForEntity(
                                    "https://api.brevo.com/v3/smtp/email",
                                    new HttpEntity<>(emailPayloadPreview, brevoHeadersPreview),
                                    String.class
                                );
                                try { db().collection("certificates").document(certificateId).update(Map.of("previewEmailStatus", "sent", "previewEmailedAt", java.time.Instant.now().toString())).get(); } catch (Exception ignore) {}
                            } catch (Exception e) {
                                try { db().collection("certificates").document(certificateId).update(Map.of("previewEmailStatus", "failed", "previewEmailError", e.getMessage())).get(); } catch (Exception ignore) {}
                            }
                        } catch (Exception ignored) {}
                    }).start();
                }

                // Start background work to poll/download/send using the created document id
                final String pdfmonDocumentIdFinal = documentId;
                final HttpHeaders pdfHeadersFinal = pdfHeaders;
                final Object createBodyFinal = createBody;

                new Thread(() -> {
                    try {
                        // POLL PDFMONKEY (RETRY) and continue with the existing workflow
                        String status = "pending";
                        int attempts = 0;
                        ResponseEntity<Map> lastStatusResp = null;
                        int maxAttempts = 30;
                        long sleepMillis = 2000L;
                        long maxSleep = 8000L;
                        RestTemplate rt2 = new RestTemplate();

                        while (!"success".equalsIgnoreCase(status) && attempts < maxAttempts) {
                            try { Thread.sleep(sleepMillis); } catch (InterruptedException ignored) {}
                            attempts++;

                            ResponseEntity<Map> statusResp = rt2.exchange(
                                "https://api.pdfmonkey.io/api/v1/documents/" + pdfmonDocumentIdFinal,
                                HttpMethod.GET,
                                new HttpEntity<>(pdfHeadersFinal),
                                Map.class
                            );
                            lastStatusResp = statusResp;
                            if (statusResp == null || statusResp.getBody() == null) { sleepMillis = Math.min(maxSleep, sleepMillis * 2); continue; }
                            Map d = (Map) statusResp.getBody().get("document");
                            if (d == null) { sleepMillis = Math.min(maxSleep, sleepMillis * 2); continue; }
                            status = String.valueOf(d.get("status"));
                            if (!"success".equalsIgnoreCase(status)) sleepMillis = Math.min(maxSleep, sleepMillis * 2);
                        }

                        if (!"success".equalsIgnoreCase(status)) {
                            Object lastBody = lastStatusResp == null ? null : lastStatusResp.getBody();
                            Map<String,Object> update = new HashMap<>();
                            update.put("status", "failed");
                            update.put("pdfmonkeyCreateResponse", createBodyFinal);
                            update.put("pdfmonkeyLastStatusResponse", lastBody);
                            update.put("pdfmonkeyAttempts", attempts);
                            db().collection("certificates").document(certificateId).update(update).get();
                            return;
                        }

                        // DOWNLOAD PDF
                        ResponseEntity<byte[]> pdfResp = rt2.exchange(
                            "https://api.pdfmonkey.io/api/v1/documents/" + pdfmonDocumentIdFinal + "/download",
                            HttpMethod.GET,
                            new HttpEntity<>(pdfHeadersFinal),
                            byte[].class
                        );
                        byte[] pdfBytes = pdfResp == null ? null : pdfResp.getBody();
                        if (pdfBytes == null) { db().collection("certificates").document(certificateId).update(Map.of("status", "failed", "error", "Failed to download PDF")).get(); return; }
                        String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);
                        String pdfUrl = "https://api.pdfmonkey.io/api/v1/documents/" + pdfmonDocumentIdFinal + "/download";

                        // Update Firestore record
                        Map<String,Object> update = new HashMap<>();
                        update.put("pdfUrl", pdfUrl);
                        update.put("status", "generated");
                        update.put("generatedAt", java.time.Instant.now().toString());
                        db().collection("certificates").document(certificateId).update(update).get();

                        // SEND EMAIL (BREVO)
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
                        if (brevoKey == null || brevoKey.isBlank()) { db().collection("certificates").document(certificateId).update(Map.of("status", "failed", "error", "Brevo API key not configured")).get(); return; }
                        brevoHeaders.set("api-key", brevoKey);

                        try {
                            rt2.postForEntity(
                                "https://api.brevo.com/v3/smtp/email",
                                new HttpEntity<>(emailPayload, brevoHeaders),
                                String.class
                            );
                        } catch (Exception e) {
                            db().collection("certificates").document(certificateId).update(Map.of("status", "failed", "error", "Failed to send email", "emailError", e.getMessage())).get();
                            return;
                        }

                        db().collection("certificates").document(certificateId).update(Map.of("status", "sent", "sentAt", java.time.Instant.now().toString())).get();

                    } catch (Exception e) {
                        try { db().collection("certificates").document(certificateId).update(Map.of("status", "failed", "error", e.getMessage())).get(); } catch (Exception ignore) {}
                    }
                }).start();

                // Return accepted with certificateId and previewUrl (if available)
                Map<String,Object> resp = new HashMap<>();
                resp.put("message", "Processing");
                resp.put("certificateId", certificateId);
                if (previewUrl != null) resp.put("previewUrl", previewUrl);
                return ResponseEntity.accepted().body(resp);

            } catch (Exception inner) {
                try { db().collection("certificates").document(certificateId).update(Map.of("status", "failed", "error", inner.getMessage())).get(); } catch (Exception ignore) {}
                return ResponseEntity.status(500).body(Map.of("message", "Failed to initiate PDF generation", "error", inner.getMessage()));
            }

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
