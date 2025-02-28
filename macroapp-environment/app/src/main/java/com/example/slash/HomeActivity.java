package com.example.slash;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText profileNameInput;
    private EditText macroSettingsInput;
    private Button saveNameButton;
    private Button saveSettingsButton;
    private Button signOutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        profileNameInput = findViewById(R.id.profile_name_input);
        macroSettingsInput = findViewById(R.id.macro_settings_input);
        saveNameButton = findViewById(R.id.save_name_button);
        saveSettingsButton = findViewById(R.id.save_settings_button);
        signOutButton = findViewById(R.id.sign_out_button);

        // Load existing profile data
        loadProfile();

        // Save name
        saveNameButton.setOnClickListener(v -> {
            String name = profileNameInput.getText().toString().trim();
            if (!name.isEmpty()) {
                saveProfileField("name", name);
            } else {
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
            }
        });

        // Save macro settings
        saveSettingsButton.setOnClickListener(v -> {
            String settings = macroSettingsInput.getText().toString().trim();
            if (!settings.isEmpty()) {
                saveProfileField("macro_settings", settings);
            } else {
                Toast.makeText(this, "Please enter macro settings", Toast.LENGTH_SHORT).show();
            }
        });

        // Sign out
        signOutButton.setOnClickListener(v -> signOut());
    }

    private void loadProfile() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    String name = document.getString("name");
                    String settings = document.getString("macro_settings");
                    if (name != null) profileNameInput.setText(name);
                    if (settings != null) macroSettingsInput.setText(settings);
                }
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to load profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    private void saveProfileField(String field, String value) {
        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> profileData = new HashMap<>();
        profileData.put(field, value);

        db.collection("users").document(uid).set(profileData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(this, field.equals("name") ? "Name saved!" : "Settings saved!", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to save " + field + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
    }

    private void signOut() {
        mAuth.signOut();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}