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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document.OutputSettings;
// iText7 imports for image-based PDF creation
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.layout.Document;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.element.Image;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
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
     * Accepts certificate payload and sends an email via Brevo (Sendinblue).
     * Expected request body: { to: string, subject?: string, html: string, fileName?: string, attachPdf?: boolean }
     */
    @PostMapping("/send-certificate")
    public ResponseEntity<?> sendCertificate(@RequestBody Map<String, Object> body) {
        try {
            String to = (String) body.get("to");
            String subject = (String) body.getOrDefault("subject", "Your Certificate");
            String html = (String) body.get("html");
            // support minimal payload: studentName and courseTitle when html is not provided
            String studentNameReq = (String) body.get("studentName");
            String courseTitleReq = (String) body.get("courseTitle");
            String fileName = (String) body.getOrDefault("fileName", "certificate.pdf");
            boolean attachPdf = true;
            if (body.containsKey("attachPdf")) {
                Object v = body.get("attachPdf");
                attachPdf = Boolean.TRUE.equals(v) || (v instanceof String && Boolean.parseBoolean((String)v));
            }

            // Require either html OR both studentName and courseTitle
            if (to == null || to.isBlank() || ((html == null || html.isBlank()) && (studentNameReq == null || studentNameReq.isBlank() || courseTitleReq == null || courseTitleReq.isBlank()))) {
                return ResponseEntity.badRequest().body(Map.of("message", "'to' and either 'html' or both 'studentName' and 'courseTitle' are required"));
            }

            // Save certificate record to Firestore before sending and reserve an ID
            String certId = generateCertificateId();
            Map<String,Object> certDoc = new HashMap<>();
            certDoc.put("id", certId);
            certDoc.put("to", to);
            certDoc.put("subject", subject);
            certDoc.put("fileName", fileName);
            certDoc.put("createdAt", java.time.Instant.now().toString());
            // optional fields from request
            if (body.containsKey("courseId")) certDoc.put("courseId", body.get("courseId"));
            if (body.containsKey("studentName")) certDoc.put("studentName", body.get("studentName"));
            // store courseTitle if provided
            if (body.containsKey("courseTitle")) certDoc.put("courseTitle", body.get("courseTitle"));
            certDoc.put("status", "pending");
            db().collection("certificates").document(certId).set(certDoc).get();

            // Replace placeholder in HTML with actual certificate ID so it appears on the certificate (only if html provided)
            if (html != null) {
                html = html.replace("%%CERT_ID%%", certId);
                // store a small preview/snippet of rendered HTML for debugging/audit
                try {
                    String preview = html.length() > 2000 ? html.substring(0, 2000) : html;
                    certDoc.put("sentHtmlPreview", preview);
                    db().collection("certificates").document(certId).update(certDoc).get();
                } catch (Exception ignore) {}
            }

            // Optionally convert HTML or generate PDF from certificate PNG using iText7
            String attachmentBase64 = null;
            if (attachPdf) {
                try {
                    // Try to generate PDF by overlaying text on a certificate PNG background using iText7
                    byte[] imageBytes = null;
                    // Try classpath resource first: /certificate/certificate.png
                    try (InputStream is = AdminController.class.getResourceAsStream("/certificate/certificate.png")) {
                        if (is != null) {
                            imageBytes = is.readAllBytes();
                        }
                    } catch (Exception ignore) {}

                    // If not found on classpath, try environment-configured URL
                    if (imageBytes == null) {
                        String imgUrl = env.getProperty("CERTIFICATE_IMAGE_URL", System.getenv("CERTIFICATE_IMAGE_URL"));
                        if (imgUrl != null && !imgUrl.isBlank()) {
                            try {
                                // fetch raw bytes from URL
                                try (InputStream is = new URL(imgUrl).openStream()) {
                                    imageBytes = is.readAllBytes();
                                }
                            } catch (Exception e) {
                                System.err.println("Failed to fetch certificate image from URL: " + e.getMessage());
                            }
                        }
                    }

                    if (imageBytes != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        PdfWriter writer = new PdfWriter(baos);
                        PdfDocument pdfDoc = new PdfDocument(writer);
                        pdfDoc.setDefaultPageSize(PageSize.A4.rotate());
                        Document document = new Document(pdfDoc);

                        Image img = new Image(ImageDataFactory.create(imageBytes));
                        // scale image to cover full page
                        img.scaleToFit(pdfDoc.getDefaultPageSize().getWidth(), pdfDoc.getDefaultPageSize().getHeight());
                        img.setFixedPosition(0, 0);
                        document.add(img);

                        float pageWidth = pdfDoc.getDefaultPageSize().getWidth();
                        float pageHeight = pdfDoc.getDefaultPageSize().getHeight();

                        String studentName = (String) certDoc.getOrDefault("studentName", studentNameReq != null ? studentNameReq : "");
                        String courseIdVal = String.valueOf(certDoc.getOrDefault("courseId", ""));
                        String courseTitle = (String) certDoc.getOrDefault("courseTitle", courseTitleReq != null ? courseTitleReq : "");
                        // Format createdAt to date-only string (YYYY-MM-DD)
                        String createdAtRaw = (String) certDoc.getOrDefault("createdAt", Instant.now().toString());
                        String dateStr = "";
                        try {
                            Instant inst = Instant.parse(createdAtRaw);
                            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));
                            dateStr = fmt.format(inst);
                        } catch (Exception ex) {
                            dateStr = createdAtRaw;
                        }

                        var font = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                        // Student name (centered, slightly lower)
                        Paragraph nameP = new Paragraph(studentName).setFont(font).setFontSize(36).setBold();
                        document.showTextAligned(nameP, pageWidth / 2f, pageHeight * 0.50f, TextAlignment.CENTER);

                        // Course title (centered below name)
                        Paragraph courseP = new Paragraph(courseTitle).setFont(font).setFontSize(20);
                        document.showTextAligned(courseP, pageWidth / 2f, pageHeight * 0.40f, TextAlignment.CENTER);

                        // Date (bottom-left area) - moved slightly left and up
                        Paragraph dateP = new Paragraph("Date: " + dateStr).setFont(font).setFontSize(12);
                        document.showTextAligned(dateP, pageWidth * 0.15f, pageHeight * 0.22f, TextAlignment.LEFT);

                        // Certificate ID (bottom-right area) - moved slightly right and up
                        Paragraph idP = new Paragraph("Certificate ID: " + certId).setFont(font).setFontSize(12);
                        document.showTextAligned(idP, pageWidth * 0.85f, pageHeight * 0.22f, TextAlignment.RIGHT);

                        document.close();
                        byte[] pdfBytes = baos.toByteArray();
                        attachmentBase64 = Base64.getEncoder().encodeToString(pdfBytes);
                    } else {
                        // Fallback to the existing HTML -> PDF conversion if image not available
                        try {
                            OutputSettings settings = new OutputSettings();
                            settings.syntax(OutputSettings.Syntax.xml);
                            settings.escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);
                            settings.charset(java.nio.charset.StandardCharsets.UTF_8);
                            String xhtml = Jsoup.parse(html).outputSettings(settings).outerHtml();

                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            PdfRendererBuilder builder = new PdfRendererBuilder();
                            builder.withHtmlContent(xhtml, null);
                            builder.toStream(os);
                            builder.run();
                            byte[] pdfBytes = os.toByteArray();
                            attachmentBase64 = Base64.getEncoder().encodeToString(pdfBytes);
                        } catch (Exception pdfEx) {
                            pdfEx.printStackTrace();
                            System.err.println("PDF conversion failed, will send HTML-only email: " + pdfEx.getMessage());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Certificate image PDF generation failed: " + e.getMessage());
                }
            }

            // Build request payload for Brevo API v3
            Map<String, Object> sendPayload = new HashMap<>();
            Map<String, String> sender = new HashMap<>();
            sender.put("name", env.getProperty("brevo.sender.name", "Skiller Classes"));
            sender.put("email", env.getProperty("brevo.sender.email", "support@skillerclasses.com"));
            sendPayload.put("sender", sender);

            List<Map<String,String>> toList = new ArrayList<>();
            Map<String,String> toMap = new HashMap<>();
            toMap.put("email", to);
            toList.add(toMap);
            sendPayload.put("to", toList);
            sendPayload.put("subject", subject);
            sendPayload.put("htmlContent", html);

            if (attachmentBase64 != null) {
                List<Map<String,String>> attachments = new ArrayList<>();
                Map<String,String> a = new HashMap<>();
                a.put("content", attachmentBase64);
                a.put("name", fileName.endsWith(".pdf") ? fileName : fileName + ".pdf");
                a.put("contentType", "application/pdf");
                attachments.add(a);
                sendPayload.put("attachment", attachments);
            }

            String apiKey = env.getProperty("brevo.api.key", System.getenv("BREVO_API_KEY"));
            if (apiKey == null || apiKey.isBlank()) {
                return ResponseEntity.status(500).body(Map.of("message", "Brevo API key not configured"));
            }

            RestTemplate rt = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            HttpEntity<Map<String,Object>> req = new HttpEntity<>(sendPayload, headers);
            String url = "https://api.brevo.com/v3/smtp/email";
            var resp = rt.postForEntity(url, req, String.class);

            // update firestore record with result
            certDoc.put("status", resp.getStatusCode().is2xxSuccessful() ? "sent" : "failed");
            certDoc.put("brevoResponse", resp.getBody());
            db().collection("certificates").document(certId).update(certDoc).get();

            return ResponseEntity.status(resp.getStatusCode()).body(Map.of("message", "Certificate email sent", "certificateId", certId, "brevoResp", resp.getBody()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("message", "Failed to send certificate", "error", e.getMessage()));
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
