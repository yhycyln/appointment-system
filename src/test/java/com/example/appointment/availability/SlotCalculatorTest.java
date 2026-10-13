package com.example.appointment.availability;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlotCalculatorTest {
    private final SlotCalculator calculator = new SlotCalculator();

    @Test
    void skipsSlotsThatOverlapBreakAndKeepsFixedCadence() {
        var slots = calculator.generate(
                LocalDate.of(2026, 10, 12),
                600, 1440,
                List.of(new SlotCalculator.MinuteRange(790, 820)), // 13:10-13:40
                40);

        assertTrue(slots.stream().noneMatch(s ->
                s.start().getHour() == 12 && s.start().getMinute() == 40));
        assertTrue(slots.stream().noneMatch(s ->
                s.start().getHour() == 13 && s.start().getMinute() == 20));
        assertTrue(slots.stream().anyMatch(s ->
                s.start().getHour() == 14 && s.start().getMinute() == 0));
    }

    @Test
    void doesNotCreateSlotThatEndsAfterClosingTime() {
        var slots = calculator.generate(
                LocalDate.of(2026, 10, 12), 600, 1440, List.of(), 40);

        assertFalse(slots.stream().anyMatch(s -> s.end().getHour() == 0
                && s.end().getMinute() > 0));
        assertEquals(23, slots.get(slots.size() - 1).start().getHour());
        assertEquals(20, slots.get(slots.size() - 1).start().getMinute());
    }
}
