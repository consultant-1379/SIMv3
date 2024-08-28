package com.ericsson.sim.common.history;

import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public abstract class RuntimeFile implements Closeable {

    protected abstract void initializeFile() throws IOException;

    public abstract void read(boolean checkLoaded) throws IOException;

    public abstract void save() throws IOException;

    public abstract boolean exists();

    protected abstract Logger getLogger();

    public abstract File getFile();

    protected boolean tryCopy(File file, File copy) {
        try {
            Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (IOException e) {
            getLogger().error("Failed to copy {} to {}", file.getName(), copy.getName());
            getLogger().error(e);
            return false;
        }
    }

    protected boolean tryMove(File oldFile, File newFile) {
        File moved = oldFile;
        try {
            if (!oldFile.setWritable(true, true)) {
                //this may affect the move operation
                getLogger().debug("Failed to set {} as writable", oldFile.getName());
            }

            try {
                try {
                    Files.move(oldFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    getLogger().info("Atomic move is not supported, trying without ATOMIC_MOVE option");
                    Files.move(oldFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                moved = newFile;
                return true;
            } catch (Exception e) {
                getLogger().error("Failed to rename {} to {}", oldFile.getName(), newFile.getName());
                getLogger().error(e);

                return false;
            }
        } finally {
            if (moved.exists()) {
                if (!moved.setReadOnly()) {
                    getLogger().debug("Failed to set {} as readonly", moved.getName());
                }
            }
        }
    }

    protected boolean tryRemove(File file) {
        try {
            Files.deleteIfExists(file.toPath());
            return true;
        } catch (Exception e1) {
            getLogger().warn("Failed to remove " + file.getName(), e1);
            return false;
        }
    }
}
