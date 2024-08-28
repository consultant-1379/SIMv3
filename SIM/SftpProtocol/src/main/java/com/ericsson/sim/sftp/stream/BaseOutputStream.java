package com.ericsson.sim.sftp.stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public abstract class BaseOutputStream extends OutputStream implements Closeable {
    private static final Logger logger = LogManager.getLogger(BaseOutputStream.class);

    private int count = 0;

    @Override
    public void write(int b) throws IOException {
        this.write(new byte[b], 0, 1);
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    protected void _write(byte[] b, int off, int len, int indexAt, boolean addToCount) throws IOException {
        logger.debug("Writing to stream from buffer from offset {} and length {}. Delimiter index found at {}", off, len, indexAt);
        getInner().write(b, off, len);
        if (addToCount) {
            this.count += len;
        } else {
            logger.debug("{} byte are not added to count", len);
        }
    }

    protected abstract OutputStream getInner();

    protected int indexOf(byte[] array, int off, int len, byte[] target) {
        if (target.length == 0) {
            return 0;
        }

        outer:
        for (int i = off; i < (off + len) - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }

        return -1;
    }

    protected int lastIndexOf(byte[] array, int off, int len, byte[] target) {
        if (target.length == 0) {
            return 0;
        }

        int lastIndex = -1;

        outer:
        for (int i = off; i < (off + len) - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            lastIndex = i;
        }

        return lastIndex;
    }

    /**
     * Returns number of bytes written to inner stream so far.
     * Closing stream will not reset this value
     *
     * @return Actual number of bytes written
     */
    public int bytesWritten() {
        return count;
    }
}
