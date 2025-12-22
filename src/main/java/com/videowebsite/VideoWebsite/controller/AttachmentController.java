package com.videowebsite.VideoWebsite.controller;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AttachmentController {

    @Autowired(required = false)
    private Storage storage;

    @Value("${firebase.project.id:sajid-project}")
    private String projectId;
    @Value("${firebase.storage.bucket:}")
    private String storageBucket;

    // Upload endpoint for admin to send attachment files. Returns storagePath and a temp signedUrl.
    @PostMapping(path = "/admin/attachments/upload", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadAttachment(@RequestParam("file") MultipartFile file, @RequestParam(required = false) String courseId, @RequestParam(required = false) String videoId) {
        // Uploads are intentionally disabled. Admin UI now accepts external links (Google Drive etc.).
        // Keep the endpoint mapped to return a clear, non-ambiguous response so callers (old clients) fail fast.
        return ResponseEntity.status(410).body(Map.of("error", "Upload endpoint disabled. Use external links (Google Drive) in the admin UI."));
    }

    // Return a short-lived signed URL for a given storage path. Useful when serving downloads to authorized users.
    @GetMapping("/attachments/signedUrl")
    public ResponseEntity<?> getSignedUrl(@RequestParam("path") String path, @RequestParam(required = false, defaultValue = "1") int days) {
        try {
            if (storage == null) return ResponseEntity.internalServerError().body(Map.of("error", "Storage client not initialized"));
            String bucket = projectId + ".appspot.com";
            BlobId blobId = BlobId.of(bucket, path);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            URL signedUrl = storage.signUrl(blobInfo, Math.max(1, Math.min(days, 30)), TimeUnit.DAYS, Storage.SignUrlOption.withV4Signature());
            return ResponseEntity.ok(Map.of("signedUrl", signedUrl.toString()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
