package com.ericsson.sim.common.history.model;

import java.util.*;
import java.util.stream.Collectors;

public class HistoryModel {
    private int total = 0;
    private Map<String, FileAttributes> files = new LinkedHashMap<>();

    public void initializeMap(boolean createNew) {
        if (createNew) {
            this.files = new LinkedHashMap<>();
            this.total = 0;
        } else if (this.files == null) {
            this.files = new LinkedHashMap<>();
            this.total = 0;
        }
    }

    /**
     * Add file to history model
     *
     * @param remotePath Absolute path to remote file
     * @param attributes File attributes
     */
    public void add(String remotePath, FileAttributes attributes) {
        this.files.put(remotePath, attributes);
    }

    public FileAttributes get(String remotePath) {
        return this.files.get(remotePath);
    }

    public boolean containsKey(String remotePath) {
        return this.files.containsKey(remotePath);
    }

    public void remove(String remotePath) {
        this.files.remove(remotePath);
    }

    public void clear() {
        this.files.clear();
    }

    public int getCount() {
        this.total = this.files.size();
        return this.total;
    }

    public List<String> getFileNames() {
        return Collections.unmodifiableList(new ArrayList<>(files.keySet()));
    }

    public Set<String> getFileNamesSet() {
        return Collections.unmodifiableSet(new HashSet<>(files.keySet()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoryModel that = (HistoryModel) o;
        if (this.files == null && that.files == null) {
            return true;
        } else if (this.files == null) {
            return false;
        } else if (that.files == null) {
            return false;
        } else {
            return this.total == that.total && this.files.keySet().equals(that.files.keySet());
        }
    }

    @Override
    public int hashCode() {
        return files.keySet().hashCode();
    }

    @Override
    public String toString() {
        return "{\ntotal='" + total + '\'' +
                "\nfiles= {" + (files == null ? "null" : files.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n\t"))) +
                '}';
    }
}
