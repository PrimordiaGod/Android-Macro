package com.example.slash;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private EditText tokenInput;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        tokenInput = findViewById(R.id.token_input);
        submitButton = findViewById(R.id.submit_button);

        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Attempting anonymous sign-in...", Toast.LENGTH_SHORT).show();
            mAuth.signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        Toast.makeText(this, "Sign-in successful", Toast.LENGTH_SHORT).show();
                        setupUI();
                    })
                    .addOnFailureListener(e -> {
                        String errorMessage = "Authentication failed: " + e.getMessage();
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                        finish();
                    });
        } else {
            setupUI();
        }
    }

    private void setupUI() {
        Toast.makeText(this, "UI setup complete", Toast.LENGTH_SHORT).show();
        submitButton.setOnClickListener(v -> {
            String tokenJson = tokenInput.getText().toString().trim();
            if (!tokenJson.isEmpty()) {
                validateToken(tokenJson);
            } else {
                Toast.makeText(this, "Please enter a token", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void validateToken(String tokenJson) {
        try {
            JSONObject json = new JSONObject(tokenJson);
            json.getString("token"); // Ensure token field exists
            String expiration = json.getString("expiration");

            long expirationTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .parse(expiration).getTime();
            long currentTime = System.currentTimeMillis();

            if (currentTime < expirationTime) {
                Toast.makeText(this, "Access granted!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(MainActivity.this, HomeActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Token has expired", Toast.LENGTH_LONG).show();
            }
        } catch (JSONException e) {
            Toast.makeText(this, "Invalid token format: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error processing token: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}