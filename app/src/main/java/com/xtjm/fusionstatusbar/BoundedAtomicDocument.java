package com.xtjm.fusionstatusbar;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Atomic, durable small documents with a read bound independent of the reported file size. */
final class BoundedAtomicDocument implements FusionConfigStore.SnapshotFile {
    private final AtomicFile file;
    private final int maxBytes;

    BoundedAtomicDocument(File file, int maxBytes) { this.file = new AtomicFile(file); this.maxBytes = maxBytes; }

    @Override public byte[] read() throws IOException {
        try (FileInputStream input = file.openRead()) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new IOException("document_too_large");
            return bytes;
        }
    }

    @Override public void write(byte[] data) throws IOException {
        if (data.length > maxBytes) throw new IOException("document_too_large");
        FileOutputStream output = null;
        boolean committed = false;
        try {
            output = file.startWrite();
            output.write(data);
            output.flush();
            output.getFD().sync();
            file.finishWrite(output);
            committed = true;
        } catch (IOException | RuntimeException error) {
            if (!committed) file.failWrite(output);
            throw error;
        }
        // Once finishWrite succeeds AtomicFile has removed its rollback file. Calling
        // failWrite after a verification read error could then delete the committed file.
        if (!Arrays.equals(data, read())) throw new IOException("document_publish_failed");
    }
}
