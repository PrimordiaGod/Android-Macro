package com.example.slash;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private EditText tokenInput;
    private EditText expirationInput;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        tokenInput = findViewById(R.id.token_input);
        expirationInput = findViewById(R.id.expiration_input);
        submitButton = findViewById(R.id.submit_button);

        submitButton.setOnClickListener(v -> {
            String token = tokenInput.getText().toString().trim();
            String expiration = expirationInput.getText().toString().trim();
            if (!token.isEmpty() && !expiration.isEmpty()) {
                validateAndStoreToken(token, expiration);
            } else {
                Toast.makeText(this, "Please enter both token and expiration", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void validateAndStoreToken(String token, String expiration) {
        try {
            long expirationTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .parse(expiration).getTime();
            long currentTime = System.currentTimeMillis();

            if (currentTime < expirationTime) {
                Map<String, Object> tokenData = new HashMap<>();
                tokenData.put("token", token);
                tokenData.put("expiration", expirationTime);

                db.collection("tokens").document(token).set(tokenData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Access granted!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(MainActivity.this, HomeActivity.class));
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error storing token: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
            } else {
                Toast.makeText(this, "Token has expired", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Invalid expiration format: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}