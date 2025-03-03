package com.example.slash;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 100;
    private static final int INPUT_WIDTH = 224; // Adjust based on your TensorFlow Lite model
    private static final int INPUT_HEIGHT = 224;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private TextView macroStatus;
    private Button recordButton, replayButton, cropButton, saveMacroButton, loadMacroButton;
    private LinearLayout macroArea;
    private boolean isRecording = false;
    private List<Action> recordedActions = new ArrayList<>();
    private Bitmap triggerImage;
    private int triggerX, triggerY, triggerWidth, triggerHeight;
    private int staminaX, staminaY, staminaWidth, staminaHeight;
    private AIImageRecognizer aiRecognizer;
    private float triggerSensitivity = 0.8f; // Default
    private long clickInterval = 1000; // Default 1s
    private volatile GameState currentState = GameState.IDLE;

    // TensorFlow Lite
    private Interpreter tflite;

    // Screen Capture
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private enum GameState {
        STAMINA_FULL, STAMINA_LOW, STAMINA_EMPTY, IN_BATTLE, IDLE
    }

    private static class Action {
        float x, y;
        long timestamp;

        Action(float x, float y, long timestamp) {
            this.x = x;
            this.y = y;
            this.timestamp = timestamp;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("x", x);
            map.put("y", y);
            map.put("timestamp", timestamp);
            return map;
        }

        static Action fromMap(Map<String, Object> map) {
            float x = ((Double) map.get("x")).floatValue();
            float y = ((Double) map.get("y")).floatValue();
            long timestamp = (Long) map.get("timestamp");
            return new Action(x, y, timestamp);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setTitle("Slash");
        Toast.makeText(this, "Welcome to Slash", Toast.LENGTH_SHORT).show();

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        aiRecognizer = new AIImageRecognizer();

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        macroStatus = findViewById(R.id.macro_status);
        recordButton = findViewById(R.id.record_button);
        replayButton = findViewById(R.id.replay_button);
        cropButton = findViewById(R.id.crop_button);
        saveMacroButton = findViewById(R.id.save_macro_button);
        loadMacroButton = findViewById(R.id.load_macro_button);
        macroArea = findViewById(R.id.macro_area);

        initModel();
        setupScreenCapture();
        setupNavigationDrawer();
        setupMacroControls();
    }

    private void initModel() {
        try {
            MappedByteBuffer modelFile = loadModelFile("stamina_model.tflite");
            tflite = new Interpreter(modelFile);
            Log.d(TAG, "TensorFlow Lite model loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load TensorFlow Lite model", e);
            Toast.makeText(this, "Failed to load AI model", Toast.LENGTH_LONG).show();
        }
    }

    private MappedByteBuffer loadModelFile(String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void setupScreenCapture() {
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            setupVirtualDisplay();
        }
    }

    private void setupVirtualDisplay() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("MacroCapture",
                width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    private Bitmap captureScreenArea(int x, int y, int width, int height) {
        if (imageReader == null) return null;
        Image image = imageReader.acquireLatestImage();
        if (image == null) return null;
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        image.close();
        return Bitmap.createBitmap(bitmap, x, y, width, height);
    }

    private float[] runInference(Bitmap bitmap) {
        if (tflite == null) return new float[]{0, 0, 0};
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true);
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resized);
        float[][] output = new float[1][3]; // Assuming 3 classes: full, low, empty
        tflite.run(inputBuffer, output);
        return output[0];
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_WIDTH * INPUT_HEIGHT * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_WIDTH * INPUT_HEIGHT];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < INPUT_WIDTH; ++i) {
            for (int j = 0; j < INPUT_HEIGHT; ++j) {
                int val = intValues[pixel++];
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f); // R
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);  // G
                byteBuffer.putFloat((val & 0xFF) / 255.0f);         // B
            }
        }
        return byteBuffer;
    }

    private void setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_name) showNameDialog();
            else if (itemId == R.id.nav_macro_settings) showSettingsDialog();
            else if (itemId == R.id.nav_sign_out) signOut();
            drawerLayout.closeDrawers();
            return true;
        });
    }

    private void setupMacroControls() {
        recordButton.setOnClickListener(v -> {
            if (!isRecording) startRecording();
            else stopRecording();
        });

        replayButton.setOnClickListener(v -> replayMacroWithMonitoring());

        cropButton.setOnClickListener(v -> startCropping());

        saveMacroButton.setOnClickListener(v -> saveMacroToFirestore());

        loadMacroButton.setOnClickListener(v -> loadMacroFromFirestore());

        macroArea.setOnTouchListener((v, event) -> {
            if (isRecording && event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();
                recordedActions.add(new Action(x, y, System.currentTimeMillis()));
                Toast.makeText(this, "Click recorded at (" + x + ", " + y + ")", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Action recorded: x=" + x + ", y=" + y);
            }
            return true;
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

    private void replayMacroWithMonitoring() {
        if (recordedActions.isEmpty()) {
            Toast.makeText(this, "No macro recorded", Toast.LENGTH_SHORT).show();
            return;
        }
        if (staminaWidth == 0 || staminaHeight == 0) {
            Toast.makeText(this, "Please crop stamina bar area first", Toast.LENGTH_SHORT).show();
            return;
        }
        macroStatus.setText("Macro Status: Replaying with Monitoring");
        startMonitoring();

        Thread replayThread = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            for (Action action : recordedActions) {
                if (!macroStatus.getText().toString().contains("Replaying")) break;
                long elapsed = System.currentTimeMillis() - startTime;
                long delay = action.timestamp - recordedActions.get(0).timestamp;
                if (elapsed < delay) {
                    try {
                        Thread.sleep(delay - elapsed);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Replay interrupted", e);
                    }
                }
                if (currentState == GameState.STAMINA_LOW || currentState == GameState.STAMINA_EMPTY) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Stamina low, pausing macro", Toast.LENGTH_SHORT).show();
                        performLowStaminaAction();
                    });
                    break;
                }
                runOnUiThread(() -> simulateClick(action.x, action.y));
            }
            runOnUiThread(() -> macroStatus.setText("Macro Status: Idle"));
        });
        replayThread.start();
    }

    private void startMonitoring() {
        Thread monitorThread = new Thread(() -> {
            while (macroStatus.getText().toString().contains("Replaying")) {
                Bitmap staminaArea = captureScreenArea(staminaX, staminaY, staminaWidth, staminaHeight);
                if (staminaArea != null) {
                    float[] results = runInference(staminaArea);
                    updateGameState(results);
                    runOnUiThread(() -> macroStatus.setText("Macro Status: " + currentState.name()));
                }
                try {
                    Thread.sleep(500); // Check every 0.5s
                } catch (InterruptedException e) {
                    Log.e(TAG, "Monitor interrupted", e);
                }
            }
        });
        monitorThread.start();
    }

    private void updateGameState(float[] results) {
        int maxIndex = 0;
        for (int i = 1; i < results.length; i++) {
            if (results[i] > results[maxIndex]) maxIndex = i;
        }
        switch (maxIndex) {
            case 0: currentState = GameState.STAMINA_FULL; break;
            case 1: currentState = GameState.STAMINA_LOW; break;
            case 2: currentState = GameState.STAMINA_EMPTY; break;
        }
    }

    private void performLowStaminaAction() {
        simulateClick(200, 200); // Example: Click a "rest" button
        Log.d(TAG, "Performed low stamina action");
    }

    private void startCropping() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.crop_preview, null);
        builder.setTitle("Crop Stamina Bar Area");
        builder.setView(dialogView);

        EditText cropX = dialogView.findViewById(R.id.crop_x);
        EditText cropY = dialogView.findViewById(R.id.crop_y);
        EditText cropWidth = dialogView.findViewById(R.id.crop_width);
        EditText cropHeight = dialogView.findViewById(R.id.crop_height);

        builder.setPositiveButton("Crop", (dialog, which) -> {
            try {
                staminaX = Integer.parseInt(cropX.getText().toString());
                staminaY = Integer.parseInt(cropY.getText().toString());
                staminaWidth = Integer.parseInt(cropWidth.getText().toString());
                staminaHeight = Integer.parseInt(cropHeight.getText().toString());
                Toast.makeText(this, "Stamina area cropped", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Cropped stamina area at (" + staminaX + ", " + staminaY + ") size " + staminaWidth + "x" + staminaHeight);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid cropping values", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Cropping failed", e);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void simulateClick(float x, float y) {
        Toast.makeText(this, "Simulated click at (" + x + ", " + y + ")", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Simulated click at (" + x + ", " + y + ")");
    }

    private void saveMacroToFirestore() {
        if (recordedActions.isEmpty()) {
            Toast.makeText(this, "No macro to save", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> macroData = new HashMap<>();
        List<Map<String, Object>> actionsList = new ArrayList<>();
        for (Action action : recordedActions) {
            actionsList.add(action.toMap());
        }
        macroData.put("actions", actionsList);
        macroData.put("triggerSensitivity", triggerSensitivity);
        macroData.put("clickInterval", clickInterval);
        macroData.put("staminaX", staminaX);
        macroData.put("staminaY", staminaY);
        macroData.put("staminaWidth", staminaWidth);
        macroData.put("staminaHeight", staminaHeight);
        db.collection("users").document(uid).collection("macros").document("default")
                .set(macroData)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Macro saved", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save macro: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void loadMacroFromFirestore() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).collection("macros").document("default")
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        recordedActions.clear();
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> actions = (List<Map<String, Object>>) document.get("actions");
                        for (Map<String, Object> actionMap : actions) {
                            recordedActions.add(Action.fromMap(actionMap));
                        }
                        triggerSensitivity = document.getDouble("triggerSensitivity").floatValue();
                        clickInterval = document.getLong("clickInterval");
                        staminaX = document.getLong("staminaX").intValue();
                        staminaY = document.getLong("staminaY").intValue();
                        staminaWidth = document.getLong("staminaWidth").intValue();
                        staminaHeight = document.getLong("staminaHeight").intValue();
                        Toast.makeText(this, "Macro loaded", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "No saved macro found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load macro: " + e.getMessage(), Toast.LENGTH_LONG).show());
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
        View dialogView = getLayoutInflater().inflate(R.layout.macro_settings_dialog, null);
        builder.setTitle("Macro Settings");
        builder.setView(dialogView);

        EditText sensitivityInput = dialogView.findViewById(R.id.trigger_sensitivity);
        EditText intervalInput = dialogView.findViewById(R.id.click_interval);
        sensitivityInput.setText(String.valueOf(triggerSensitivity));
        intervalInput.setText(String.valueOf(clickInterval));

        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                triggerSensitivity = Float.parseFloat(sensitivityInput.getText().toString());
                clickInterval = Long.parseLong(intervalInput.getText().toString());
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid settings values", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private String getCurrentUserName() {
        return "";
    }

    private void saveProfileField(String field, String value) {
        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> profileData = new HashMap<>();
        profileData.put(field, value);
        db.collection("users").document(uid).set(profileData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> Toast.makeText(this, field.equals("name") ? "Name saved!" : "Settings saved!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save " + field + ": " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void signOut() {
        mAuth.signOut();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
    }
    }