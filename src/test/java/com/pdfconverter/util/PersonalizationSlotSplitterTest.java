package com.pdfconverter.util;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonalizationSlotSplitterTest {

    @Test
    void splitsFrontBackSameLine() {
        List<String> p = PersonalizationSlotSplitter.trySplitFrontBack("Front: DDS 2026 Back: Class of 2026");
        assertEquals(List.of("DDS 2026", "Class of 2026"), p);
    }

    @Test
    void splitsFrontBackSameLineCaseInsensitive() {
        List<String> p = PersonalizationSlotSplitter.trySplitFrontBack("Front: DDS 2026 back: Class of 2026");
        assertEquals(List.of("DDS 2026", "Class of 2026"), p);
    }

    @Test
    void splitsWhenFrontLabelLowercase() {
        List<String> p = PersonalizationSlotSplitter.trySplitFrontBack("front: TS\nBACK: Forever");
        assertEquals(List.of("TS", "Forever"), p);
    }

    @Test
    void splitsRoundDiscAndBar() {
        List<String> p = PersonalizationSlotSplitter.trySplitFrontBack(
                "Round Disc: Photo\nBar: Forever your little girl, Font 28");
        assertEquals(List.of("Photo", "Forever your little girl, Font 28"), p);
    }

    @Test
    void splitsFrontBarMultiline() {
        List<String> p = PersonalizationSlotSplitter.trySplitFrontBack(
                "Front: photo in message\nBar: Forever your little girl, Font 28");
        assertEquals(2, p.size());
        assertEquals("photo in message", p.get(0));
        assertEquals("Forever your little girl, Font 28", p.get(1));
    }

    @Test
    void splitsClassicNewlines() {
        List<String> p = PersonalizationSlotSplitter.trySplitFrontBack("Front: TS\nBack: Forever & Always");
        assertEquals(List.of("TS", "Forever & Always"), p);
    }

    @Test
    void lowercaseBackAfterNewlineStillSplits() {
        List<String> p = PersonalizationSlotSplitter.trySplitFrontBack("Front: LineA\nback: LineB");
        assertEquals(List.of("LineA", "LineB"), p);
    }

    @Test
    void doesNotSplitWithoutFrontLabelEvenIfBackAppears() {
        List<String> p = PersonalizationSlotSplitter.trySplitFrontBack("Please refer back: see notes");
        assertEquals(Collections.emptyList(), p);
    }

    @Test
    void doesNotSplitHoldBackMidPhraseWithoutStructuredFrontBack() {
        List<String> p = PersonalizationSlotSplitter.trySplitFrontBack(
                "Front: please hold back on extra lines\nExtra note here");
        assertEquals(Collections.emptyList(), p);
    }
}
