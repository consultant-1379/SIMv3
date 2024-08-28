package com.ericsson.sim.common.history;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;

public class AppendProgressManager {
    private static final Logger logger = LogManager.getLogger(AppendProgressManager.class);

    private final HashMap<String, AppendProgressFile> cache = new HashMap<>();
    public static final AppendProgressManager instance = new AppendProgressManager();

    private AppendProgressManager() {
    }

    public AppendProgressFile getProgress(String uniqueId, File historyDir) throws IOException {
        logger.debug("Get progress for {}", uniqueId);
        if (!historyDir.exists()) {
            synchronized (this) {
                if (!historyDir.exists()) {
                    if (!historyDir.mkdir()) {
                        throw new FileNotFoundException("Failed to find or create path " + historyDir.getAbsolutePath());
                    }
                }
            }
        }

        AppendProgressFile appendProgressFile;
        File progressFile = Paths.get(historyDir.getAbsolutePath(), "appendprogress_" + uniqueId + ".json").toFile();

        synchronized (cache) {
            if (!cache.containsKey(uniqueId)) {
                logger.debug("{} not initialized, initializing it", uniqueId);
                appendProgressFile = new AppendProgressFile(progressFile);
                cache.put(uniqueId, appendProgressFile);
            } else {
                logger.debug("{} already initialized", uniqueId);
                appendProgressFile = cache.get(uniqueId);
                if (!appendProgressFile.exists()) {
                    logger.info("Seems like progress file was removed since last execution. Creating again");
                    cache.remove(uniqueId);
                    appendProgressFile = new AppendProgressFile(progressFile);
                    cache.put(uniqueId, appendProgressFile);
                }
            }
        }

//        ConcurrentHashMap<String, AppendProgressFile> cus = new ConcurrentHashMap<>();
//        cus.computeIfPresent(uniqueId, (key, value) -> {
//            if (!value.exists()) {
//                return new AppendProgressFile(progressFile);
//            }
//        });

        appendProgressFile.read(true);
        return appendProgressFile;
    }
}
