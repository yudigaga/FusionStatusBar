package com.xtjm.fusionstatusbar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35})
public class LayoutPresetStoreTest {
    @Test public void saveCopyRenameAndDeleteKeepBothLayoutModes() throws Exception {
        MemoryFile file = new MemoryFile();
        LayoutPresetStore store = new LayoutPresetStore(file);
        ControlCenterConfig config = ControlCenterConfig.defaults().withEnabled(true).withColumns(5)
                .withLayoutPlan(ControlCenterLayoutPlan.blank(5).forPublication(true).encode());

        LayoutPresetStore.Preset saved = store.save("工作布局", config, true);
        assertEquals("工作布局", saved.name);
        assertTrue(saved.compact);
        assertConfigEquals(config, saved.config);

        LayoutPresetStore.Preset copied = store.copy(saved.id, "副本");
        store.rename(copied.id, "出差布局");
        List<LayoutPresetStore.Preset> values = new LayoutPresetStore(file).list();
        assertEquals(2, values.size());
        assertEquals("出差布局", values.get(1).name);
        assertTrue(values.get(1).compact);
        assertConfigEquals(config, values.get(1).config);

        store.delete(saved.id);
        List<LayoutPresetStore.Preset> reopened = new LayoutPresetStore(file).list();
        assertEquals(1, reopened.size());
        assertEquals("出差布局", reopened.get(0).name);
    }

    @Test public void invalidNamesDuplicatesMissingIdsAndLimitAreRejectedWithoutLosingData() throws Exception {
        MemoryFile file = new MemoryFile();
        LayoutPresetStore store = new LayoutPresetStore(file);
        ControlCenterConfig config = ControlCenterConfig.defaults();
        assertIOException("invalid_preset_name", () -> store.save("  ", config, false));
        LayoutPresetStore.Preset first = store.save("日常", config, false);
        LayoutPresetStore.Preset second = store.save("游戏", config, true);
        assertIOException("duplicate_preset_name", () -> store.save("日常", config, true));
        assertIOException("duplicate_preset_name", () -> store.rename(second.id, "日常 "));
        assertIOException("preset_not_found", () -> store.delete("missing"));
        for (int i = 2; i < LayoutPresetStore.MAX_PRESETS; i++) store.save("布局" + i, config, (i & 1) == 0);
        assertIOException("preset_limit", () -> store.save("超出上限", config, false));
        assertEquals(LayoutPresetStore.MAX_PRESETS, store.list().size());
    }

    @Test public void corruptOrUnsupportedPresetDocumentsAreNotSilentlyReplaced() throws Exception {
        MemoryFile file = new MemoryFile();
        LayoutPresetStore store = new LayoutPresetStore(file);
        file.bytes = "{\"version\":2,\"presets\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] before = file.bytes.clone();
        try {
            store.save("新布局", ControlCenterConfig.defaults(), false);
            fail("expected corrupt preset rejection");
        } catch (IOException expected) { }
        assertArrayEquals(before, file.bytes);
    }

    private static void assertIOException(String message, IoAction action) throws Exception {
        try { action.run(); fail("expected " + message); }
        catch (IOException expected) { assertEquals(message, expected.getMessage()); }
    }

    private static void assertConfigEquals(ControlCenterConfig expected, ControlCenterConfig actual) {
        android.os.Bundle expectedValues = new android.os.Bundle(), actualValues = new android.os.Bundle();
        expected.writeTo(expectedValues);
        actual.writeTo(actualValues);
        assertEquals(expectedValues.keySet(), actualValues.keySet());
        for (String key : expectedValues.keySet()) assertEquals(key, expectedValues.get(key), actualValues.get(key));
    }

    private interface IoAction { void run() throws IOException; }

    static final class MemoryFile implements FusionConfigStore.SnapshotFile {
        byte[] bytes;
        @Override public byte[] read() throws IOException {
            if (bytes == null) throw new FileNotFoundException();
            return bytes.clone();
        }
        @Override public void write(byte[] value) { bytes = value.clone(); }
    }
}
