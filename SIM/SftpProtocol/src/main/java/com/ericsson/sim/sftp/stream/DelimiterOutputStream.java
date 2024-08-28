package com.ericsson.sim.sftp.stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;

public class DelimiterOutputStream extends BaseOutputStream {

    private static final Logger logger = LogManager.getLogger(DelimiterOutputStream.class);

    private static ByteBuffer initBuffer() {
        //32KB buffer
        return ByteBuffer.allocate(32768);
    }

    private OutputStream out;
    protected final byte[] finishDelimiter;

    //64kb buffer. if we don't get delimiter until this, we will have to write it
    //to not eat up memory
    protected ByteBuffer byteBuffer = initBuffer();

    public DelimiterOutputStream(OutputStream out, String finishDelimiter) {
        this.out = out;
        this.finishDelimiter = finishDelimiter.getBytes();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        logger.debug("Request to write buffer of {} bytes from offset {} and length {}", b.length, off, len);

        //write only when delimiter is found.
        //this will not work if delimiter itself is split in two writes.

        //if there is no delimiter, we just write everything as it is
        if (finishDelimiter.length == 0) {
            logger.debug("Delimiter is of size 0. Writing buffer to stream as it is");
            this._write(b, off, len, -1, true);
            return;
        }
        //if the buffer is too small, even smaller than finish delimiter
        else if (finishDelimiter.length >= byteBuffer.capacity()) {
            logger.debug("Internal buffer capacity is too small to even hold delimiter. Buffer size is {} while delimiter is {} bytes long. Writing buffer to stream as it is",
                    byteBuffer.capacity(), finishDelimiter.length);
            this._write(b, off, len, -1, true);
            return;
        }

        //before we write anything to buffer, check if it will overflow
        //buffer. If so, flush buffer except for last finishDelimiter.length
        //we will write remaining part to buffer again and check for index from there

        //we have something coming in that is larger than the buffer capacity. Flush enough to make room
        if (byteBuffer.capacity() < len) {
            //flush buffer first
            logger.info("Flushing existing buffer containing {} byte as incoming data of {} bytes is too large for buffer's capacity of {}",
                    byteBuffer.position(), len, byteBuffer.capacity());
            _flush();

            logger.debug("Writing excess {} bytes to fit current buffer capacity", (len - byteBuffer.capacity()));
            //write enough to make capacity
            this._write(b, off, (len - byteBuffer.capacity()), -1, true);

            //update offset and len
            off += (len - byteBuffer.capacity());
            len = byteBuffer.capacity();
        }
        //now for cases where capacity is enough but remaining is not
        else if (byteBuffer.remaining() < len) {
            //write excess bytes from buffer
            final int excess = len - byteBuffer.remaining();

            logger.info("Flushing {} bytes from existing buffer, which has {} bytes, as incoming data of {} cannot fit in remaining capacity of {}",
                    excess, byteBuffer.position(), len, byteBuffer.remaining());
            _write(byteBuffer.array(), 0, excess, -1, true);

            //adjust buffer
            ByteBuffer newBuffer = initBuffer();
            logger.debug("Copying remaining {} bytes from old buffer to new", byteBuffer.position() - excess);
            newBuffer.put(byteBuffer.array(), excess, byteBuffer.position() - excess);
            byteBuffer = newBuffer;
        }

        logger.debug("Copy {} bytes from offset {} from incoming array to buffer", len, off);
        //write everything to byte buffer
        byteBuffer.put(b, off, len);

        //otherwise, we have to check where we get delimiter in the array
        //byteBuffer is filled in the check as well
        final int position = streamBuffer();
        if (position < 0) {
            return;
        }

        //adjust buffer
        ByteBuffer newBuffer = initBuffer();
        newBuffer.put(byteBuffer.array(), position, byteBuffer.position() - position);
        byteBuffer = newBuffer;
    }

    /**
     * Stream the content of {@code byteBuffer} to underlying stream according to {@code finishDelimiter}
     *
     * @return Position until the content is streamed, or -1 if not {@code finishDelimiter} was found
     * @throws IOException If an error occurred while streaming
     */
    protected int streamBuffer() throws IOException {
        final int index = computeIndex(0, byteBuffer.position());
        //we could not find index, move to next part
        if (index < 0) {
            logger.debug("Delimiter not found, returning. Buffer has {} bytes", byteBuffer.position());
            return -1;
        }

        logger.debug("Delimiter found at {} of buffer having {} bytes", index, byteBuffer.position());

        final int pointer = index + finishDelimiter.length;
        this._write(byteBuffer.array(), 0, pointer, index, true);
        return pointer;
    }

    protected int computeIndex(int off, int len) {
        return lastIndexOf(byteBuffer.array(), off, len, finishDelimiter);
    }
//
//    private void _write(byte[] b, int off, int len, int indexAt) throws IOException {
//        this._write(out, b, off, len, indexAt);
//    }

    //not to be called by external/outer streams
    private void _flush() throws IOException {
        logger.debug("Flushing buffer");
        this._write(byteBuffer.array(), 0, byteBuffer.position(), -1, true);
        byteBuffer.clear();
    }

    @Override
    protected OutputStream getInner() {
        return out;
    }

    @Override
    public void close() throws IOException {
        logger.debug("Closing stream");
        //Closing the inner stream is not our job. The one who provided it
        //should handle the lifecycle as well
//            try {
//                this.out.close();
//            } catch (Exception ignored) {
//            }
        super.close();

        //important cast
        ((Buffer) byteBuffer).clear();
    }


    /**
     * Returns number of bytes still in in-memory buffer
     *
     * @return Actual number of bytes still in buffer
     */
    public int remaining() {
        return byteBuffer.position();
    }

    /**
     * Return string representation of remaining bytes in buffer
     *
     * @return String representing remaining bytes in buffer
     */
    public String dumpRemaining() {
        return new String(byteBuffer.array(), 0, byteBuffer.position());
    }

    public String getHeader() {
        return "";
    }
}
