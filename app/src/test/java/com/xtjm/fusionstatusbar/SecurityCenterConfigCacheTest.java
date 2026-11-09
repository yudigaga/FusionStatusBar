package com.xtjm.fusionstatusbar;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = AndroidAtomicFileShadow.class)
public class SecurityCenterConfigCacheTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void bothReadsAndWritesHoldTheSharedProcessLockFile() throws Exception {
        File file = new File(folder.newFolder(), "fusion_statusbar_security_config_v1.json");
        File lockFile = new File(file.getPath() + ".lck");
        FusionConfigStore.SnapshotFile checked = new FusionConfigStore.SnapshotFile() {
            @Override public byte[] read() throws IOException { assertLocked(lockFile); return new byte[] { 1 }; }
            @Override public void write(byte[] data) throws IOException { assertLocked(lockFile); }
        };
        SecurityCenterConfigCache cache = new SecurityCenterConfigCache(file, checked);
        assertArrayEquals(new byte[] { 1 }, cache.read());
        cache.write(new byte[] { 2 });
    }

    @Test public void separateCacheInstancesCannotReadAnUncommittedWriter() throws Exception {
        File file = new File(folder.newFolder(), "fusion_statusbar_security_config_v1.json");
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CountDownLatch readerReady = new CountDownLatch(1);
        CountDownLatch reading = new CountDownLatch(1);
        byte[][] committed = { new byte[] { 1 } };
        FusionConfigStore.SnapshotFile document = new FusionConfigStore.SnapshotFile() {
            @Override public byte[] read() { reading.countDown(); return committed[0]; }
            @Override public void write(byte[] data) throws IOException {
                writing.countDown();
                try {
                    if (!commit.await(5, TimeUnit.SECONDS)) throw new IOException("test_writer_timeout");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt(); throw new IOException(error);
                }
                committed[0] = data;
            }
        };
        SecurityCenterConfigCache writer = new SecurityCenterConfigCache(file, document);
        SecurityCenterConfigCache reader = new SecurityCenterConfigCache(file, document);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> write = threads.submit(() -> { writer.write(new byte[] { 2 }); return null; });
            assertTrue(writing.await(5, TimeUnit.SECONDS));
            Future<byte[]> read = threads.submit(() -> { readerReady.countDown(); return reader.read(); });
            assertTrue(readerReady.await(5, TimeUnit.SECONDS));
            assertFalse("read entered while AtomicFile write was uncommitted", reading.await(250, TimeUnit.MILLISECONDS));
            commit.countDown();
            write.get(5, TimeUnit.SECONDS);
            assertArrayEquals(new byte[] { 2 }, read.get(5, TimeUnit.SECONDS));
        } finally {
            commit.countDown();
            threads.shutdownNow();
        }
    }

    @Test public void durableSnapshotsKeepTheExistingFilenameAndSizeBound() throws Exception {
        File file = new File(folder.newFolder(), "fusion_statusbar_security_config_v1.json");
        SecurityCenterConfigCache first = new SecurityCenterConfigCache(file);
        byte[] data = "{\"misc_schema_version\":1}".getBytes(StandardCharsets.UTF_8);
        first.write(data);
        assertArrayEquals(data, new SecurityCenterConfigCache(file).read());
        assertTrue(file.isFile());
        assertThrows(IOException.class, () -> first.write(new byte[4097]));
        assertArrayEquals(data, first.read());
    }

    private static void assertLocked(File file) throws IOException {
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            try (FileLock unexpected = channel.tryLock()) {
                fail("cache delegate ran without an exclusive OS lock");
            } catch (OverlappingFileLockException expected) {
                // A different process is excluded by this same lock for the full delegate operation.
            }
        }
    }
}
