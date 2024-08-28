package com.ericsson.sim.sftp.stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;

public class SingleLineOutputStream extends DelimiterOutputStream {

    private static final Logger logger = LogManager.getLogger(SingleLineOutputStream.class);


    //        public HeaderAwareOutputStream(OutputStream out, String headerDelimiter, String lineDelimiter, int headerLines) {
    public SingleLineOutputStream(OutputStream out, String lineDelimiter) {
        super(out, lineDelimiter);
    }

    /**
     * Stream the content of {@code byteBuffer} to underlying stream according to {@code finishDelimiter}
     *
     * @return Position until the content is streamed, or -1 if not {@code finishDelimiter} was found
     * @throws IOException If an error occurred while streaming
     */
    @Override
    protected int streamBuffer() throws IOException {

        int index = computeIndex(0, byteBuffer.position());
        if (index < 0) {
            logger.debug("Delimiter not found, returning. Buffer has {} bytes", byteBuffer.position());
            return -1;
        }

        int pointer = -1;
        int prevIndex = 0;
        while (index > -1) {
            logger.debug("Delimiter found at {} of buffer having {} bytes", index, byteBuffer.position());
            pointer = index + finishDelimiter.length;
            this._write(byteBuffer.array(), prevIndex, (pointer - prevIndex), index, true);

            prevIndex = pointer;
            index = computeIndex(pointer, byteBuffer.position() - pointer);
        }
        return pointer;
    }

    @Override
    protected int computeIndex(int off, int len) {
//            if (!header.headerRead) {
//                header.index = indexOf(byteBuffer.array(), 0, byteBuffer.position(), finishDelimiter);
//                logger.debug("Header was not read yet, delimiter found at {} for header start index", header.index);
//            }
//            return super.computeIndex();

        return indexOf(byteBuffer.array(), off, len, finishDelimiter);
    }
}
