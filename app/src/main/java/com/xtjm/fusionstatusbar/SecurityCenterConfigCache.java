package com.xtjm.fusionstatusbar;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

/** Dedicated phone-manager cache; the filename remains compatible with earlier snapshots. */
final class SecurityCenterConfigCache implements FusionConfigStore.SnapshotFile {
    // FileChannel rejects overlapping locks within one VM rather than waiting for them.
    private static final Object PROCESS_LOCK = new Object();
    private final File lockFile;
    private final FusionConfigStore.SnapshotFile document;

    SecurityCenterConfigCache(File file) {
        this(file, new BoundedAtomicDocument(file, 4096));
    }

    SecurityCenterConfigCache(File file, FusionConfigStore.SnapshotFile document) {
        lockFile = new File(file.getAbsolutePath() + ".lck");
        this.document = document;
    }

    @Override public byte[] read() throws IOException {
        synchronized (PROCESS_LOCK) {
            try (FileChannel channel = openLock(); FileLock ignored = channel.lock()) {
                // AtomicFile.openRead can recover/delete staging files, so reads need the same lock.
                return document.read();
            }
        }
    }

    @Override public void write(byte[] data) throws IOException {
        synchronized (PROCESS_LOCK) {
            try (FileChannel channel = openLock(); FileLock ignored = channel.lock()) {
                // Includes AtomicFile's publish and verification read, not just its byte write.
                document.write(data);
            }
        }
    }

    private FileChannel openLock() throws IOException {
        File directory = lockFile.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory())
            throw new IOException("security_config_cache_directory_unavailable");
        return FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }
}
