package com.archivist.database;

import com.archivist.ArchivistMod;
import com.archivist.gui.ServerLogData;
import com.google.gson.*;

import java.lang.reflect.Method;

/**
 * Remote MongoDB storage via the mongo-driver-sync.
 * Uses reflection to avoid hard compile-time dependency --
 * the mongodb-driver-sync jar must be shaded into the mod.
 *
 * If the driver is not present, connect() will throw a clear error message.
 */
public class MongoAdapter implements DatabaseAdapter {

    private Object mongoClient;      // com.mongodb.client.MongoClient
    private Object database;         // com.mongodb.client.MongoDatabase
    private Object collection;       // com.mongodb.client.MongoCollection
    private boolean connected = false;

    @Override
    public void connect(String connectionString, String authToken) throws Exception {
        try {
            Class.forName("com.mongodb.client.MongoClients");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MongoDB driver not found. " +
                    "The mongodb-driver-sync library must be shaded into the mod jar.", e);
        }

        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalArgumentException("MongoDB connection string cannot be empty");
        }

        // Use reflection to create client
        Class<?> mongoClientsClass = Class.forName("com.mongodb.client.MongoClients");
        Method createMethod = mongoClientsClass.getMethod("create", String.class);
        mongoClient = createMethod.invoke(null, connectionString);

        // Get database (default "archivist")
        Method getDatabaseMethod = mongoClient.getClass().getMethod("getDatabase", String.class);
        database = getDatabaseMethod.invoke(mongoClient, "archivist");

        // Get collection
        Method getCollectionMethod = database.getClass().getMethod("getCollection", String.class);
        collection = getCollectionMethod.invoke(database, "server_logs");

        connected = true;
        ArchivistMod.LOGGER.info("[Archivist] MongoDB connected");
    }

    @Override
    public void disconnect() {
        if (mongoClient != null) {
            try {
                Method closeMethod = mongoClient.getClass().getMethod("close");
                closeMethod.invoke(mongoClient);
            } catch (Exception ignored) {}
        }
        mongoClient = null;
        database = null;
        collection = null;
        connected = false;
    }

    @Override
    public void upload(ServerLogData entry) throws Exception {
        if (!connected || collection == null) {
            throw new IllegalStateException("Not connected to MongoDB");
        }

        String json = serializeToJson(entry);

        // Use reflection: Document.parse(json), collection.insertOne(doc)
        Class<?> documentClass = Class.forName("org.bson.Document");
        Method parseMethod = documentClass.getMethod("parse", String.class);
        Object document = parseMethod.invoke(null, json);

        Method insertOneMethod = collection.getClass().getMethod("insertOne", Object.class);
        insertOneMethod.invoke(collection, document);

        ArchivistMod.LOGGER.info("[Archivist] MongoDB upload: {}", entry.getDisplayName());
    }

    @Override
    public boolean testConnection() {
        if (!connected || database == null) return false;
        try {
            // Try listing collection names as a ping
            Method listMethod = database.getClass().getMethod("listCollectionNames");
            Object iterable = listMethod.invoke(database);
            Method firstMethod = iterable.getClass().getMethod("first");
            firstMethod.invoke(iterable); // may return null, that's ok
            return true;
        } catch (Exception e) {
            ArchivistMod.LOGGER.warn("[Archivist] MongoDB test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String displayName() {
        return "MongoDB";
    }

    private String serializeToJson(ServerLogData entry) {
        JsonObject root = new JsonObject();
        root.addProperty("serverName", entry.getDisplayName());
        root.addProperty("serverIp", entry.ip);
        root.addProperty("port", entry.port);
        root.addProperty("brand", entry.brand);
        root.addProperty("version", entry.version);
        root.addProperty("timestamp", entry.timestamp);

        JsonArray plugins = new JsonArray();
        entry.plugins.forEach(plugins::add);
        root.add("plugins", plugins);

        JsonArray worlds = new JsonArray();
        for (ServerLogData.WorldSession ws : entry.worlds) {
            JsonObject w = new JsonObject();
            w.addProperty("dimension", ws.dimension);
            w.addProperty("timestamp", ws.timestamp);
            worlds.add(w);
        }
        root.add("worlds", worlds);

        return new GsonBuilder().create().toJson(root);
    }
}
