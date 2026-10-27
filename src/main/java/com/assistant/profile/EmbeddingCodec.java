package com.assistant.profile;

import com.assistant.common.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Conversion et comparaison des empreintes faciales (vecteurs de 128 flottants stockes en texte JSON). */
public final class EmbeddingCodec {

    public static final int SIZE = 128;
    private static final double MAX_ABS_VALUE = 5.0;

    private EmbeddingCodec() {
    }

    public static float[] fromList(List<Double> values) {
        if (values == null || values.size() != SIZE) {
            throw invalid();
        }
        float[] vector = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            Double value = values.get(i);
            if (value == null || !Double.isFinite(value) || Math.abs(value) > MAX_ABS_VALUE) {
                throw invalid();
            }
            vector[i] = value.floatValue();
        }
        return vector;
    }

    public static String encode(float[] vector) {
        StringBuilder sb = new StringBuilder(SIZE * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    public static float[] decode(String json) {
        String body = json.trim();
        if (body.length() < 2 || body.charAt(0) != '[' || body.charAt(body.length() - 1) != ']') {
            throw new IllegalArgumentException("Empreinte illisible");
        }
        String[] parts = body.substring(1, body.length() - 1).split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    public static List<Double> toList(float[] vector) {
        Double[] values = new Double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            values[i] = (double) vector[i];
        }
        return List.of(values);
    }

    public static double distance(float[] a, float[] b) {
        if (a.length != b.length) {
            return Double.MAX_VALUE;
        }
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = (double) a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "Empreinte faciale invalide : " + SIZE + " valeurs numeriques attendues.");
    }
}
