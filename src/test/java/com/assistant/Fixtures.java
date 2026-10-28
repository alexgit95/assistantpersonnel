package com.assistant;

import com.assistant.config.AssistantProperties;
import com.assistant.config.AssistantProperties.Backend;
import java.util.List;

/** Donnees de test partagees. */
public final class Fixtures {

    private Fixtures() {
    }

    public static AssistantProperties properties() {
        return properties(0);
    }

    public static AssistantProperties properties(int maxProfiles) {
        return new AssistantProperties(
                new AssistantProperties.Session(10, 30, 60),
                new AssistantProperties.Scan(1000, 5000),
                new AssistantProperties.Recognition(0.55, 0.05, 2, 224, 0.5, 80, 640, Backend.AUTO, false),
                new AssistantProperties.Enrollment(5, 8, maxProfiles),
                new AssistantProperties.Weather(30, 5),
                new AssistantProperties.Ui(true, 25),
                new AssistantProperties.Backup(50),
                new AssistantProperties.FaceAssets(AssistantProperties.FaceAssetMode.CDN,
                    "https://cdn.jsdelivr.net/npm/@vladmandic/face-api@1.7.15/dist/face-api.js",
                    "https://cdn.jsdelivr.net/npm/@vladmandic/face-api@1.7.15/model",
                    "https://cdn.jsdelivr.net/npm/@tensorflow/tfjs-backend-wasm@4.22.0/dist/tf-backend-wasm.js",
                    "https://cdn.jsdelivr.net/npm/@tensorflow/tfjs-backend-wasm@4.22.0/dist/"),
                new AssistantProperties.Prim("", 750, 15, "22:00", "05:00"));
    }

    /** Vecteur de 128 valeurs ; deux bases differentes de 1.0 donnent une distance d'environ 11. */
    public static float[] vector(float base) {
        float[] v = new float[128];
        for (int i = 0; i < v.length; i++) {
            v[i] = base + i * 0.0001f;
        }
        return v;
    }

    public static List<Double> list(float[] vector) {
        return com.assistant.profile.EmbeddingCodec.toList(vector);
    }

    public static List<float[]> vectors(int count, float base) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> vector(base + i * 0.0001f)).toList();
    }

    public static String avatar() {
        return "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD=";
    }
}
