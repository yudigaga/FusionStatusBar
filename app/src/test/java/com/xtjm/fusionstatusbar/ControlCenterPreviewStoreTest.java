package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterPreviewStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void incompleteWriteCannotPublishFileOrMetadata() throws Exception {
        File directory = temporary.newFolder();
        ControlCenterPreviewStore store = new ControlCenterPreviewStore(directory);
        long generation = request(store);
        byte[] png = png();
        write(directory, generation, Arrays.copyOf(png, png.length - 6));
        assertThrows(IOException.class, () -> store.ready(generation, "layout"));
        assertEquals(0L, store.state().getLong(FusionConfig.KEY_PREVIEW_READY));
        assertFalse(new File(directory, generation + ".png").exists());
        assertThrows(FileNotFoundException.class,
                () -> store.open(FusionConfig.controlCenterPreviewUri(generation), "r"));
        write(directory, generation, png);
        assertEquals(generation, store.ready(generation, "layout").getLong(FusionConfig.KEY_PREVIEW_READY));
        assertEquals("layout", new ControlCenterPreviewStore(directory).state().getString(FusionConfig.KEY_PREVIEW_LAYOUT));
    }

    @Test public void lateWriteAfterNewRequestOrClearCanNeverBecomeVisible() throws Exception {
        File directory = temporary.newFolder();
        ControlCenterPreviewStore store = new ControlCenterPreviewStore(directory);
        long old = request(store);
        write(directory, old, png());
        long current = request(store);
        write(directory, old, png());
        assertEquals(0L, store.ready(old, "old").getLong(FusionConfig.KEY_PREVIEW_READY));
        write(directory, current, png());
        assertEquals(current, store.ready(current, "new").getLong(FusionConfig.KEY_PREVIEW_READY));
        assertEquals("new", store.state().getString(FusionConfig.KEY_PREVIEW_LAYOUT));
        Bundle clear = store.request(true);
        assertEquals("", clear.getString(FusionConfig.KEY_PREVIEW_URI));
        write(directory, current, png());
        store.ready(current, "late");
        assertEquals("", store.state().getString(FusionConfig.KEY_PREVIEW_LAYOUT));
        assertEquals(clear.getLong(FusionConfig.KEY_PREVIEW_REQUEST),
                store.state().getLong(FusionConfig.KEY_PREVIEW_READY));
    }

    @Test public void publishedBytesAreImmutableEvenWhenStagingWriterContinues() throws Exception {
        File directory = temporary.newFolder();
        ControlCenterPreviewStore store = new ControlCenterPreviewStore(directory);
        long generation = request(store);
        byte[] png = png();
        write(directory, generation, png);
        store.ready(generation, "capture");
        write(directory, generation, new byte[] {1, 2, 3});
        assertArrayEquals(png, java.nio.file.Files.readAllBytes(new File(directory, generation + ".png").toPath()));
        assertThrows(FileNotFoundException.class,
                () -> store.open(FusionConfig.controlCenterPreviewUri(generation), "w"));
    }

    @Test public void catalogCanRefreshAfterSameGenerationIsPublished() throws Exception {
        File directory = temporary.newFolder();
        ControlCenterPreviewStore store = new ControlCenterPreviewStore(directory);
        long generation = request(store);
        byte[] png = png();
        write(directory, generation, png);
        store.ready(generation, "layout", "wifi,cell");
        assertEquals("wifi,cell", store.state().getString(FusionConfig.KEY_PREVIEW_CATALOG));
        assertEquals(generation, store.refreshCatalog(generation, "wifi,cell,custom(pkg/.Tile)")
                .getLong(FusionConfig.KEY_PREVIEW_READY));
        assertEquals("wifi,cell,custom(pkg/.Tile)",
                store.state().getString(FusionConfig.KEY_PREVIEW_CATALOG));
        assertArrayEquals(png, java.nio.file.Files.readAllBytes(
                new File(directory, generation + ".png").toPath()));
    }

    @Test public void concurrentCallersReceiveUniqueSerializedGenerations() throws Exception {
        File directory = temporary.newFolder();
        ControlCenterPreviewStore first = new ControlCenterPreviewStore(directory);
        ControlCenterPreviewStore second = new ControlCenterPreviewStore(directory);
        java.util.Set<Long> requests = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        Thread a = new Thread(() -> requestMany(first, requests, error));
        Thread b = new Thread(() -> requestMany(second, requests, error));
        a.start(); b.start(); a.join(); b.join();
        assertNull(error.get());
        assertEquals(20, requests.size());
        assertEquals(20L, first.state().getLong(FusionConfig.KEY_PREVIEW_REQUEST));
    }

    @Test public void excessiveLayoutAndChecksumDamageAreRejectedWithoutPublication() throws Exception {
        File directory = temporary.newFolder();
        ControlCenterPreviewStore store = new ControlCenterPreviewStore(directory);
        long generation = request(store);
        write(directory, generation, png());
        assertThrows(IOException.class,
                () -> store.ready(generation, "x".repeat(ControlCenterPreviewStore.MAX_LAYOUT_CHARS + 1)));
        byte[] corrupted = png();
        corrupted[29] ^= 1;
        write(directory, generation, corrupted);
        assertThrows(IOException.class, () -> store.ready(generation, ""));
        assertEquals(0L, store.state().getLong(FusionConfig.KEY_PREVIEW_READY));
    }

    @Test public void requestsRemoveObsoleteFilesAndRejectUnversionedWrites() throws Exception {
        File directory = temporary.newFolder();
        ControlCenterPreviewStore store = new ControlCenterPreviewStore(directory);
        for (int i = 0; i < 8; i++) {
            long generation = request(store);
            write(directory, generation, png());
            store.ready(generation, "");
        }
        assertEquals(2, directory.listFiles().length);
        request(store);
        assertThrows(FileNotFoundException.class, () -> store.open(FusionConfig.controlCenterPreviewUri(), "w"));
    }

    private static void requestMany(ControlCenterPreviewStore store, java.util.Set<Long> requests,
            java.util.concurrent.atomic.AtomicReference<Throwable> error) {
        try { for (int i = 0; i < 10; i++) requests.add(request(store)); }
        catch (Throwable failure) { error.set(failure); }
    }

    private static long request(ControlCenterPreviewStore store) throws IOException {
        return store.request(false).getLong(FusionConfig.KEY_PREVIEW_REQUEST);
    }

    private static void write(File directory, long generation, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(new File(directory, generation + ".pending"))) {
            output.write(bytes);
        }
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream fields = new DataOutputStream(header);
        fields.writeInt(1); fields.writeInt(1);
        fields.write(new byte[] {8, 6, 0, 0, 0});
        chunk(output, "IHDR", header.toByteArray());
        ByteArrayOutputStream pixels = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(pixels)) {
            deflater.write(new byte[] {0, (byte) 255, 0, 0, (byte) 255});
        }
        chunk(output, "IDAT", pixels.toByteArray());
        chunk(output, "IEND", new byte[0]);
        return bytes.toByteArray();
    }

    private static void chunk(DataOutputStream output, String name, byte[] content) throws IOException {
        byte[] type = name.getBytes(StandardCharsets.US_ASCII);
        output.writeInt(content.length); output.write(type); output.write(content);
        CRC32 crc = new CRC32(); crc.update(type); crc.update(content);
        output.writeInt((int) crc.getValue());
    }
}
