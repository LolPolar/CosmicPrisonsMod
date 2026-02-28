package me.landon.papercompanion.timer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EventTimerService {
    private final Map<String, EventTimer> timers = new LinkedHashMap<>();

    public synchronized void upsertTimer(String key, String displayName, long endEpochMillis) {
        timers.put(key, new EventTimer(key, displayName, endEpochMillis));
    }

    public synchronized void removeTimer(String key) {
        timers.remove(key);
    }

    public synchronized List<EventTimer> snapshot() {
        return List.copyOf(timers.values());
    }

    public synchronized List<String> buildLines(long nowEpochMillis, int maxLines) {
        return timers.values().stream()
                .sorted(Comparator.comparingLong(EventTimer::endEpochMillis))
                .limit(Math.max(0, maxLines))
                .map(timer -> timer.displayName() + ": " + formatRemaining(nowEpochMillis, timer.endEpochMillis()))
                .toList();
    }

    public static String formatRemaining(long nowEpochMillis, long endEpochMillis) {
        long remainingMillis = Math.max(0L, endEpochMillis - nowEpochMillis);
        Duration duration = Duration.ofMillis(remainingMillis);
        long totalSeconds = duration.getSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public synchronized List<String> purgeExpired(long nowEpochMillis) {
        List<String> removed = new ArrayList<>();
        for (EventTimer timer : List.copyOf(timers.values())) {
            if (timer.endEpochMillis() <= nowEpochMillis) {
                removed.add(timer.key());
            }
        }
        removed.forEach(timers::remove);
        return removed;
    }
}
