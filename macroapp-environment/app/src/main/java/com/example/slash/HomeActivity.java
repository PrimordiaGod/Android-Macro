package com.example.slash;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView macroStatus;
    private Button recordButton, replayButton, cropButton, actionButton1, actionButton2;
    private boolean isRecording = false;
    private List<Integer> recordedActions = new ArrayList<>();
    private Bitmap croppedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setTitle("Slash");
        Toast.makeText(this, "Welcome to Slash", Toast.LENGTH_SHORT).show();

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        macroStatus = findViewById(R.id.macro_status);
        recordButton = findViewById(R.id.record_button);
        replayButton = findViewById(R.id.replay_button);
        cropButton = findViewById(R.id.crop_button);
        actionButton1 = findViewById(R.id.action_button_1);
        actionButton2 = findViewById(R.id.action_button_2);

        setupNavigationDrawer();
        setupMacroControls();
    }

    private void setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_name) {
                showNameDialog();
            } else if (itemId == R.id.nav_macro_settings) {
                showSettingsDialog();
            } else if (itemId == R.id.nav_sign_out) {
                signOut();
            }
            drawerLayout.closeDrawers();
            return true;
        });
    }

    private void setupMacroControls() {
        recordButton.setOnClickListener(v -> {
            if (!isRecording) startRecording();
            else stopRecording();
        });

        replayButton.setOnClickListener(v -> replayMacroWithTrigger());

        cropButton.setOnClickListener(v -> startCropping());

        actionButton1.setOnClickListener(v -> {
            if (isRecording) recordedActions.add(R.id.action_button_1);
            else Toast.makeText(this, "Action 1", Toast.LENGTH_SHORT).show();
        });

        actionButton2.setOnClickListener(v -> {
            if (isRecording) recordedActions.add(R.id.action_button_2);
            else Toast.makeText(this, "Action 2", Toast.LENGTH_SHORT).show();
        });
    }

    private void startRecording() {
        isRecording = true;
        recordedActions.clear();
        macroStatus.setText("Macro Status: Recording");
        recordButton.setText("Stop Recording");
        Log.d(TAG, "Started recording macro");
    }

    private void stopRecording() {
        isRecording = false;
        macroStatus.setText("Macro Status: Idle");
        recordButton.setText("Record Macro");
        Log.d(TAG, "Stopped recording macro with " + recordedActions.size() + " actions");
    }

    private void replayMacroWithTrigger() {
        if (recordedActions.isEmpty()) {
            Toast.makeText(this, "No macro recorded", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Replay attempted with no actions");
            return;
        }
        if (croppedImage == null) {
            Toast.makeText(this, "Crop an area first to set a trigger", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Replay attempted without cropped trigger");
            return;
        }
        macroStatus.setText("Macro Status: Replaying with Trigger");
        Log.d(TAG, "Replaying macro with " + recordedActions.size() + " actions and trigger");
        new Thread(() -> {
            boolean running = true;
            int clickIndex = 0;
            while (running && clickIndex < recordedActions.size()) {
                // Placeholder screen check (to be replaced with real capture)
                Bitmap currentScreen = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
                if (currentScreen.getWidth() == croppedImage.getWidth()) { // Placeholder match
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Trigger detected, stopping", Toast.LENGTH_SHORT).show();
                        macroStatus.setText("Macro Status: Idle");
                    });
                    running = false;
                } else {
                    int id = recordedActions.get(clickIndex);
                    runOnUiThread(() -> {
                        if (id == R.id.action_button_1) actionButton1.performClick();
                        else if (id == R.id.action_button_2) actionButton2.performClick();
                    });
                    clickIndex++;
                    try {
                        Thread.sleep(1000); // 1-second delay
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Replay interrupted", e);
                    }
                }
            }
            runOnUiThread(() -> macroStatus.setText("Macro Status: Idle"));
        }).start();
    }

    private void startCropping() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Crop Screen");
        builder.setMessage("Select an area (simulated for now)");
        builder.setPositiveButton("Crop", (dialog, which) -> {
            croppedImage = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888); // Placeholder
            Toast.makeText(this, "Area cropped", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Cropped area set as trigger");
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Profile Name");
        final EditText input = new EditText(this);
        input.setText(getCurrentUserName());
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) saveProfileField("name", name);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Macro Settings");
        final EditText input = new EditText(this);
        input.setText(getCurrentUserSettings());
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String settings = input.getText().toString().trim();
            if (!settings.isEmpty()) saveProfileField("macro_settings", settings);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private String getCurrentUserName() {
        return ""; // Placeholder; fetch from Firestore if needed
    }

    private String getCurrentUserSettings() {
        return ""; // Placeholder; fetch from Firestore if needed
    }

    private void saveProfileField(String field, String value) {
        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> profileData = new HashMap<>();
        profileData.put(field, value);
        db.collection("users").document(uid).set(profileData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, field.equals("name") ? "Name saved!" : "Settings saved!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, field + " saved for UID: " + uid);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save " + field + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Failed to save " + field, e);
                });
    }

    private void signOut() {
        mAuth.signOut();
        startActivity(new Intent(this, MainActivity.class));
        finish();
        Log.d(TAG, "User signed out");
    }
}