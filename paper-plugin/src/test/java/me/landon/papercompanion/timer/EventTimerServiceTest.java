package me.landon.papercompanion.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EventTimerServiceTest {
    @Test
    void formatsRemainingTime() {
        assertEquals("02:05", EventTimerService.formatRemaining(1_000L, 126_000L));
        assertEquals("00:00", EventTimerService.formatRemaining(10_000L, 9_000L));
    }

    @Test
    void buildsSortedLinesAndPurgesExpired() {
        EventTimerService service = new EventTimerService();
        long now = 1_000_000L;
        service.upsertTimer("c", "C", now + 30_000L);
        service.upsertTimer("a", "A", now + 10_000L);
        service.upsertTimer("b", "B", now + 20_000L);

        List<String> lines = service.buildLines(now, 2);
        assertEquals(List.of("A: 00:10", "B: 00:20"), lines);

        List<String> removed = service.purgeExpired(now + 15_000L);
        assertEquals(List.of("a"), removed);
        assertTrue(service.snapshot().stream().noneMatch(timer -> timer.key().equals("a")));
    }
}
