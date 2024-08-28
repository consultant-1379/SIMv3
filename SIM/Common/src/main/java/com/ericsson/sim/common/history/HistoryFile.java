package com.ericsson.sim.common.history;

import com.ericsson.sim.common.history.model.FileAttributes;
import com.ericsson.sim.common.history.model.HistoryModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class HistoryFile extends RuntimeFile {
    private final File historyFile;
    private final File oldHistoryFileRef;

    private HistoryModel historyModel = null;
    private boolean alreadyLoaded = false;

    private static final Logger logger = LogManager.getLogger(HistoryFile.class);

    public HistoryFile(File historyFile) throws IOException {
        this.historyFile = historyFile;
        oldHistoryFileRef = Paths.get(historyFile.getParentFile().getAbsolutePath(), historyFile.getName() + ".old").toFile();

        initializeFile();
    }

    @Override
    protected void initializeFile() throws IOException {
        boolean createNew = false;
        if (!historyFile.exists()) {

            createNew = true;

            logger.debug("Unable to find {}. Will check if there is a old file that can be used", historyFile.getAbsolutePath());
            if (oldHistoryFileRef.exists()) {
                logger.info("{} was not found but an old saved file {} was found. It maybe that previous save did not complete successfully. Going to use {} as history file",
                        historyFile.getName(), oldHistoryFileRef.getName(), oldHistoryFileRef.getName());

                if (tryMove(oldHistoryFileRef, historyFile)) {
                    logger.debug("File {} renamed to {}", oldHistoryFileRef.getName(), historyFile.getName());
                    createNew = false;
                } else {
                    logger.debug("File renaming has failed, will try to copy and remove");
                    if (tryCopy(oldHistoryFileRef, historyFile)) {
                        logger.debug("File {} copied to {}. Removing old", oldHistoryFileRef.getName(), historyFile);
                        tryRemove(oldHistoryFileRef);
                    }
                }
            }
        }

        if (createNew) {
            logger.debug("Creating history file {}", historyFile.getAbsolutePath());
            if (!historyFile.createNewFile()) {
                throw new IOException("Unable to create history file " + historyFile.getName());
            }
        }

        if (!historyFile.setReadOnly()) {
            logger.info("Unable to set the history file {} to readonly. This should not affect operations but is set against accidental changes or removal", historyFile.getName());
        }
    }

    @Override
    public boolean exists() {
        return this.historyFile.exists();
    }

    @Override
    public synchronized void read(boolean checkLoaded) throws IOException {

        if (checkLoaded && alreadyLoaded) {
            logger.debug("History file {} is already open, will not read it again", historyFile.getName());
            return;
        }

        try {
            loadHistory(historyFile);
        } catch (IOException | JsonIOException | JsonSyntaxException e) {
            logger.error("Failed to load history file, trying old history file", e);

            loadHistory(oldHistoryFileRef);
            //if we are able to load old history file, this means there was something wrong with the
            //history file. we move old to history
            if (tryMove(oldHistoryFileRef, historyFile)) {
                logger.debug("File {} renamed to {}", oldHistoryFileRef.getName(), historyFile.getName());
            } else {
                logger.debug("File renaming has failed, will try to copy and remove");
                if (tryCopy(oldHistoryFileRef, historyFile)) {
                    logger.debug("File {} copied to {}. Removing old", oldHistoryFileRef.getName(), historyFile);
                    tryRemove(oldHistoryFileRef);
                }
            }
        }

        alreadyLoaded = true;
    }


    @Override
    public synchronized void save() throws IOException {
        if (!alreadyLoaded) {
            logger.warn("History file is not open, cannot save it");
            return;
        }

        if (historyModel != null) {
            try {
                //before doing anything on history file, copy it so that we have a backup
                if (!tryCopy(historyFile, oldHistoryFileRef)) {
                    logger.info("Failed to backup history. Backup will not be available if history file is corrupted, or a old history may be available");
                }

                if (!historyFile.setWritable(true, true)) {
                    logger.warn("Failed to set ths history file {} to writeable. This may result in failure to saving the history.", historyFile.getName());
                }

                try (FileWriter writer = new FileWriter(historyFile)) {
                    logger.info("Saving history of {} files in file {}", historyModel.getCount(), historyFile.getAbsolutePath());
                    if (logger.isTraceEnabled()) {
                        logger.trace("Writing history: {}", historyModel.toString());
                    }
                    Gson gson = new GsonBuilder()
//                            .setPrettyPrinting()
                            .create();
                    gson.toJson(historyModel, writer);
                }

                logger.debug("Removing the old file as it has been copied to {}", historyFile.getName());
                tryRemove(oldHistoryFileRef);
            } finally {
                if (!historyFile.setReadOnly()) {
                    logger.info("Unable to set the history file {} to readonly. This should not affect operations but is set against accidental changes or removal", historyFile.getName());
                }
            }
        } else {
            logger.warn("History model is null, nothing will be saved save anything");
        }
    }


    public synchronized void addFile(String remotePath, FileAttributes model) {
        historyModel.add(remotePath, model);
    }

    public synchronized FileAttributes getFile(String remotePath) {
        return historyModel.get(remotePath);
    }

    public synchronized boolean hasFile(String remotePath) {
        return historyModel.containsKey(remotePath);
    }

    public synchronized void removeFile(String remotePath) {
        historyModel.remove(remotePath);
    }

    public synchronized List<String> getFileNames() {
        return historyModel.getFileNames();
    }

    public synchronized Set<String> getFileNamesSet() {
        return historyModel.getFileNamesSet();
    }

    public synchronized boolean isAlreadyLoaded() {
        return alreadyLoaded;
    }

    private void loadHistory(File file) throws IOException, JsonIOException, JsonSyntaxException {
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new GsonBuilder().create();
//            Type type = new TypeToken<Map<String, FileAttributes>>() {
//            }.getType();
//
//            historyModel = gson.fromJson(reader, type);
            historyModel = gson.fromJson(reader, HistoryModel.class);
            if (historyModel == null) {
                logger.debug("Read model from history file {} is null. Creating one", file.getName());
//                historyModel = new HashMap<>();
                historyModel = new HistoryModel();
            }
            historyModel.initializeMap(false);
            if (logger.isTraceEnabled() && historyModel != null) {
//                logger.trace("Read history: {}", Arrays.toString(historyModel.entrySet().toArray()));
                logger.trace("Read history: {}", historyModel.toString());
            }
        }
    }

    @Override
    public File getFile() {
        return historyFile;
    }

    @Override
    public synchronized void close() {
        alreadyLoaded = false;
        historyModel.clear();
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    public static String getRemotePath(String parentPath, String filename) {
        //SFTP protocol mandates that / must be used as a path separator.
        if (parentPath.endsWith("/")) {
            return parentPath + filename;
        } else {
            return parentPath + "/" + filename;
        }
    }
}
