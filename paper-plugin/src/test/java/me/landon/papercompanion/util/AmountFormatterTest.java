package me.landon.papercompanion.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AmountFormatterTest {
    @Test
    void compactsCommonValues() {
        assertEquals("950", AmountFormatter.compact(950L));
        assertEquals("1.50k", AmountFormatter.compact(1_500L));
        assertEquals("12.3k", AmountFormatter.compact(12_300L));
        assertEquals("5.50m", AmountFormatter.compact(5_500_000L));
        assertEquals("7.20b", AmountFormatter.compact(7_200_000_000L));
    }
}
