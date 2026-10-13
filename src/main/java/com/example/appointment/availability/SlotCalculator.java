package com.example.appointment.availability;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class SlotCalculator {
    public record Slot(LocalDateTime start, LocalDateTime end) {}

    /**
     * Generates fixed slots relative to the working-day start.
     * A slot is omitted if any part overlaps the break.
     * Minutes are measured from midnight; 1440 represents 24:00.
     */
    public List<Slot> generate(LocalDate date, int workStartMinute,
                               int workEndMinute, List<MinuteRange> breaks,
                               int slotDurationMinutes) {
        if (slotDurationMinutes <= 0) {
            throw new IllegalArgumentException("slotDurationMinutes must be positive");
        }
        List<Slot> result = new ArrayList<>();
        for (int start = workStartMinute;
             start + slotDurationMinutes <= workEndMinute;
             start += slotDurationMinutes) {
            int end = start + slotDurationMinutes;
            int finalStart = start;
            boolean overlaps = breaks.stream()
                    .anyMatch(b -> finalStart < b.endMinute() && end > b.startMinute());
            if (!overlaps) {
                LocalDateTime slotStart = LocalDateTime.of(date, LocalTime.MIDNIGHT)
                        .plusMinutes(finalStart);
                result.add(new Slot(slotStart, slotStart.plusMinutes(slotDurationMinutes)));
            }
        }
        return result;
    }

    public record MinuteRange(int startMinute, int endMinute) {
        public MinuteRange {
            if (startMinute < 0 || endMinute > 1440 || startMinute >= endMinute) {
                throw new IllegalArgumentException("Invalid minute range");
            }
        }
    }
}
