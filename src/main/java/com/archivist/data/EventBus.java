package com.archivist.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central event bus for Archivist. Events posted here flow to:
 * - Connection Log window (live timeline)
 * - Console window (real-time event feed)
 *
 * Thread-safe: events can be posted from mixin hooks on the network thread.
 */
public class EventBus {

    private static final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private static final List<Consumer<LogEvent>> listeners = new CopyOnWriteArrayList<>();

    /** Post a new event. Listeners are notified immediately. */
    public static void post(LogEvent event) {
        events.add(event);
        for (var listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {}
        }
    }

    /** Convenience: post with type and message. */
    public static void post(LogEvent.Type type, String message) {
        post(new LogEvent(type, message));
    }

    /** Get a snapshot of all events. */
    public static List<LogEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /** Get the number of events. */
    public static int size() {
        return events.size();
    }

    /** Add a listener for new events. */
    public static void addListener(Consumer<LogEvent> listener) {
        listeners.add(listener);
    }

    /** Remove a listener. */
    public static void removeListener(Consumer<LogEvent> listener) {
        listeners.remove(listener);
    }

    /** Clear all events but keep listeners. */
    public static void clearEvents() {
        events.clear();
    }

    /** Full reset: clear events and listeners. */
    public static void reset() {
        events.clear();
    }
}
