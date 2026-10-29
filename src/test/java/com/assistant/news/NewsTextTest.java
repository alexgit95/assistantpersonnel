package com.assistant.news;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewsTextTest {

    @Test
    void stripsTagsAndKeepsParagraphBreaks() {
        assertThat(NewsText.plain("<p>Un <b>titre</b></p><p>Deux<br/>trois</p>", 200))
                .isEqualTo("Un titre\nDeux\ntrois");
    }

    @Test
    void dropsScriptAndStyleBlocksEntirely() {
        assertThat(NewsText.plain("Avant<script>alert(1)</script><style>p{}</style>Après", 200))
                .isEqualTo("Avant Après");
    }

    @Test
    void decodesEntitiesWithoutReinterpretingThemAsMarkup() {
        assertThat(NewsText.plain("Tom &amp; Jerry &#233;t&#xE9; &laquo;ok&raquo;", 200))
                .isEqualTo("Tom & Jerry été «ok»");
        assertThat(NewsText.plain("&lt;script&gt;alert(1)&lt;/script&gt;", 200))
                .isEqualTo("<script>alert(1)</script>");
        assertThat(NewsText.plain("&unknown; &#99999999;", 200)).isEqualTo("&unknown; &#99999999;");
    }

    @Test
    void collapsesWhitespaceAndTruncatesWithAnEllipsis() {
        assertThat(NewsText.plain("  a   b \n\n\n c  ", 200)).isEqualTo("a b\nc");
        assertThat(NewsText.plain("abcdefghij", 5)).isEqualTo("abcde…");
    }

    @Test
    void returnsAnEmptyStringForMissingContent() {
        assertThat(NewsText.plain(null, 10)).isEmpty();
        assertThat(NewsText.plain("   ", 10)).isEmpty();
    }
}
