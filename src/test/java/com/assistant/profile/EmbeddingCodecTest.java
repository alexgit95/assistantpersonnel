package com.assistant.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assistant.Fixtures;
import com.assistant.common.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingCodecTest {

    @Test
    void encodeThenDecodeKeepsValues() {
        float[] vector = Fixtures.vector(0.25f);
        assertThat(EmbeddingCodec.decode(EmbeddingCodec.encode(vector))).containsExactly(vector);
    }

    @Test
    void decodeRejectsNonArray() {
        assertThatThrownBy(() -> EmbeddingCodec.decode("0.1,0.2")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromListRejectsWrongSize() {
        assertThatThrownBy(() -> EmbeddingCodec.fromList(List.of(0.1, 0.2))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> EmbeddingCodec.fromList(null)).isInstanceOf(ApiException.class);
    }

    @Test
    void fromListRejectsNonFiniteAndNullValues() {
        List<Double> withNaN = new ArrayList<>(Fixtures.list(Fixtures.vector(0f)));
        withNaN.set(3, Double.NaN);
        assertThatThrownBy(() -> EmbeddingCodec.fromList(withNaN)).isInstanceOf(ApiException.class);

        List<Double> withNull = new ArrayList<>(Fixtures.list(Fixtures.vector(0f)));
        withNull.set(0, null);
        assertThatThrownBy(() -> EmbeddingCodec.fromList(withNull)).isInstanceOf(ApiException.class);
    }

    @Test
    void fromListRejectsOutOfRangeValues() {
        List<Double> huge = new ArrayList<>(Fixtures.list(Fixtures.vector(0f)));
        huge.set(1, 9999.0);
        assertThatThrownBy(() -> EmbeddingCodec.fromList(huge)).isInstanceOf(ApiException.class);
    }

    @Test
    void distanceIsZeroForIdenticalVectorsAndPositiveOtherwise() {
        assertThat(EmbeddingCodec.distance(Fixtures.vector(0f), Fixtures.vector(0f))).isZero();
        assertThat(EmbeddingCodec.distance(Fixtures.vector(0f), Fixtures.vector(1f))).isGreaterThan(10.0);
    }

    @Test
    void distanceOfDifferentLengthsIsInfinite() {
        assertThat(EmbeddingCodec.distance(new float[2], new float[3])).isEqualTo(Double.MAX_VALUE);
    }
}
