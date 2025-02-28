package com.example.slash;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TOKEN_URL = "https://slash-worker.primordiagod.workers.dev/";
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        fetchToken();
    }

    private void fetchToken() {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, TOKEN_URL, null,
            response -> {
                try {
                    String token = response.getString("token");
                    String expiration = response.getString("expiration");
                    long expirationTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                        .parse(expiration).getTime();

                    Log.d("Token", "Generated: " + token + ", Expires: " + expiration);
                    if (System.currentTimeMillis() < expirationTime) {
                        // Store in Firestore
                        Map<String, Object> tokenData = new HashMap<>();
                        tokenData.put("token", token);
                        tokenData.put("expiration", expirationTime);
                        db.collection("tokens").document(token).set(tokenData)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Token valid: " + token, Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(MainActivity.this, HomeActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e -> Log.e("Firestore", "Error: " + e.getMessage()));
                    } else {
                        Toast.makeText(this, "Token expired", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e("TokenError", "Parsing failed: " + e.getMessage());
                }
            }, error -> Log.e("TokenError", "Fetch failed: " + error.toString()));
        queue.add(request);
    }
}