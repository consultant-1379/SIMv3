package com.ericsson.sim.common.history.model;

import com.ericsson.sim.common.util.HelpUtil;

import java.io.File;
import java.util.Objects;
import java.util.regex.Matcher;

public class FileAttributes {
    //    private String parentDir;
//    private String filename;
    private String localPath;
    private int remoteMTime;
//    private long remoteSize;

    private transient final Matcher remoteFileMatcher;
    private transient final String remotePath;
    private transient final String remoteParentDir;
    private transient final String remoteFilename;

    //    public FileAttributes(String parentDir, String filename, int remoteMTime, long remoteSize) {
    public FileAttributes(String remotePath, String localPath, int remoteMTime, Matcher remoteFileMatcher) {
//        this.parentDir = parentDir;
//        this.filename = filename;
        this.localPath = localPath;
        this.remoteMTime = remoteMTime;
//        this.remoteSize = remoteSize;

        File file = new File(remotePath);
        this.remotePath = remotePath;
        this.remoteFilename = file.getName();
        this.remoteParentDir = HelpUtil.replaceLast(remotePath, remoteFilename, "");
        this.remoteFileMatcher = remoteFileMatcher;
    }
//
//    public String getParentDir() {
//        return parentDir;
//    }
//
//    public String getFilename() {
//        return filename;
//    }

    /**
     * Get absolute path of remote file
     *
     * @return Absolute path of remote file
     */
    public String getRemotePath() {
        return remotePath;
    }

    public String getRemoteParentDir() {
        return remoteParentDir;
    }

    public String getRemoteFilename() {
        return remoteFilename;
    }

    public String getLocalPath() {
        return localPath;
    }

    /**
     * Local files' absolute path name
     *
     * @param localPath
     */
    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public int getRemoteMTime() {
        return remoteMTime;
    }

    public Matcher getRemoteFileMatcher() {
        return remoteFileMatcher;
    }

    //    public long getRemoteSize() {
//        return remoteSize;
//    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileAttributes that = (FileAttributes) o;
        return remotePath.equals(that.remotePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localPath);
    }

    @Override
    public String toString() {
        return "{" +
//                "parentDir='" + parentDir + '\'' +
//                "filename='" + filename + '\'' +
                "remotePath=" + remotePath + '\'' +
                "localPath='" + localPath + '\'' +
                ", mtime=" + remoteMTime + " ms" +
//                ", size=" + remoteSize + " bytes" +
                '}';
    }
}
