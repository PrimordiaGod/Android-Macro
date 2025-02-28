import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import android.util.Log;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static final String TOKEN_URL = "https://slash-worker.primordiagod.workers.dev/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Fetch token when app starts (or tie to a button)
        fetchToken();
    }

    private void fetchToken() {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, TOKEN_URL, null,
            response -> {
                try {
                    String token = response.getString("token");
                    String expiration = response.getString("expiration");
                    Log.d("Token", "Generated: " + token + ", Expires: " + expiration);

                    // Example: Redirect if token is valid
                    if (System.currentTimeMillis() < new Date(expiration).getTime()) {
                        startActivity(new Intent(MainActivity.this, HomeActivity.class));
                        finish();
                    }
                } catch (Exception e) {
                    Log.e("TokenError", "Parsing failed: " + e.getMessage());
                }
            }, error -> Log.e("TokenError", "Fetch failed: " + error.toString()));
        queue.add(request);
    }
}
