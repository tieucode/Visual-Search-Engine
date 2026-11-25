package com.visualsearch.indexing.image;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipFrameExtractorTest {
    @Test
    void keepsRepresentativesForBriefScenesButDropsRepeatedFrames() {
        var frames = ClipFrameExtractor.select(List.of(
                sample(0, 0x102030), sample(25, 0x112131),
                sample(50, 0xf0e0d0), sample(75, 0x102030)));
        assertEquals(List.of(0, 50), frames.stream().map(ClipFrameExtractor.Frame::timestampMs).toList());
    }

    @Test
    void capsRepresentativesAndKeepsTheFirstFrame() {
        var colors = List.of(0, 0x80, 0xff, 0x8000, 0xff00, 0x800000, 0xff0000,
                0xffffff, 0xff00ff, 0xffff00, 0x00ffff);
        var frames = ClipFrameExtractor.select(IntStream.range(0, colors.size())
                .mapToObj(index -> sample(index, colors.get(index))).toList());
        assertEquals(8, frames.size());
        assertEquals(0, frames.getFirst().timestampMs());
    }

    private ClipFrameExtractor.Sample sample(int timestamp, int color) {
        return new ClipFrameExtractor.Sample(new ClipFrameExtractor.Frame(timestamp, new byte[0]),
                new int[] {color});
    }
}
