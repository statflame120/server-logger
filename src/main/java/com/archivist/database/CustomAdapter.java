package com.archivist.database;

import com.archivist.ArchivistMod;
import com.archivist.gui.ServerLogData;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * Loads a user-supplied class implementing DatabaseAdapter via reflection.
 * Config specifies a classpath (path to jar) and fully-qualified class name.
 * This lets power users integrate any storage backend without forking Archivist.
 *
 * Classloader isolation: the user jar is loaded in its own URLClassLoader
 * with the mod's classloader as parent, so it can see the DatabaseAdapter
 * interface but cannot interfere with mod internals.
 */
public class CustomAdapter implements DatabaseAdapter {

    private DatabaseAdapter delegate;
    private URLClassLoader userClassLoader;

    private final String classpath;
    private final String className;

    public CustomAdapter(String classpath, String className) {
        this.classpath = classpath;
        this.className = className;
    }

    @Override
    public void connect(String connectionString, String authToken) throws Exception {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Custom adapter class name cannot be empty");
        }

        try {
            ClassLoader parentCl = CustomAdapter.class.getClassLoader();

            if (classpath != null && !classpath.isBlank()) {
                File jarFile = new File(classpath);
                if (!jarFile.exists()) {
                    throw new RuntimeException("Custom adapter jar not found: " + classpath);
                }
                URL jarUrl = jarFile.toURI().toURL();
                userClassLoader = new URLClassLoader(new URL[]{jarUrl}, parentCl);
            }

            ClassLoader cl = userClassLoader != null ? userClassLoader : parentCl;
            Class<?> adapterClass = Class.forName(className, true, cl);

            if (!DatabaseAdapter.class.isAssignableFrom(adapterClass)) {
                throw new RuntimeException(className + " does not implement DatabaseAdapter");
            }

            delegate = (DatabaseAdapter) adapterClass.getDeclaredConstructor().newInstance();
            delegate.connect(connectionString, authToken);

            ArchivistMod.LOGGER.info("[Archivist] Custom adapter loaded: {}", className);

        } catch (Exception e) {
            // Clean up on failure
            if (userClassLoader != null) {
                try { userClassLoader.close(); } catch (Exception ignored) {}
                userClassLoader = null;
            }
            delegate = null;
            throw e;
        }
    }

    @Override
    public void disconnect() {
        if (delegate != null) {
            try {
                delegate.disconnect();
            } catch (Exception e) {
                ArchivistMod.LOGGER.warn("[Archivist] Custom adapter disconnect error: {}", e.getMessage());
            }
            delegate = null;
        }
        if (userClassLoader != null) {
            try {
                userClassLoader.close();
            } catch (Exception ignored) {}
            userClassLoader = null;
        }
    }

    @Override
    public void upload(ServerLogData entry) throws Exception {
        if (delegate == null) throw new IllegalStateException("Custom adapter not loaded");
        delegate.upload(entry);
    }

    @Override
    public void uploadBatch(List<ServerLogData> entries) throws Exception {
        if (delegate == null) throw new IllegalStateException("Custom adapter not loaded");
        delegate.uploadBatch(entries);
    }

    @Override
    public boolean testConnection() {
        return delegate != null && delegate.testConnection();
    }

    @Override
    public String displayName() {
        if (delegate != null) {
            try {
                return delegate.displayName();
            } catch (Exception ignored) {}
        }
        return "Custom (" + className + ")";
    }
}
