package com.archivist.gui.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe image cache for downloading and caching textures from URLs.
 * Uses counter-based texture IDs and LRU eviction.
 * Hides version-specific ResourceLocation/Identifier types internally.
 */
public class ImageCache {

    private static final int MAX_CACHE_SIZE = 5;
    private static final AtomicInteger idCounter = new AtomicInteger(0);

    private static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final Set<String> loading = ConcurrentHashMap.newKeySet();
    private static final Set<String> failed = ConcurrentHashMap.newKeySet();

    private static final LinkedList<String> accessOrder = new LinkedList<>();
    private static final Object orderLock = new Object();

    private record CacheEntry(DynamicTexture texture, Object texId,
                              int imageWidth, int imageHeight) {}

    /**
     * Render a cached background image to fill the given area.
     * Downloads async if not yet cached. Returns true if rendered.
     */
    public static boolean renderBackground(GuiGraphics g, String url, int width, int height, float opacity) {
        if (url == null || url.isEmpty()) return false;
        if (failed.contains(url)) return false;

        CacheEntry entry = cache.get(url);
        if (entry != null) {
            synchronized (orderLock) {
                accessOrder.remove(url);
                accessOrder.addLast(url);
            }
            // Render via blit - the texId is the version-appropriate identifier type
            //? if >=1.21.11 {
            g.blit((net.minecraft.resources.Identifier) entry.texId, 0, 0, 0, 0, width, height, width, height);
            //?} else if >=1.21.6 {
            /*g.blit((net.minecraft.resources.ResourceLocation) entry.texId, 0, 0, 0, 0, width, height, width, height);
            *///?} else if >=1.21.5 {
            /*g.blit(net.minecraft.client.renderer.RenderType::guiTextured, (net.minecraft.resources.ResourceLocation) entry.texId, 0, 0, 0f, 0f, width, height, width, height);
            *///?} else {
            /*g.blit((net.minecraft.resources.ResourceLocation) entry.texId, 0, 0, 0, 0, width, height, width, height);
            *///?}

            // Apply opacity as darkening overlay
            int alpha = (int) ((1.0f - opacity) * 255);
            if (alpha > 0) {
                g.fill(0, 0, width, height, (alpha << 24));
            }
            return true;
        }

        // Start async download if not already loading
        if (!loading.contains(url)) {
            loading.add(url);
            CompletableFuture.runAsync(() -> {
                try {
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(15))
                            .build();
                    HttpResponse<byte[]> response = client.send(request,
                            HttpResponse.BodyHandlers.ofByteArray());

                    if (response.statusCode() == 200) {
                        byte[] data = response.body();
                        Minecraft.getInstance().execute(() -> {
                            try {
                                NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
                                int texNum = idCounter.getAndIncrement();

                                // DynamicTexture constructor changed in 1.21.5+
                                //? if >=1.21.5 {
                                DynamicTexture texture = new DynamicTexture(() -> "archivist_bg_" + texNum, image);
                                //?} else {
                                /*DynamicTexture texture = new DynamicTexture(image);
                                *///?}

                                // ResourceLocation renamed to Identifier in 1.21.11
                                //? if >=1.21.11 {
                                net.minecraft.resources.Identifier texId =
                                        net.minecraft.resources.Identifier.fromNamespaceAndPath("archivist", "bg_" + texNum);
                                //?} else {
                                /*net.minecraft.resources.ResourceLocation texId =
                                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("archivist", "bg_" + texNum);
                                *///?}

                                Minecraft.getInstance().getTextureManager()
                                        .register(texId, texture);

                                evictIfNeeded();

                                cache.put(url, new CacheEntry(texture, texId,
                                        image.getWidth(), image.getHeight()));
                                synchronized (orderLock) {
                                    accessOrder.addLast(url);
                                }
                            } catch (Exception e) {
                                failed.add(url);
                            }
                            loading.remove(url);
                        });
                    } else {
                        failed.add(url);
                        loading.remove(url);
                    }
                } catch (Exception e) {
                    failed.add(url);
                    loading.remove(url);
                }
            });
        }
        return false;
    }

    /** Check if a URL is currently loading. */
    public static boolean isLoading(String url) {
        return loading.contains(url);
    }

    /** Check if a URL has been cached. */
    public static boolean isCached(String url) {
        return cache.containsKey(url);
    }

    private static void evictIfNeeded() {
        while (cache.size() >= MAX_CACHE_SIZE) {
            String oldest;
            synchronized (orderLock) {
                if (accessOrder.isEmpty()) break;
                oldest = accessOrder.removeFirst();
            }
            CacheEntry removed = cache.remove(oldest);
            if (removed != null) {
                removed.texture.close();
            }
        }
    }

    public static void clearCache() {
        cache.values().forEach(entry -> entry.texture.close());
        cache.clear();
        failed.clear();
        loading.clear();
        synchronized (orderLock) {
            accessOrder.clear();
        }
    }

    public static void clearFailed(String url) {
        failed.remove(url);
    }
}
