package com.videowebsite.VideoWebsite.controller;

import com.videowebsite.VideoWebsite.services.GumletSignedUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gumlet")
public class GumletDebugController {

    @Value("${signed.url.secret}")
    private String secretKey;

    @GetMapping("/debug-sign/{videoId}")
    public ResponseEntity<?> debugSignAndTest(@PathVariable String videoId) {
        try {
            // Build base playback URL using same collection id as config
            String playbackBase = "https://video.gumlet.io/684afee9e78588ecc92ec593/" + videoId + "/main.m3u8";
            long expires = Instant.now().getEpochSecond() + 3600;

            List<Map<String,Object>> results = new ArrayList<>();

            // Use GumletSignedUrl to produce the standard token (HMAC-SHA1 as currently implemented)
            try {
                String signed = GumletSignedUrl.getSignedUrl(videoId, secretKey);
                Map<String,Object> r = new HashMap<>();
                r.put("method","standard-generated");
                r.put("signedUrl", signed);
                r.put("status", probeUrl(signed));
                results.add(r);
            } catch (Exception ex) {
                results.add(Map.of("method","standard-generated","error",ex.getMessage()));
            }

            // Build a richer set of canonical candidate strings by varying separators and ordering
            String rawPath = "/684afee9e78588ecc92ec593/" + videoId + "/main.m3u8";
            String pathNoLeading = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
            String pathGetPath = rawPath; // same as rawPath for our constructed URL
            String urlEncodedPath = java.net.URLEncoder.encode(rawPath, java.nio.charset.StandardCharsets.UTF_8);

            String[] pathVariants = new String[] { rawPath, pathNoLeading, pathGetPath, urlEncodedPath };
            String[] seps = new String[] { "", ":", "-", "|", "\n", " " };
            String[] algs = new String[] {"HmacSHA1", "HmacSHA256"};

            for (String pv : pathVariants) {
                for (String sep : seps) {
                    // try both path+sep+expires and expires+sep+path
                    String cand1 = pv + sep + String.valueOf(expires);
                    String cand2 = String.valueOf(expires) + sep + pv;
                    String[] cands = new String[] { cand1, cand2 };
                    for (String cand : cands) {
                        for (String alg : algs) {
                            String tokenHex = GumletSignedUrl.computeHmacHex(cand, secretKey, alg);
                            String tokenHexRaw = GumletSignedUrl.computeHmacHexWithRawSecret(cand, secretKey, alg);
                            String tokenHexUpper = tokenHex.toUpperCase();
                            byte[] tokenBytes = hexStringToByteArray(tokenHex);
                            String tokenB64 = java.util.Base64.getEncoder().encodeToString(tokenBytes);
                            String tokenB64Url = tokenB64.replace('+', '-').replace('/', '_').replaceAll("=+$", "");

                            // try hex (lower)
                            String testUrlHex = playbackBase + "?token=" + tokenHex + "&expires=" + expires;
                            Map<String,Object> rHex = new HashMap<>();
                            rHex.put("candidate", cand);
                            rHex.put("alg", alg);
                            rHex.put("encoding", "hex");
                            rHex.put("token", tokenHex);
                            rHex.put("testUrl", testUrlHex);
                            rHex.put("status", probeUrl(testUrlHex));
                            results.add(rHex);

                            // try hex computed with raw-secret
                            String testUrlHexRaw = playbackBase + "?token=" + tokenHexRaw + "&expires=" + expires;
                            Map<String,Object> rHexRaw = new HashMap<>();
                            rHexRaw.put("candidate", cand);
                            rHexRaw.put("alg", alg + "-rawSecret");
                            rHexRaw.put("encoding", "hex");
                            rHexRaw.put("token", tokenHexRaw);
                            rHexRaw.put("testUrl", testUrlHexRaw);
                            rHexRaw.put("status", probeUrl(testUrlHexRaw));
                            results.add(rHexRaw);

                            // try hex (upper)
                            String testUrlHexU = playbackBase + "?token=" + tokenHexUpper + "&expires=" + expires;
                            Map<String,Object> rHexU = new HashMap<>();
                            rHexU.put("candidate", cand);
                            rHexU.put("alg", alg);
                            rHexU.put("encoding", "hex-upper");
                            rHexU.put("token", tokenHexUpper);
                            rHexU.put("testUrl", testUrlHexU);
                            rHexU.put("status", probeUrl(testUrlHexU));
                            results.add(rHexU);

                            // try base64
                            String testUrlB64 = playbackBase + "?token=" + tokenB64 + "&expires=" + expires;
                            Map<String,Object> rB64 = new HashMap<>();
                            rB64.put("candidate", cand);
                            rB64.put("alg", alg);
                            rB64.put("encoding", "base64");
                            rB64.put("token", tokenB64);
                            rB64.put("testUrl", testUrlB64);
                            rB64.put("status", probeUrl(testUrlB64));
                            results.add(rB64);

                            // try base64url (no padding)
                            String testUrlB64Url = playbackBase + "?token=" + tokenB64Url + "&expires=" + expires;
                            Map<String,Object> rB64Url = new HashMap<>();
                            rB64Url.put("candidate", cand);
                            rB64Url.put("alg", alg);
                            rB64Url.put("encoding", "base64url");
                            rB64Url.put("token", tokenB64Url);
                            rB64Url.put("testUrl", testUrlB64Url);
                            rB64Url.put("status", probeUrl(testUrlB64Url));
                            results.add(rB64Url);

                            // also test URL-encoded forms of tokens
                            String tokenHexEncoded = java.net.URLEncoder.encode(tokenHex, java.nio.charset.StandardCharsets.UTF_8);
                            String tokenB64Encoded = java.net.URLEncoder.encode(tokenB64, java.nio.charset.StandardCharsets.UTF_8);
                            String tokenB64UrlEncoded = java.net.URLEncoder.encode(tokenB64Url, java.nio.charset.StandardCharsets.UTF_8);

                            Map<String,Object> rHexEnc = new HashMap<>();
                            rHexEnc.put("candidate", cand);
                            rHexEnc.put("alg", alg);
                            rHexEnc.put("encoding", "hex-encoded");
                            rHexEnc.put("token", tokenHexEncoded);
                            rHexEnc.put("testUrl", playbackBase + "?token=" + tokenHexEncoded + "&expires=" + expires);
                            rHexEnc.put("status", probeUrl(playbackBase + "?token=" + tokenHexEncoded + "&expires=" + expires));
                            results.add(rHexEnc);

                            Map<String,Object> rB64Enc = new HashMap<>();
                            rB64Enc.put("candidate", cand);
                            rB64Enc.put("alg", alg);
                            rB64Enc.put("encoding", "base64-encoded");
                            rB64Enc.put("token", tokenB64Encoded);
                            rB64Enc.put("testUrl", playbackBase + "?token=" + tokenB64Encoded + "&expires=" + expires);
                            rB64Enc.put("status", probeUrl(playbackBase + "?token=" + tokenB64Encoded + "&expires=" + expires));
                            results.add(rB64Enc);

                            Map<String,Object> rB64UrlEnc = new HashMap<>();
                            rB64UrlEnc.put("candidate", cand);
                            rB64UrlEnc.put("alg", alg);
                            rB64UrlEnc.put("encoding", "base64url-encoded");
                            rB64UrlEnc.put("token", tokenB64UrlEncoded);
                            rB64UrlEnc.put("testUrl", playbackBase + "?token=" + tokenB64UrlEncoded + "&expires=" + expires);
                            rB64UrlEnc.put("status", probeUrl(playbackBase + "?token=" + tokenB64UrlEncoded + "&expires=" + expires));
                            results.add(rB64UrlEnc);
                        }
                    }
                }
            }

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String,Object> probeUrl(String urlStr) {
        Map<String,Object> out = new HashMap<>();
        try {
            URL url = new URL(urlStr);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            out.put("httpCode", code);
            BufferedReader br;
            if (code >= 200 && code < 400) br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            else br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            int lines = 0;
            while ((line = br.readLine()) != null && lines++ < 20) {
                sb.append(line).append('\n');
            }
            out.put("bodyPreview", sb.toString());
        } catch (Exception ex) {
            out.put("error", ex.getMessage());
        }
        return out;
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
