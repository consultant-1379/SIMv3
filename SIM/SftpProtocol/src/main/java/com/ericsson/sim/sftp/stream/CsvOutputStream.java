package com.ericsson.sim.sftp.stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class CsvOutputStream extends SingleLineOutputStream {
    private static final Logger logger = LogManager.getLogger(CsvOutputStream.class);

    private static class Header {
        private String header = null;
        private boolean addToCount = true;
        private boolean headerRead = false;
    }

    private final Header header = new Header();


    public CsvOutputStream(OutputStream out, String lineDelimiter, String header) {
        super(out, lineDelimiter);
        if (header != null && !header.isEmpty()) {
            this.header.header = header;
            this.header.headerRead = true;
            this.header.addToCount = false;
        }
    }

    protected void processHeader(byte[] b, int off, int len) {
        //check if we have saved headers, and save it if not already done
        if (len == 0) {
            logger.warn("Stream passed length of 0. Nothing to process");
        } else if (!header.headerRead) {
            logger.debug("Header is not read yet. Going to copy header from offset {}, and length {}", off, len);
            header.header = new String(Arrays.copyOfRange(b, off, off + len));
            header.headerRead = true;
        }
    }

    @Override
    protected void _write(byte[] b, int off, int len, int indexAt, boolean addToCount) throws IOException {
        //headers can only be processed with full information.
        //if we don't have a delimiter, write it as it is
        if (indexAt > -1) {
            processHeader(b, off, len);
        }

        super._write(b, off, len, indexAt, addToCount);
    }

    @Override
    public String getHeader() {
        return header.header;
    }

    public boolean addHeaderBytesToCount() {
        return header.addToCount;
    }
//
//    @Override
//    public int bytesWritten() {
//        if (header.removeFromCount > 0) {
//            logger.debug("Removing {} from byte count as the header is taken from history", header.removeFromCount);
//        }
//
//        return super.bytesWritten() - header.removeFromCount;
//    }
}
