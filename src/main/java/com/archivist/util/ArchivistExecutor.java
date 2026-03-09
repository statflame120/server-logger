package com.archivist.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared background executor for Archivist. All file I/O, JSON serialization,
 * and non-render work should be submitted here to keep the render thread free.
 */
public final class ArchivistExecutor {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Archivist-IO");
        t.setDaemon(true);
        return t;
    });

    private ArchivistExecutor() {}

    /** Submit work to run off the render thread. */
    public static void run(Runnable task) {
        IO.submit(task);
    }

    /** Shutdown the executor. */
    public static void shutdown() {
        IO.shutdown();
    }
}
