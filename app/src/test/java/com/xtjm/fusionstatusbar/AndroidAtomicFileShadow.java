package com.xtjm.fusionstatusbar;

import android.util.AtomicFile;
import android.util.Log;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Android rename replaces a destination atomically; File.renameTo on Windows does not. */
@Implements(value = AtomicFile.class, minSdk = 33)
public final class AndroidAtomicFileShadow {
    @Implementation
    protected static void rename(File source, File destination) {
        try {
            if (destination.isDirectory() && !destination.delete()) {
                throw new IOException("Cannot replace non-empty directory");
            }
            Files.move(source.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            Log.e("AtomicFile", "Failed to rename " + source + " to " + destination, error);
        }
    }
}
