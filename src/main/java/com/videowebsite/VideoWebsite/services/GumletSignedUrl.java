package com.videowebsite.VideoWebsite.services;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.time.Instant;
import java.net.URI;
import java.util.List;

public class GumletSignedUrl {

    /**
     * Generate a Gumlet signed playback URL using HMAC-SHA1.
     * The string to sign is the playback path (including leading '/') + expiration (unix seconds).
     * The returned URL uses query params: ?token=HEX&expires=EPOCH
     */
    public static String getSignedUrl(String videoId, String secret) throws Exception {
        // Playback URL – uses the configured collection id hard-coded here (matches controllers)
        String playbackUrl = "https://video.gumlet.io/684afee9e78588ecc92ec593/" + videoId + "/main.m3u8";

        // Token lifetime (seconds)
        int tokenLifetime = 3600;
        long expiration = Instant.now().getEpochSecond() + tokenLifetime;

        // Use the path portion for signing, including leading '/'
        String path = new URI(playbackUrl).getRawPath();
        String stringToSign = path + expiration;

        // Decode secret: try Base64, then hex, then raw UTF-8 bytes
        byte[] secretBytes = decodeSecret(secret);

        // Compute HMAC-SHA1
        Mac hmac = Mac.getInstance("HmacSHA1");
        SecretKeySpec keySpec = new SecretKeySpec(secretBytes, "HmacSHA1");
        hmac.init(keySpec);
        byte[] hmacBytes = hmac.doFinal(stringToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String tokenHex = bytesToHex(hmacBytes);

        String signedUrl = playbackUrl + "?token=" + tokenHex + "&expires=" + expiration;

        // Optional diagnostics (no-op on success)
        try {
            generateDiagnosticCandidates(playbackUrl, expiration, secretBytes);
        } catch (Exception ignore) {
            // ignore diagnostic failures
        }

        return signedUrl;
    }

    private static byte[] decodeSecret(String secret) {
        if (secret == null) return new byte[0];
        // Try Base64
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ignored) {
        }
        // Try hex
        try {
            return hexStringToByteArray(secret);
        } catch (Exception ignored) {
        }
        // Fallback: raw UTF-8 bytes
        return secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(s.charAt(i), 16);
            int lo = Character.digit(s.charAt(i + 1), 16);
            data[i / 2] = (byte) ((hi << 4) + lo);
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // Public helper used by diagnostic controller to compute HMAC hex tokens
    public static String computeHmacHex(String stringToSign, String secret, String algorithm) throws Exception {
        byte[] secretBytes = decodeSecret(secret);
        Mac mac = Mac.getInstance(algorithm);
        SecretKeySpec keySpec = new SecretKeySpec(secretBytes, algorithm);
        mac.init(keySpec);
        byte[] out = mac.doFinal(stringToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return bytesToHex(out);
    }

    // Try HMAC where the secret is taken as raw UTF-8 bytes (no base64/hex decoding)
    public static String computeHmacHexWithRawSecret(String stringToSign, String secret, String algorithm) throws Exception {
        byte[] secretBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance(algorithm);
        SecretKeySpec keySpec = new SecretKeySpec(secretBytes, algorithm);
        mac.init(keySpec);
        byte[] out = mac.doFinal(stringToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return bytesToHex(out);
    }

    private static void generateDiagnosticCandidates(String playbackUrl, long expiration, byte[] secretBytes) throws Exception {
        String rawPath = new URI(playbackUrl).getRawPath();
        String pathNoLeading = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        String pathGetPath = rawPath;
        String urlEncodedPath = java.net.URLEncoder.encode(rawPath, java.nio.charset.StandardCharsets.UTF_8);

        List<String> candidates = List.of(
                rawPath + String.valueOf(expiration),
                pathNoLeading + String.valueOf(expiration),
                pathGetPath + String.valueOf(expiration),
                urlEncodedPath + String.valueOf(expiration)
        );

        String[] algs = {"HmacSHA1", "HmacSHA256"};
        System.out.println("--- Diagnostic signature candidates ---");
        for (String c : candidates) {
            System.out.println("Candidate string: '" + c + "'");
            for (String alg : algs) {
                Mac mac = Mac.getInstance(alg);
                SecretKeySpec keySpec = new SecretKeySpec(secretBytes, alg);
                mac.init(keySpec);
                byte[] out = mac.doFinal(c.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String hex = bytesToHex(out);
                String b64 = Base64.getEncoder().encodeToString(out);
                System.out.println("  alg=" + alg + " hex=" + hex + " b64=" + b64);
            }
        }
        System.out.println("--- end diagnostic candidates ---");
    }

}