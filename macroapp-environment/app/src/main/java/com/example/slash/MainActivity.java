package com.example.slash;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private EditText tokenInput;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize UI elements
        tokenInput = findViewById(R.id.token_input);
        submitButton = findViewById(R.id.submit_button);

        submitButton.setOnClickListener(v -> validateToken());
    }

    private void validateToken() {
        String token = tokenInput.getText().toString().trim();
        if (token.isEmpty()) {
            Toast.makeText(this, "Please enter a token", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("tokens").document(token).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long expiration = documentSnapshot.getLong("expiration");
                        if (expiration != null && expiration > new Date().getTime()) {
                            Toast.makeText(this, "Token valid", Toast.LENGTH_SHORT).show();
                            updateUI();
                        } else {
                            Toast.makeText(this, "Token expired", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Invalid token", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateUI() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
