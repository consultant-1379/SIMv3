package com.ericsson.sim.common.history.model;

public class ActiveFile {
    private String header = null;
    private int successiveEmptyResponses = 0;
    private int totalBytesRead = 0;

    public int getTotalBytesRead() {
        return totalBytesRead;
    }

    public int getSuccessiveEmptyResponses() {
        return successiveEmptyResponses;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public void addByteReads(int bytesRead) {
        this.totalBytesRead += bytesRead;
    }

    public void incrementSuccessiveEmptyResponses() {
        this.successiveEmptyResponses += 1;
    }

    @Override
    public String toString() {
        return "ActiveFiles{" +
                ", header=" + header +
                ", lastReadBytes=" + totalBytesRead +
                ", emptyResponses=" + successiveEmptyResponses +
                '}';
    }
}
