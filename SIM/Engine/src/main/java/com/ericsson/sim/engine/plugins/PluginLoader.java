package com.ericsson.sim.engine.plugins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PluginLoader {
    private static final Logger logger = LogManager.getLogger(PluginLoader.class);

    private final String pluginFolderPath;

    public PluginLoader(String pluginFolderPath) {
        this.pluginFolderPath = pluginFolderPath;

    }
}
