package com.pdfconverter.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngravingEnumeratedTokensTest {

    @Test
    void splitHandlesTrailingAndPrefix() {
        List<String> t = EngravingEnumeratedTokens.splitEnumeratedTokens("CK, CK, CA, TT, and PC");
        assertEquals(List.of("CK", "CK", "CA", "TT", "PC"), t);
    }

    @Test
    void onePieceReturnsWholeString() {
        assertEquals("CK, CK, CA, TT, PC",
                EngravingEnumeratedTokens.engravingForPiece("CK, CK, CA, TT, PC", 0, 1));
    }

    @Test
    void fivePiecesMapsRows() {
        String raw = "CK, CK, CA, TT, PC";
        for (int i = 0; i < 5; i++) {
            assertEquals(EngravingEnumeratedTokens.splitEnumeratedTokens(raw).get(i),
                    EngravingEnumeratedTokens.engravingForPiece(raw, i, 5));
        }
    }

    @Test
    void singleTokenRepeatedAcrossRows() {
        assertEquals("DAD",
                EngravingEnumeratedTokens.engravingForPiece("DAD", 0, 3));
        assertEquals("DAD",
                EngravingEnumeratedTokens.engravingForPiece("DAD", 2, 3));
    }

    @Test
    void countMismatchKeepsFull() {
        String raw = "A, B, C";
        assertEquals(raw, EngravingEnumeratedTokens.engravingForPiece(raw, 0, 5));
        assertEquals(raw, EngravingEnumeratedTokens.engravingForPiece(raw, 4, 5));
    }
}
