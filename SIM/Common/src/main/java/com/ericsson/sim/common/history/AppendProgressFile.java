package com.ericsson.sim.common.history;

import com.ericsson.sim.common.history.model.ActiveFile;
import com.ericsson.sim.common.history.model.ProgressModel;
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

public class AppendProgressFile extends RuntimeFile {

    private static final Logger logger = LogManager.getLogger(AppendProgressFile.class);

    private final File progressFile;
    private final File oldProgressFileRef;

    private ProgressModel progressModel = null;
    private boolean alreadyLoaded = false;

    AppendProgressFile(File progressFile) throws IOException {
        this.progressFile = progressFile;
        oldProgressFileRef = Paths.get(progressFile.getParentFile().getAbsolutePath(), progressFile.getName() + ".old").toFile();

        initializeFile();
    }

    @Override
    protected void initializeFile() throws IOException {
        boolean createNew = false;
        if (!progressFile.exists()) {

            createNew = true;

            logger.debug("Unable to find {}. Will check if there is a old file that can be used", progressFile.getAbsolutePath());
            if (oldProgressFileRef.exists()) {
                logger.info("{} was not found but an old saved file {} was found. It maybe that previous save did not complete successfully. Going to use {} as progress file",
                        progressFile.getName(), oldProgressFileRef.getName(), oldProgressFileRef.getName());

                if (tryMove(oldProgressFileRef, progressFile)) {
                    logger.debug("File {} renamed to {}", oldProgressFileRef.getName(), progressFile.getName());
                    createNew = false;
                } else {
                    logger.debug("File renaming has failed, will try to copy and remove");
                    if (tryCopy(oldProgressFileRef, progressFile)) {
                        logger.debug("File {} copied to {}. Removing old", oldProgressFileRef.getName(), progressFile);
                        tryRemove(oldProgressFileRef);
                    }
                }
            }
        }

        if (createNew) {
            logger.debug("Creating progress file {}", progressFile.getAbsolutePath());
            if (!progressFile.createNewFile()) {
                throw new IOException("Unable to create progress file " + progressFile.getName());
            }
        }

        if (!progressFile.setReadOnly()) {
            logger.info("Unable to set the progress file {} to readonly. This should not affect operations but is set against accidental changes or removal", progressFile.getName());
        }
    }

    @Override
    public boolean exists() {
        return progressFile.exists();
    }

    @Override
    public void read(boolean checkLoaded) throws IOException {
        if (checkLoaded && alreadyLoaded) {
            logger.debug("Append progress file {} is already open, will not read it again", progressFile.getName());
            return;
        }

        try {
            loadProgress(progressFile);
        } catch (IOException | JsonIOException | JsonSyntaxException e) {
            logger.error("Failed to load progress file, trying old progress file");
            logger.throwing(e);

            loadProgress(oldProgressFileRef);
            if (tryMove(oldProgressFileRef, progressFile)) {
                logger.debug("File {} renamed to {}", oldProgressFileRef.getName(), progressFile.getName());
            } else {
                logger.debug("File renaming has failed, will try to copy and remove");
                if (tryCopy(oldProgressFileRef, progressFile)) {
                    logger.debug("File {} copied to {}. Removing old", oldProgressFileRef.getName(), progressFile);
                    tryRemove(oldProgressFileRef);
                }
            }
        }

        alreadyLoaded = true;
    }

    @Override
    public void save() throws IOException {
        if (!alreadyLoaded) {
            logger.warn("Append progress file is not open, cannot save it");
            return;
        }

        if (progressModel != null) {
            try {
                //before doing anything on progress file, copy it so that we have a backup
                if (!tryCopy(progressFile, oldProgressFileRef)) {
                    logger.info("Failed to backup progress. Backup will not be available if progress file is corrupted, or a old progress may be available");
                }

                if (!progressFile.setWritable(true, true)) {
                    logger.warn("Failed to set ths progress file {} to writeable. This may result in failure to saving the progress.", progressFile.getName());
                }

                try (FileWriter writer = new FileWriter(progressFile)) {
                    logger.info("Saving progress of {} files in file {}", progressModel.getCount(), progressFile.getAbsolutePath());
                    if (logger.isTraceEnabled()) {
                        logger.trace("Writing progress: {}", progressModel.toString());
                    }
                    Gson gson = new GsonBuilder()
                            .setPrettyPrinting()
                            .create();
                    gson.toJson(progressModel, writer);
                }

                logger.debug("Removing the old file as it has been copied to {}", progressFile.getName());
                tryRemove(oldProgressFileRef);
            } finally {
                if (!progressFile.setReadOnly()) {
                    logger.info("Unable to set the progress file {} to readonly. This should not affect operations but is set against accidental changes or removal", progressFile.getName());
                }
            }
        } else {
            logger.warn("Append progress model is null, nothing will be saved save anything");
        }
    }

    public synchronized void addFile(String remotePath, ActiveFile model) {
        progressModel.add(remotePath, model);
    }

    public synchronized ActiveFile getFile(String remotePath) {
        return progressModel.get(remotePath);
    }

    public synchronized boolean hasFile(String remotePath) {
        return progressModel.containsKey(remotePath);
    }

    public synchronized void removeFile(String remotePath) {
        progressModel.remove(remotePath);
    }

    /**
     * Get list of remote files absolute path
     *
     * @return Unmodifiable list of remote file absolute paths
     */
    public synchronized List<String> getFileNames() {
        return progressModel.getFileNames();
    }

    /**
     * Get set of remote files absolute path
     *
     * @return Unmodifiable set of remote file absolute paths
     */
    public synchronized Set<String> getFileNamesSet() {
        return progressModel.getFileNamesSet();
    }

    public synchronized boolean isAlreadyLoaded() {
        return alreadyLoaded;
    }

    private void loadProgress(File file) throws IOException, JsonIOException, JsonSyntaxException {
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new GsonBuilder().create();

            progressModel = gson.fromJson(reader, ProgressModel.class);
            if (progressModel == null) {
                logger.debug("Read model from append progress file {} is null. Creating one", file.getName());

                progressModel = new ProgressModel();
            }
            progressModel.initializeMap(false);

            if (logger.isTraceEnabled() && progressFile != null) {
                logger.trace("Read progress: {}", progressFile.toString());
            }
        }
    }

    @Override
    public File getFile() {
        return progressFile;
    }

    @Override
    public synchronized void close() {
        alreadyLoaded = false;
        progressModel.clear();
    }


    @Override
    protected Logger getLogger() {
        return logger;
    }


}
