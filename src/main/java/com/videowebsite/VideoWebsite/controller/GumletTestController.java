package com.videowebsite.VideoWebsite.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/gumlet-test")
@CrossOrigin("*")
public class GumletTestController {

    @Value("${signed.url.secret}")
    private String secretKey;

    @GetMapping("/debug-sign/{videoId}")
    public ResponseEntity<?> debugSignAndTest(@PathVariable String videoId) {
        try {
            Map<String, Object> results = new HashMap<>();
            String playbackUrl = "https://video.gumlet.io/684afee9e78588ecc92ec593/" + videoId + "/main.m3u8";
            long expires = Instant.now().getEpochSecond() + 3600;

            results.put("videoId", videoId);
            results.put("playbackUrl", playbackUrl);
            results.put("expires", expires);
            results.put("secretKey", secretKey);

            // Test different signing methods
            List<Map<String, Object>> signingResults = new ArrayList<>();

            // Method 1: Current implementation
            try {
                String path = "/684afee9e78588ecc92ec593/" + videoId + "/main.m3u8";
                String stringToSign = path + expires;
                String token = generateHmacSha1(stringToSign, secretKey);
                String signedUrl = playbackUrl + "?token=" + token + "&expires=" + expires;
                
                Map<String, Object> method1 = new HashMap<>();
                method1.put("method", "Current Implementation");
                method1.put("stringToSign", stringToSign);
                method1.put("token", token);
                method1.put("signedUrl", signedUrl);
                signingResults.add(method1);
            } catch (Exception e) {
                Map<String, Object> method1 = new HashMap<>();
                method1.put("method", "Current Implementation");
                method1.put("error", e.getMessage());
                signingResults.add(method1);
            }

            // Method 2: Without leading slash
            try {
                String path = "684afee9e78588ecc92ec593/" + videoId + "/main.m3u8";
                String stringToSign = path + expires;
                String token = generateHmacSha1(stringToSign, secretKey);
                String signedUrl = playbackUrl + "?token=" + token + "&expires=" + expires;
                
                Map<String, Object> method2 = new HashMap<>();
                method2.put("method", "Without Leading Slash");
                method2.put("stringToSign", stringToSign);
                method2.put("token", token);
                method2.put("signedUrl", signedUrl);
                signingResults.add(method2);
            } catch (Exception e) {
                Map<String, Object> method2 = new HashMap<>();
                method2.put("method", "Without Leading Slash");
                method2.put("error", e.getMessage());
                signingResults.add(method2);
            }

            // Method 3: With auth parameter format
            try {
                String path = "/684afee9e78588ecc92ec593/" + videoId + "/main.m3u8";
                String stringToSign = path + expires;
                String token = generateHmacSha1(stringToSign, secretKey);
                String signedUrl = playbackUrl + "?auth=" + token + "&expires=" + expires;
                
                Map<String, Object> method3 = new HashMap<>();
                method3.put("method", "With auth parameter");
                method3.put("stringToSign", stringToSign);
                method3.put("token", token);
                method3.put("signedUrl", signedUrl);
                signingResults.add(method3);
            } catch (Exception e) {
                Map<String, Object> method3 = new HashMap<>();
                method3.put("method", "With auth parameter");
                method3.put("error", e.getMessage());
                signingResults.add(method3);
            }

            // Method 4: HMAC-SHA256
            try {
                String path = "/684afee9e78588ecc92ec593/" + videoId + "/main.m3u8";
                String stringToSign = path + expires;
                String token = generateHmacSha256(stringToSign, secretKey);
                String signedUrl = playbackUrl + "?token=" + token + "&expires=" + expires;
                
                Map<String, Object> method4 = new HashMap<>();
                method4.put("method", "HMAC-SHA256");
                method4.put("stringToSign", stringToSign);
                method4.put("token", token);
                method4.put("signedUrl", signedUrl);
                signingResults.add(method4);
            } catch (Exception e) {
                Map<String, Object> method4 = new HashMap<>();
                method4.put("method", "HMAC-SHA256");
                method4.put("error", e.getMessage());
                signingResults.add(method4);
            }

            results.put("signingMethods", signingResults);
            return ResponseEntity.ok(results);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    private String generateHmacSha1(String data, String secret) throws Exception {
        byte[] secretBytes = hexStringToByteArray(secret);
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, "HmacSHA1");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes("UTF-8"));
        return bytesToHex(hmacBytes);
    }

    private String generateHmacSha256(String data, String secret) throws Exception {
        byte[] secretBytes = hexStringToByteArray(secret);
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes("UTF-8"));
        return bytesToHex(hmacBytes);
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}