package com.videowebsite.VideoWebsite.contact;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
public class ContactService {

    private Firestore getFirestore() { return FirestoreClient.getFirestore(); }

    public Contact save(Contact contact) {
        try {
            DocumentReference docRef = getFirestore().collection("contacts").document(contact.getId());
            docRef.set(contact).get();
            return contact;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to save contact to Firestore", e);
        }
    }
}
