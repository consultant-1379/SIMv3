package com.ericsson.sim.common.history;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;

public class HistoryManager {
    private static final Logger logger = LogManager.getLogger(HistoryManager.class);


    private final HashMap<String, HistoryFile> cache = new HashMap<>();
    public static final HistoryManager instance = new HistoryManager();

    private HistoryManager() {
    }

    public HistoryFile getHistory(String uniqueId, File historyDir) throws IOException {

        logger.debug("Get history for {}", uniqueId);
        if (!historyDir.exists()) {
            synchronized (this) {
                if (!historyDir.exists()) {
                    if (!historyDir.mkdir()) {
                        throw new FileNotFoundException("Failed to find or create path " + historyDir.getAbsolutePath());
                    }
                }
            }
        }

        HistoryFile history;
        File historyFile = Paths.get(historyDir.getAbsolutePath(), uniqueId + ".json").toFile();

        synchronized (cache) {
            if (!cache.containsKey(uniqueId)) {
                logger.debug("{} not initialized, initializing it", uniqueId);
                history = new HistoryFile(historyFile);
                cache.put(uniqueId, history);
            } else {
                logger.debug("{} already initialized", uniqueId);
                history = cache.get(uniqueId);
                if (!history.exists()) {
                    logger.info("Seems like history file was removed since last execution. Creating again");
                    cache.remove(uniqueId);
                    history = new HistoryFile(historyFile);
                    cache.put(uniqueId, history);
                }
            }
        }

//        synchronized (history) {
//            if (!history.isOpen()) {
        //will check if the file is already open or not
        history.read(true);
//            }
//        }
//
        return history;
    }
}
