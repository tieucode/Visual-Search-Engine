package com.visualsearch.service;

import com.fasterxml.jackson.databind.node.TextNode;
import com.visualsearch.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClipUploadProbeTest {
    @Test
    void enforcesTheExactDurationLimitBeforeRounding() {
        assertEquals(30_000, ClipUploadProbe.durationMs(text("30.000"), text("29.9"), false));
        assertEquals(1, ClipUploadProbe.durationMs(text("0.0001"), text("N/A"), false));
        assertThrows(BadRequestException.class, () -> ClipUploadProbe.durationMs(text("29"), text("30.0001"), false));
        assertThrows(BadRequestException.class, () -> ClipUploadProbe.durationMs(text("N/A"), text("0"), false));
    }

    @Test
    void acceptsASingleGifFrameWithoutAnExplicitDelay() {
        assertEquals(100, ClipUploadProbe.durationMs(text("N/A"), text("0"), true));
    }

    private TextNode text(String value) {
        return TextNode.valueOf(value);
    }
}
