package com.ericsson.sim.common.history.model;

import java.util.*;
import java.util.stream.Collectors;

public class ProgressModel {
    private int total = 0;
    private Map<String, ActiveFile> activeFiles = new LinkedHashMap<>();

    public void initializeMap(boolean createNew) {
        if (createNew) {
            this.activeFiles = new LinkedHashMap<>();
            this.total = 0;
        } else if (this.activeFiles == null) {
            this.activeFiles = new LinkedHashMap<>();
            this.total = 0;
        }
    }

    /**
     * Add file to progress model
     * @param remotePath Absolute path to remote file
     * @param activeFiles Active file model
     */
    public void add(String remotePath, ActiveFile activeFiles) {
        this.activeFiles.put(remotePath, activeFiles);
    }

    public ActiveFile get(String remotePath) {
        return this.activeFiles.get(remotePath);
    }

    public boolean containsKey(String remotePath) {
        return this.activeFiles.containsKey(remotePath);
    }

    public void remove(String remotePath) {
        this.activeFiles.remove(remotePath);
    }

    public void clear() {
        this.activeFiles.clear();
    }

    public int getCount() {
        this.total = this.activeFiles.size();
        return this.total;
    }

    /**
     * Get list of remote files absolute path
     *
     * @return Unmodifiable list of remote file absolute paths
     */
    public List<String> getFileNames() {
        return Collections.unmodifiableList(new ArrayList<>(activeFiles.keySet()));
    }

    /**
     * Get set of remote files absolute path
     *
     * @return Unmodifiable set of remote file absolute paths
     */
    public Set<String> getFileNamesSet() {
        return Collections.unmodifiableSet(new HashSet<>(activeFiles.keySet()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProgressModel that = (ProgressModel) o;
        if (this.activeFiles == null && that.activeFiles == null) {
            return true;
        } else if (this.activeFiles == null) {
            return false;
        } else if (that.activeFiles == null) {
            return false;
        } else {
            return this.total == that.total && this.activeFiles.keySet().equals(that.activeFiles.keySet());
        }
    }

    @Override
    public int hashCode() {
        return activeFiles.keySet().hashCode();
    }

    @Override
    public String toString() {
        return "{\ntotal='" + total + '\'' +
                "\nfiles= {" + (activeFiles == null ? "null" : activeFiles.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n\t"))) +
                '}';
    }

}
