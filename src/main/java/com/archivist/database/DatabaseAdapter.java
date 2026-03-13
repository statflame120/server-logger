package com.archivist.database;

import com.archivist.gui.ServerLogData;

import java.util.List;

/**
 * Interface for uploading Archivist log data to an external database.
 * Third-party developers can implement this to add custom storage backends.
 */
public interface DatabaseAdapter {

    /** Called once when the adapter is activated. */
    void connect(String connectionString, String authToken) throws Exception;

    /** Clean shutdown. */
    void disconnect();

    /** Upload a single log entry. */
    void upload(ServerLogData entry) throws Exception;

    /** Bulk upload. Default implementation loops upload(). */
    default void uploadBatch(List<ServerLogData> entries) throws Exception {
        for (ServerLogData e : entries) upload(e);
    }

    /** Returns true if the connection is healthy. */
    boolean testConnection();

    /** Human-readable adapter name for the dropdown. */
    String displayName();
}
