package com.example.slash;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.io.FileInputStream;
import android.content.res.AssetFileDescriptor;
import java.util.HashMap;
import java.util.Map;

public class AIImageRecognizer {
    private static final String TAG = "AIImageRecognizer";
    private static final int INPUT_WIDTH = 224;
    private static final int INPUT_HEIGHT = 224;
    private static final int PIXEL_SIZE = 3; // RGB channels
    private static final int BATCH_SIZE = 1;
    private static final int FLOAT_SIZE_BYTES = 4;
    
    private Interpreter staminaInterpreter;
    private Interpreter enemyInterpreter;
    private Interpreter itemInterpreter;
    
    private Map<Integer, String> staminaLabels;
    private Map<Integer, String> enemyLabels;
    private Map<Integer, String> itemLabels;
    
    public AIImageRecognizer() {
        initLabels();
    }
    
    public void initModels(Context context) {
        try {
            // Load models from assets
            MappedByteBuffer staminaModel = loadModelFile(context, "stamina_model.tflite");
            MappedByteBuffer enemyModel = loadModelFile(context, "enemy_model.tflite");
            MappedByteBuffer itemModel = loadModelFile(context, "item_model.tflite");
            
            // Initialize interpreters
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            
            staminaInterpreter = new Interpreter(staminaModel, options);
            enemyInterpreter = new Interpreter(enemyModel, options);
            itemInterpreter = new Interpreter(itemModel, options);
            
            Log.d(TAG, "TensorFlow Lite models loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load TensorFlow Lite models", e);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing models", e);
        }
    }
    
    private void initLabels() {
        // Initialize label maps
        staminaLabels = new HashMap<>();
        staminaLabels.put(0, "FULL");
        staminaLabels.put(1, "LOW");
        staminaLabels.put(2, "EMPTY");
        
        enemyLabels = new HashMap<>();
        enemyLabels.put(0, "NO_ENEMY");
        enemyLabels.put(1, "WEAK_ENEMY");
        enemyLabels.put(2, "STRONG_ENEMY");
        enemyLabels.put(3, "BOSS");
        
        itemLabels = new HashMap<>();
        itemLabels.put(0, "NO_ITEM");
        itemLabels.put(1, "HEALTH_POTION");
        itemLabels.put(2, "STAMINA_POTION");
        itemLabels.put(3, "TREASURE");
    }
    
    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    public String recognizeStamina(Bitmap bitmap) {
        if (staminaInterpreter == null) {
            return "MODEL_NOT_LOADED";
        }
        
        float[][] result = runInference(bitmap, staminaInterpreter, 3);
        int maxIndex = getMaxIndex(result[0]);
        return staminaLabels.get(maxIndex);
    }
    
    public String recognizeEnemy(Bitmap bitmap) {
        if (enemyInterpreter == null) {
            return "MODEL_NOT_LOADED";
        }
        
        float[][] result = runInference(bitmap, enemyInterpreter, 4);
        int maxIndex = getMaxIndex(result[0]);
        return enemyLabels.get(maxIndex);
    }
    
    public String recognizeItem(Bitmap bitmap) {
        if (itemInterpreter == null) {
            return "MODEL_NOT_LOADED";
        }
        
        float[][] result = runInference(bitmap, itemInterpreter, 4);
        int maxIndex = getMaxIndex(result[0]);
        return itemLabels.get(maxIndex);
    }
    
    private float[][] runInference(Bitmap bitmap, Interpreter interpreter, int numClasses) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true);
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);
        float[][] outputBuffer = new float[1][numClasses];
        
        interpreter.run(inputBuffer, outputBuffer);
        return outputBuffer;
    }
    
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(
                BATCH_SIZE * INPUT_WIDTH * INPUT_HEIGHT * PIXEL_SIZE * FLOAT_SIZE_BYTES);
        byteBuffer.order(ByteOrder.nativeOrder());
        
        int[] pixels = new int[INPUT_WIDTH * INPUT_HEIGHT];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        
        int pixel = 0;
        for (int i = 0; i < INPUT_WIDTH; ++i) {
            for (int j = 0; j < INPUT_HEIGHT; ++j) {
                final int val = pixels[pixel++];
                
                // Normalize pixel values to [-1,1]
                byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f * 2 - 1.0f);
                byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f * 2 - 1.0f);
                byteBuffer.putFloat((val & 0xFF) / 255.0f * 2 - 1.0f);
            }
        }
        
        return byteBuffer;
    }
    
    private int getMaxIndex(float[] array) {
        int maxIndex = 0;
        float maxValue = array[0];
        
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        
        return maxIndex;
    }
    
    // Color-based recognition (as fallback when models aren't available)
    public String analyzeColorPattern(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        int redCount = 0;
        int greenCount = 0;
        int blueCount = 0;
        int totalPixels = width * height;
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = bitmap.getPixel(x, y);
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);
                
                if (red > green && red > blue && red > 150) {
                    redCount++;
                } else if (green > red && green > blue && green > 150) {
                    greenCount++;
                } else if (blue > red && blue > green && blue > 150) {
                    blueCount++;
                }
            }
        }
        
        float redRatio = (float) redCount / totalPixels;
        float greenRatio = (float) greenCount / totalPixels;
        float blueRatio = (float) blueCount / totalPixels;
        
        if (redRatio > 0.4) {
            return "DANGER";
        } else if (greenRatio > 0.4) {
            return "GOOD";
        } else if (blueRatio > 0.4) {
            return "SPECIAL";
        } else {
            return "NEUTRAL";
        }
    }
    
    // Template matching for simple object detection
    public boolean detectObjectByTemplate(Bitmap source, Bitmap template, double threshold) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int templateWidth = template.getWidth();
        int templateHeight = template.getHeight();
        
        // Simple template matching using normalized cross-correlation
        double maxCorrelation = 0;
        
        for (int x = 0; x <= sourceWidth - templateWidth; x++) {
            for (int y = 0; y <= sourceHeight - templateHeight; y++) {
                double correlation = calculateCorrelation(source, template, x, y);
                if (correlation > maxCorrelation) {
                    maxCorrelation = correlation;
                }
            }
        }
        
        return maxCorrelation >= threshold;
    }
    
    private double calculateCorrelation(Bitmap source, Bitmap template, int offsetX, int offsetY) {
        int templateWidth = template.getWidth();
        int templateHeight = template.getHeight();
        long sum = 0;
        long templateSum = 0;
        
        for (int x = 0; x < templateWidth; x++) {
            for (int y = 0; y < templateHeight; y++) {
                int sourcePixel = source.getPixel(x + offsetX, y + offsetY);
                int templatePixel = template.getPixel(x, y);
                
                int sourceGray = (Color.red(sourcePixel) + Color.green(sourcePixel) + Color.blue(sourcePixel)) / 3;
                int templateGray = (Color.red(templatePixel) + Color.green(templatePixel) + Color.blue(templatePixel)) / 3;
                
                sum += sourceGray * templateGray;
                templateSum += templateGray * templateGray;
            }
        }
        
        return (double) sum / Math.sqrt(templateSum);
    }
}