package com.videowebsite.VideoWebsite.services;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.videowebsite.VideoWebsite.Entities.LoginRequest;
import com.videowebsite.VideoWebsite.Entities.SignInResponse;
import com.videowebsite.VideoWebsite.Entities.SignupRequest;
import com.videowebsite.VideoWebsite.Entities.model.User;
import com.videowebsite.VideoWebsite.exception.UserAlreadyExistsException;
import com.videowebsite.VideoWebsite.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

@Service
public class UserService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired private JwtService jwtService;
    @Autowired private BrevoEmailService brevoEmailService;
    @Autowired private ResourceLoader resourceLoader;
    @Autowired private PromoService promoService;

    @Value("${google.client-id}")
    private String googleClientId;

    private Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }

    @Value("${frontend.url:http://localhost:4200}")
    private String frontendUrl;

    public String signup(SignupRequest signupRequest) throws ExecutionException, InterruptedException {
        validateSignupRequest(signupRequest);

        if (emailExists(signupRequest.getEmail())) {
            throw new UserAlreadyExistsException("User already exists with email: " + signupRequest.getEmail());
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUserName(signupRequest.getUserName());
        user.setEmail(signupRequest.getEmail());
        user.setPassword(encoder.encode(signupRequest.getPassword()));
        user.setGoogleSignIn(false);
        // Ensure purchasedCourses is initialized to avoid NPEs later
        user.setPurchasedCourses(new ArrayList<>());

        getFirestore().collection("users").document(user.getId()).set(user).get();

        sendWelcomeEmailAsync(signupRequest.getEmail(), signupRequest.getUserName());

        // If user opted-in with a promo code, validate and persist the association.
        // We validate the promo at signup and reject signup if the promo is invalid.
        try {
            if (signupRequest.getPromoOptIn() != null && signupRequest.getPromoOptIn() && signupRequest.getPromoCode() != null && !signupRequest.getPromoCode().isBlank()) {
                String code = signupRequest.getPromoCode().trim().toUpperCase();
                // validate promo (courseId is null for signup-level promos)
                var validation = promoService.validateForCourseAndUser(code, null, user.getEmail());
                if (!validation.valid) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid promo code: " + validation.message);
                } else {
                    // Save applied promo on user, but DO NOT consume usage here. Usage will be consumed during ORDER_PAYMENT verification.
                    user.setAppliedPromoCode(code);
                    user.setPromoAppliedAt(System.currentTimeMillis());
                    getFirestore().collection("users").document(user.getId()).set(user, SetOptions.merge()).get();
                }
            }
        } catch (ResponseStatusException rse) {
            throw rse; // rethrow to let controller return proper HTTP status
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process promo code: " + e.getMessage(), e);
        }

        return "User registered successfully.";
    }

    public SignInResponse login(LoginRequest loginRequest) {
        validateLoginRequest(loginRequest);

        User existingUser;
        try {
            existingUser = findByEmail(loginRequest.getEmail());
        } catch (ExecutionException | InterruptedException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching user data", e);
        }

        if (existingUser == null || !encoder.matches(loginRequest.getPassword(), existingUser.getPassword())) {
            throw new UserNotFoundException("Invalid username or password.");
        }

        String token = jwtService.generateToken(loginRequest.getEmail());

        Map<String, Integer> videoProgressMap = new HashMap<>();
        try {
            // defensive: handle null purchasedCourses inside getUserCourseProgress
            videoProgressMap = getUserCourseProgress(existingUser.getId(), existingUser.getPurchasedCourses());
        } catch (Exception e) {
            e.printStackTrace(); // log properly in production
        }

        SignInResponse.UserResponse userResponse = new SignInResponse.UserResponse(
                existingUser.getId(),
                existingUser.getUserName(),
                existingUser.getEmail(),
                existingUser.getPurchasedCourses()
        );
        userResponse.setCoursesProgress(videoProgressMap);

        SignInResponse response = new SignInResponse();
        response.setToken(token);
        response.setUser(userResponse);
        return response;
    }

    public ResponseEntity<?> googleSignUp(Map<String, String> body) throws Exception {
        String idTokenString = body.get("idToken");

        if (idTokenString == null || idTokenString.isBlank()) {
            return ResponseEntity.badRequest().body("Missing ID token");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier
                .Builder(new NetHttpTransport(), new JacksonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            this.findOrCreateUser(email, name, true);

            String token = jwtService.generateToken(email);
            return ResponseEntity.ok(Map.of("token", token));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid ID token");
        }
    }

    public User findByEmail(String email) throws ExecutionException, InterruptedException {
        QuerySnapshot snapshot = getFirestore().collection("users")
                .whereEqualTo("email", email)
                .get()
                .get();

        if (!snapshot.isEmpty()) {
            User user = snapshot.getDocuments().get(0).toObject(User.class);
            if (user != null && user.getPurchasedCourses() == null) {
                user.setPurchasedCourses(new ArrayList<>());
            }
            return user;
        }
        return null;
    }

    public String findOrCreateUser(String email, String userName, boolean googleSignIn) {
        try {
            User user = findByEmail(email);
            if (user == null) {
                user = new User();
                user.setId(UUID.randomUUID().toString());
                user.setUserName(userName);
                user.setEmail(email);
                user.setPassword(encoder.encode("GoogleSignUp"));
                user.setGoogleSignIn(googleSignIn);
                // initialize purchased courses list
                user.setPurchasedCourses(new ArrayList<>());

                getFirestore().collection("users").document(user.getId()).set(user).get();
                sendWelcomeEmailAsync(email, userName);
            }
            return email;
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed to create user", e);
        }
    }

    public User getUser(String userId) throws ExecutionException, InterruptedException {
        DocumentReference docRef = getFirestore().collection("users").document(userId);
        User user = docRef.get().get().toObject(User.class);
        if (user != null && user.getPurchasedCourses() == null) {
            user.setPurchasedCourses(new ArrayList<>());
        }
        return user;
    }

    private boolean emailExists(String email) throws ExecutionException, InterruptedException {
        QuerySnapshot snapshot = getFirestore().collection("users")
                .whereEqualTo("email", email)
                .get()
                .get();
        return snapshot != null && !snapshot.isEmpty();
    }

    public ResponseEntity<?> deleteUser(String email) throws ExecutionException, InterruptedException {
        if (!emailExists(email)) {
            throw new UserNotFoundException("User does not exist with email: " + email);
        }

        User user = findByEmail(email);
        getFirestore().collection("users").document(user.getId()).delete().get();

        return ResponseEntity.ok().body("User deleted successfully");
    }

    /**
     * Create a password reset token, save it on the user document and send a reset link email.
     * Token expiry is set to 1 hour by default.
     */
    public void requestPasswordReset(String email) throws ExecutionException, InterruptedException {
        if (!emailExists(email)) {
            throw new UserNotFoundException("User not found with email: " + email);
        }

        String token = UUID.randomUUID().toString();
        long expiry = System.currentTimeMillis() + 3600_000L; // 1 hour

        // find user document reference
        QuerySnapshot snapshot = getFirestore().collection("users").whereEqualTo("email", email).get().get();
        if (snapshot.isEmpty()) throw new UserNotFoundException("User not found");
        DocumentReference docRef = snapshot.getDocuments().get(0).getReference();

        Map<String, Object> updates = new HashMap<>();
        updates.put("passwordResetToken", token);
        updates.put("passwordResetExpiry", expiry);

        docRef.set(updates, SetOptions.merge()).get();

        // Build reset link -> frontend should have a route that accepts token & email
        String resetLink = frontendUrl + "/auth/reset-password?token=" +
                URLEncoder.encode(token, StandardCharsets.UTF_8) +
                "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);

        String html = "<p>Hi,</p>" +
                "<p>We received a request to reset your password. Click the link below to reset it (valid for 1 hour):</p>" +
                "<p><a href=\"" + resetLink + "\">Reset your password</a></p>" +
                "<p>If you didn't request this, you can safely ignore this email.</p>";

        try {
            brevoEmailService.sendEmail(email, "Reset your Skiller Classes password", html);
        } catch (Exception e) {
            // Log and continue — don't fail the reset request if the email provider is down
            System.err.println("Failed to send password reset email to " + email + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reset a user's password using a valid reset token.
     */
    public void resetPassword(String token, String newPassword) throws ExecutionException, InterruptedException {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing token");
        if (newPassword == null || newPassword.length() < 6) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");

        QuerySnapshot snapshot = getFirestore().collection("users").whereEqualTo("passwordResetToken", token).get().get();
        if (snapshot.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");

        DocumentSnapshot doc = snapshot.getDocuments().get(0);
        Map<String, Object> data = doc.getData();
        if (data == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token data");

        Object expiryObj = data.get("passwordResetExpiry");
        long expiry = 0L;
        if (expiryObj instanceof Number) expiry = ((Number) expiryObj).longValue();
        else if (expiryObj instanceof String) expiry = Long.parseLong((String) expiryObj);

        if (System.currentTimeMillis() > expiry) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token has expired");
        }

        // update password and clear token fields
        String encoded = encoder.encode(newPassword);
        Map<String, Object> updates = new HashMap<>();
        updates.put("password", encoded);
        updates.put("passwordResetToken", null);
        updates.put("passwordResetExpiry", null);

        doc.getReference().set(updates, SetOptions.merge()).get();
    }

    @Async
    public void sendWelcomeEmailAsync(String email, String name) {
        try {
            Resource resource = resourceLoader.getResource("classpath:templates/email/welcome-email.html");
            String html = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            html = html.replace("{{name}}", name);

            brevoEmailService.sendEmail(email, "Welcome Email", html);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load email template", e);
        }
    }

    // ✅ Safe parsing of numeric types (firestore stores as Double/Long/String inconsistently)
    private Map<String, Integer> getUserCourseProgress(String userId, List<String> purchasedCourses) throws ExecutionException, InterruptedException {
        Map<String, Integer> result = new HashMap<>();
        Firestore db = getFirestore();

        // Defensive: if purchasedCourses is null or empty, return empty map
        if (purchasedCourses == null || purchasedCourses.isEmpty()) {
            return result;
        }

        for (String courseId : purchasedCourses) {
            if (courseId == null) continue; // skip null entries
            String docId = userId + "_" + courseId;
            DocumentSnapshot docSnapshot = db.collection("userCourseProgress").document(docId).get().get();

            if (docSnapshot.exists() && docSnapshot.contains("totalPercentage")) {
                Object rawPercentage = docSnapshot.get("totalPercentage");
                double value = 0.0;

                if (rawPercentage instanceof Number) {
                    value = ((Number) rawPercentage).doubleValue();
                } else if (rawPercentage instanceof String) {
                    try {
                        value = Double.parseDouble((String) rawPercentage);
                    } catch (NumberFormatException e) {
                        value = 0.0; // fallback
                    }
                }

                result.put(courseId, (int) Math.round(value));
            }
        }

        return result;
    }

    public boolean checkPassword(String raw, String encoded) {
        return encoder.matches(raw, encoded);
    }

    // ✅ Validation helpers
    private void validateSignupRequest(SignupRequest req) {
        if (req == null || req.getEmail() == null || req.getUserName() == null || req.getPassword() == null) {
            throw new IllegalArgumentException("All fields are required.");
        }

        if (!req.getEmail().matches("^[\\w-.]+@[\\w-]+\\.[a-z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email format.");
        }

        if (req.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }
    }

    private void validateLoginRequest(LoginRequest req) {
        if (req == null || req.getEmail() == null || req.getPassword() == null) {
            throw new IllegalArgumentException("Email and password are required.");
        }
    }
}
