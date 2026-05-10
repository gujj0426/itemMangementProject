package com.pdfconverter.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngravingFaceCountUtilTest {

    @Test
    void roundDiscBarInPersonalization_only_countsTwo() {
        assertEquals(2, EngravingFaceCountUtil.countFaces(
                "Round Disc: Photo\nBar: Forever your little girl, Font 28"));
    }
}
