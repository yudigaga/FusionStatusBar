package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.AtomicFile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Publishes immutable generation files only after a complete image has been received. */
final class ControlCenterPreviewStore {
    static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;
    static final int MAX_LAYOUT_CHARS = 128 * 1024;
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 24_000_000L;
    private static final Object LOCK = new Object();
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final File directory;

    ControlCenterPreviewStore(Context context) {
        this(new File(context.createDeviceProtectedStorageContext().getFilesDir(), "previews"));
    }

    ControlCenterPreviewStore(File directory) { this.directory = directory; }

    Bundle request(boolean clear) throws IOException {
        synchronized (LOCK) {
            State previous = load();
            if (previous.request == Long.MAX_VALUE) throw new IOException("generation_overflow");
            long generation = previous.request + 1L;
            State next = new State(generation, clear ? generation : 0L, "", "", false);
            save(next);
            cleanup(generation);
            return bundle(next);
        }
    }

    Bundle state() {
        synchronized (LOCK) { return bundle(load()); }
    }

    ParcelFileDescriptor open(Uri uri, String mode) throws FileNotFoundException {
        synchronized (LOCK) {
            State state = load();
            String parameter = uri.getQueryParameter("generation");
            long generation;
            try { generation = parameter == null ? state.ready : Long.parseLong(parameter); }
            catch (NumberFormatException error) { throw new FileNotFoundException("invalid_generation"); }
            if ("w".equals(mode) || "wt".equals(mode)) {
                if (parameter == null || generation <= 0L || generation != state.request
                        || state.ready == generation) {
                    throw new FileNotFoundException("stale_preview_write");
                }
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new FileNotFoundException("preview_directory_unavailable");
                }
                return ParcelFileDescriptor.open(staging(generation),
                        ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
                                | ParcelFileDescriptor.MODE_WRITE_ONLY);
            }
            if (!"r".equals(mode) || generation <= 0L || generation != state.ready
                    || !state.image) throw new FileNotFoundException("preview_not_ready");
            return ParcelFileDescriptor.open(published(generation),
                    ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }

    Bundle ready(long generation, String layout) throws IOException {
        return ready(generation, layout, "");
    }

    Bundle ready(long generation, String layout, String catalog) throws IOException {
        synchronized (LOCK) {
            State current = load();
            if (generation <= 0L || generation != current.request
                    || current.ready == generation) return bundle(current);
            if (layout == null) layout = "";
            if (layout.length() > MAX_LAYOUT_CHARS) throw new IOException("preview_layout_too_large");
            if (catalog == null) catalog = "";
            if (catalog.length() > MAX_LAYOUT_CHARS) throw new IOException("preview_catalog_too_large");
            byte[] bytes = readBounded(staging(generation), MAX_IMAGE_BYTES);
            validatePng(bytes);
            // Copy before publication: an outstanding writer can no longer mutate the reader's file.
            writeAtomic(new AtomicFile(published(generation)), bytes);
            State next = new State(generation, generation, layout, catalog, true);
            save(next);
            staging(generation).delete();
            return bundle(next);
        }
    }

    Bundle refreshCatalog(long generation, String catalog) throws IOException {
        synchronized (LOCK) {
            State current = load();
            if (generation <= 0L || generation != current.request
                    || current.ready != generation || !current.image) return bundle(current);
            if (catalog == null) catalog = "";
            if (catalog.length() > MAX_LAYOUT_CHARS) throw new IOException("preview_catalog_too_large");
            if (catalog.equals(current.catalog)) return bundle(current);
            State next = new State(current.request, current.ready, current.layout, catalog, true);
            save(next);
            return bundle(next);
        }
    }

    File currentFile() {
        synchronized (LOCK) {
            State state = load();
            return published(state.image ? state.ready : 0L);
        }
    }

    private State load() {
        try {
            byte[] bytes;
            try (FileInputStream input = metadata().openRead()) {
                bytes = input.readNBytes(MAX_LAYOUT_CHARS * 4 + 1024);
            }
            JSONObject values = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            return new State(Math.max(0L, values.optLong("request")),
                    Math.max(0L, values.optLong("ready")), values.optString("layout", ""),
                    values.optString("catalog", ""), values.optBoolean("image", false));
        } catch (IOException | JSONException | RuntimeException error) {
            return new State(0L, 0L, "", "", false);
        }
    }

    private void save(State state) throws IOException {
        try {
            JSONObject values = new JSONObject();
            values.put("request", state.request);
            values.put("ready", state.ready);
            values.put("layout", state.layout);
            values.put("catalog", state.catalog);
            values.put("image", state.image);
            writeAtomic(metadata(), values.toString().getBytes(StandardCharsets.UTF_8));
        } catch (JSONException error) {
            throw new IOException(error);
        }
    }

    private Bundle bundle(State state) {
        Bundle result = new Bundle();
        result.putLong(FusionConfig.KEY_PREVIEW_REQUEST, state.request);
        result.putLong(FusionConfig.KEY_PREVIEW_READY, state.ready);
        result.putString(FusionConfig.KEY_PREVIEW_LAYOUT, state.layout);
        result.putString(FusionConfig.KEY_PREVIEW_CATALOG, state.catalog);
        result.putString(FusionConfig.KEY_PREVIEW_URI, state.image
                ? FusionConfig.controlCenterPreviewUri(state.ready).toString() : "");
        return result;
    }

    private File staging(long generation) { return new File(directory, generation + ".pending"); }
    private File published(long generation) { return new File(directory, generation + ".png"); }
    private AtomicFile metadata() { return new AtomicFile(new File(directory, "state.json")); }

    private void cleanup(long retainedGeneration) {
        File[] files = directory.listFiles();
        if (files == null) return;
        String retained = Long.toString(retainedGeneration);
        for (File file : files) {
            String name = file.getName();
            if ((name.endsWith(".png") || name.endsWith(".pending")
                    || name.endsWith(".png.new") || name.endsWith(".png.bak"))
                    && !name.startsWith(retained + ".")) file.delete();
        }
    }

    private static byte[] readBounded(File file, int limit) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("preview_image_too_large");
            return bytes;
        }
    }

    private static void writeAtomic(AtomicFile file, byte[] data) throws IOException {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(data);
            output.flush();
            output.getFD().sync();
            file.finishWrite(output);
            try (FileInputStream check = file.openRead()) {
                if (!Arrays.equals(data, check.readNBytes(data.length + 1))) {
                    throw new IOException("preview_publish_failed");
                }
            }
        } catch (IOException | RuntimeException error) {
            file.failWrite(output);
            throw error;
        }
    }

    static void validatePng(byte[] bytes) throws IOException {
        if (bytes.length < 45 || !Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(bytes, 8))) {
            throw new IOException("invalid_png_signature");
        }
        try (DataInputStream input = new DataInputStream(
                new java.io.ByteArrayInputStream(bytes, 8, bytes.length - 8))) {
            boolean header = false;
            boolean pixels = false;
            while (input.available() > 0) {
                int length = input.readInt();
                byte[] type = new byte[4];
                input.readFully(type);
                if (length < 0 || length > input.available() - 4) {
                    throw new IOException("incomplete_png_chunk");
                }
                byte[] content = new byte[length];
                input.readFully(content);
                long checksum = Integer.toUnsignedLong(input.readInt());
                CRC32 crc = new CRC32();
                crc.update(type);
                crc.update(content);
                if (crc.getValue() != checksum) throw new IOException("invalid_png_checksum");
                String name = new String(type, StandardCharsets.US_ASCII);
                if (!header) {
                    if (!"IHDR".equals(name) || length != 13) throw new IOException("missing_png_header");
                    java.nio.ByteBuffer dimensions = java.nio.ByteBuffer.wrap(content);
                    int width = dimensions.getInt();
                    int height = dimensions.getInt();
                    if (width <= 0 || height <= 0 || width > MAX_DIMENSION
                            || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS) {
                        throw new IOException("preview_dimensions_too_large");
                    }
                    header = true;
                }
                if ("IDAT".equals(name)) pixels = true;
                if ("IEND".equals(name)) {
                    if (length != 0 || !pixels || input.available() != 0) {
                        throw new IOException("invalid_png_end");
                    }
                    return;
                }
            }
            throw new IOException("incomplete_png");
        }
    }

    private static final class State {
        final long request;
        final long ready;
        final String layout;
        final String catalog;
        final boolean image;
        State(long request, long ready, String layout, String catalog, boolean image) {
            this.request = request;
            this.ready = ready;
            this.layout = layout;
            this.catalog = catalog;
            this.image = image;
        }
    }
}
