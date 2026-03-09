package com.archivist.data;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * A single event entry for the connection log and console live feed.
 * Events are color-coded by type in the GUI.
 */
public class LogEvent {

    public enum Type {
        CONNECT,
        DISCONNECT,
        BRAND,
        PLUGIN,
        WORLD,
        GAMEMODE,
        PACKET,
        SYSTEM,
        ERROR,
        DB_SYNC
    }

    private final Type type;
    private final String message;
    private final String timestamp;

    public LogEvent(Type type, String message) {
        this.type = type;
        this.message = message;
        this.timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public Type getType() { return type; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }

    /** Formatted string for display: "[HH:mm:ss] message" */
    public String formatted() {
        return "[" + timestamp + "] " + message;
    }
}
