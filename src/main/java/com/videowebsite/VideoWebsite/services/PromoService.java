package com.videowebsite.VideoWebsite.services;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.videowebsite.VideoWebsite.Entities.PromoCode;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class PromoService {

    private Firestore getFirestore() {
        return FirestoreClient.getFirestore();
    }

    public PromoCode getPromo(String code) throws ExecutionException, InterruptedException {
        Firestore db = getFirestore();
        DocumentReference ref = db.collection("promoCodes").document(code.toUpperCase());
        DocumentSnapshot snap = ref.get().get();
        if (!snap.exists()) return null;
        return snap.toObject(PromoCode.class);
    }

    public ValidationResult validateForCourseAndUser(String code, String courseId, String userEmail) throws ExecutionException, InterruptedException {
        if (code == null || code.trim().isEmpty()) return new ValidationResult(false, "No promo code provided");
        PromoCode promo = getPromo(code);
        if (promo == null) return new ValidationResult(false, "Promo code not found");
        if (promo.getActive() == null || !promo.getActive()) return new ValidationResult(false, "Promo code is not active");
        if (promo.getExpiresAt() != null && promo.getExpiresAt() < System.currentTimeMillis()) return new ValidationResult(false, "Promo code expired");
        if (promo.getAppliesToCourseId() != null && !promo.getAppliesToCourseId().equals(courseId)) return new ValidationResult(false, "Promo code does not apply to this course");
        if (promo.getMaxUses() != null && promo.getUses() != null && promo.getUses() >= promo.getMaxUses()) return new ValidationResult(false, "Promo code has been fully redeemed");

        // If single use per user, check promoUsages for existing usage
        if (Boolean.TRUE.equals(promo.getSingleUsePerUser())) {
            Firestore db = getFirestore();
            var q = db.collection("promoUsages").whereEqualTo("promoCode", code.toUpperCase()).whereEqualTo("userEmail", userEmail).get().get();
            if (q != null && !q.isEmpty()) return new ValidationResult(false, "Promo code already used by this user");
        }

        return new ValidationResult(true, "Valid", promo);
    }

    // Atomically record promo usage and increment uses counter; returns true if applied
    public boolean applyUsageAtomic(String code, String userId, String userEmail, String reason, String courseId) throws Exception {
        Firestore db = getFirestore();
        String codeKey = code.toUpperCase();
        DocumentReference promoRef = db.collection("promoCodes").document(codeKey);

        ApiFuture<Void> txFuture = db.runTransaction(transaction -> {
            DocumentSnapshot promoSnap = transaction.get(promoRef).get();
            if (!promoSnap.exists()) {
                throw new Exception("Promo does not exist");
            }
            PromoCode promo = promoSnap.toObject(PromoCode.class);
            if (promo == null) throw new Exception("Invalid promo record");
            if (promo.getActive() == null || !promo.getActive()) throw new Exception("Promo not active");
            if (promo.getExpiresAt() != null && promo.getExpiresAt() < System.currentTimeMillis()) throw new Exception("Promo expired");
            if (promo.getAppliesToCourseId() != null && courseId != null && !promo.getAppliesToCourseId().equals(courseId)) throw new Exception("Promo not applicable for this course");
            Integer max = promo.getMaxUses();
            Integer uses = promo.getUses() == null ? 0 : promo.getUses();
            if (max != null && uses >= max) throw new Exception("Promo out of uses");

            // If single use per user, ensure not already used
            if (Boolean.TRUE.equals(promo.getSingleUsePerUser())) {
                var existing = db.collection("promoUsages").whereEqualTo("promoCode", codeKey).whereEqualTo("userEmail", userEmail).get().get();
                if (existing != null && !existing.isEmpty()) throw new Exception("Promo already used by user");
            }

            // increment uses
            int newUses = uses + 1;
            Map<String, Object> promoUpdates = new HashMap<>();
            promoUpdates.put("uses", newUses);
            transaction.update(promoRef, promoUpdates);

            // create usage record
            Map<String, Object> usage = new HashMap<>();
            usage.put("promoCode", codeKey);
            usage.put("userId", userId);
            usage.put("userEmail", userEmail);
            usage.put("reason", reason);
            usage.put("courseId", courseId);
            usage.put("appliedAt", new Date());

            DocumentReference usageRef = db.collection("promoUsages").document();
            transaction.set(usageRef, usage);

            return null;
        });

        txFuture.get();
        return true;
    }

    public static class ValidationResult {
        public boolean valid;
        public String message;
        public PromoCode promo;

        public ValidationResult(boolean v, String m) { this.valid = v; this.message = m; }
        public ValidationResult(boolean v, String m, PromoCode p) { this.valid = v; this.message = m; this.promo = p; }
    }
}
