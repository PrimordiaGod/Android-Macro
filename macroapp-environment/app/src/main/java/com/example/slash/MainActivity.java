package com.example.slash;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private EditText tokenInput;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        tokenInput = findViewById(R.id.token_input);
        submitButton = findViewById(R.id.submit_button);

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
            // Parse the JSON token response
            JSONObject json = new JSONObject(tokenJson);
            String token = json.getString("token");
            String expiration = json.getString("expiration");

            // Parse the expiration date
            long expirationTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .parse(expiration).getTime();
            long currentTime = System.currentTimeMillis();

            if (currentTime >= expirationTime) {
                Toast.makeText(this, "Token has expired", Toast.LENGTH_LONG).show();
                return;
            }

            // Check if token exists in Firestore
            db.collection("tokens").document(token).get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            long storedExpiration = document.getLong("expiration");
                            if (currentTime < storedExpiration) {
                                Toast.makeText(this, "Access granted!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(MainActivity.this, HomeActivity.class));
                                finish();
                            } else {
                                Toast.makeText(this, "Token has expired", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(this, "Invalid token", Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error validating token: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });

        } catch (JSONException e) {
            Toast.makeText(this, "Invalid token format: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error processing token: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}