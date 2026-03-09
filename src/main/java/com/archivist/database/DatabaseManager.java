package com.archivist.database;

import com.archivist.ArchivistMod;
import com.archivist.config.ArchivistConfig;
import com.archivist.gui.ServerLogData;

import java.util.List;
import java.util.concurrent.*;

/**
 * Manages the active database adapter. All IO runs on a dedicated
 * single-thread executor so it never blocks the game loop.
 *
 * Exposes: upload(), uploadAll(), testConnection(), getStatus().
 * Failed uploads go into a retry queue.
 */
public class DatabaseManager {

    public enum Status {
        NOT_CONFIGURED,
        CONNECTING,
        CONNECTED,
        UPLOADING,
        ERROR,
        DISCONNECTED
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Archivist-DB-IO");
        t.setDaemon(true);
        return t;
    });

    private DatabaseAdapter activeAdapter;
    private volatile Status status = Status.NOT_CONFIGURED;
    private volatile String statusMessage = "Not configured";
    private final ConcurrentLinkedQueue<ServerLogData> retryQueue = new ConcurrentLinkedQueue<>();

    // ── Public API ──────────────────────────────────────────────────────────

    public Status getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public DatabaseAdapter getActiveAdapter() {
        return activeAdapter;
    }

    /**
     * Connect using the given adapter type and credentials.
     * Non-blocking: runs on the IO thread.
     */
    public void connect(String adapterType, String connectionString, String authToken) {
        status = Status.CONNECTING;
        statusMessage = "Connecting...";

        executor.submit(() -> {
            try {
                // Disconnect existing
                if (activeAdapter != null) {
                    try { activeAdapter.disconnect(); } catch (Exception ignored) {}
                    activeAdapter = null;
                }

                DatabaseAdapter adapter = createAdapter(adapterType);
                if (adapter == null) {
                    status = Status.NOT_CONFIGURED;
                    statusMessage = "No adapter selected";
                    return;
                }

                adapter.connect(connectionString, authToken);
                activeAdapter = adapter;
                status = Status.CONNECTED;
                statusMessage = "Connected (" + adapter.displayName() + ")";
                ArchivistMod.LOGGER.info("[Archivist] Database connected: {}", adapter.displayName());

                // Process retry queue
                processRetryQueue();

            } catch (Exception e) {
                status = Status.ERROR;
                statusMessage = "Error: " + e.getMessage();
                ArchivistMod.LOGGER.warn("[Archivist] Database connect failed: {}", e.getMessage());
            }
        });
    }

    /** Disconnect the active adapter. Non-blocking. */
    public void disconnect() {
        executor.submit(() -> {
            if (activeAdapter != null) {
                try {
                    activeAdapter.disconnect();
                } catch (Exception e) {
                    ArchivistMod.LOGGER.warn("[Archivist] Database disconnect error: {}", e.getMessage());
                }
                activeAdapter = null;
            }
            status = Status.DISCONNECTED;
            statusMessage = "Disconnected";
        });
    }

    /** Upload a single log entry. Non-blocking. */
    public void upload(ServerLogData entry) {
        executor.submit(() -> {
            if (activeAdapter == null) {
                retryQueue.add(entry);
                return;
            }
            Status prevStatus = status;
            try {
                status = Status.UPLOADING;
                statusMessage = "Uploading " + entry.getDisplayName() + "...";
                activeAdapter.upload(entry);
                status = Status.CONNECTED;
                statusMessage = "Uploaded: " + entry.getDisplayName();
            } catch (Exception e) {
                status = Status.ERROR;
                statusMessage = "Upload failed: " + e.getMessage();
                retryQueue.add(entry);
                ArchivistMod.LOGGER.warn("[Archivist] Upload failed for {}: {}",
                        entry.getDisplayName(), e.getMessage());
            }
        });
    }

    /** Upload all given entries. Non-blocking. */
    public void uploadAll(List<ServerLogData> entries) {
        executor.submit(() -> {
            if (activeAdapter == null) {
                status = Status.ERROR;
                statusMessage = "Not connected";
                return;
            }
            int success = 0, fail = 0;
            status = Status.UPLOADING;
            for (ServerLogData entry : entries) {
                try {
                    statusMessage = "Uploading " + (success + fail + 1) + "/" + entries.size() + "...";
                    activeAdapter.upload(entry);
                    success++;
                } catch (Exception e) {
                    fail++;
                    retryQueue.add(entry);
                    ArchivistMod.LOGGER.warn("[Archivist] Bulk upload fail: {}", e.getMessage());
                }
            }
            status = fail > 0 ? Status.ERROR : Status.CONNECTED;
            statusMessage = "Uploaded " + success + "/" + entries.size()
                    + (fail > 0 ? " (" + fail + " failed)" : "");
        });
    }

    /** Test the connection. Non-blocking, updates status. */
    public void testConnection() {
        executor.submit(() -> {
            if (activeAdapter == null) {
                status = Status.NOT_CONFIGURED;
                statusMessage = "No adapter configured";
                return;
            }
            try {
                boolean ok = activeAdapter.testConnection();
                if (ok) {
                    status = Status.CONNECTED;
                    statusMessage = "Connection OK";
                } else {
                    status = Status.ERROR;
                    statusMessage = "Connection test failed";
                }
            } catch (Exception e) {
                status = Status.ERROR;
                statusMessage = "Test error: " + e.getMessage();
            }
        });
    }

    /** Shutdown the executor. Call on mod unload. */
    public void shutdown() {
        disconnect();
        executor.shutdown();
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private void processRetryQueue() {
        while (!retryQueue.isEmpty() && activeAdapter != null) {
            ServerLogData entry = retryQueue.poll();
            if (entry == null) break;
            try {
                activeAdapter.upload(entry);
            } catch (Exception e) {
                // Put it back and stop retrying
                retryQueue.add(entry);
                break;
            }
        }
    }

    private DatabaseAdapter createAdapter(String type) {
        if (type == null) return null;
        return switch (type) {
            case "REST API"    -> new RestAdapter();
            case "Discord Bot" -> new DiscordBotAdapter();
            case "Custom"      -> {
                ArchivistConfig cfg = ArchivistMod.INSTANCE != null
                        ? ArchivistMod.INSTANCE.extendedConfig : null;
                if (cfg != null) {
                    yield new CustomAdapter(cfg.customAdapterClasspath, cfg.customAdapterClassName);
                }
                yield null;
            }
            default -> null; // "None"
        };
    }
}
