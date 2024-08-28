package com.ericsson.sim.engine.nodetype;

import com.ericsson.sim.engine.nodetype.model.NodeType;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

public class NodeTypeConfigLoader {
    private static final Logger logger = LogManager.getLogger(NodeTypeConfigLoader.class);

    private NodeTypeConfigLoader() {
    }

    public static Map<String, NodeType> readTypes(String dirPath) throws IOException {
        if (dirPath == null) {
            logger.error("Provided JAR directory path is null. Will not be able to load node types");
            return null;
        }
        return readTypes(new File(dirPath));
    }

    public static Map<String, NodeType> readTypes(File dirPath) throws IOException {

        if (dirPath == null) {
            logger.error("Provided JAR directory path is null. Will not be able to load node types");
            return null;
        }

        if (!dirPath.exists()) {
            logger.error("Provided JAR directory path {} does not exists. Will not be able to load node types", dirPath.getAbsolutePath());
            return null;
        }

        Map<String, NodeType> nodeTypes = new HashMap<>();

        logger.info("Searching for node types in {}", dirPath.getAbsolutePath());
        File[] jars = dirPath.listFiles(pathname -> pathname.getName().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            logger.warn("No node type jars found.");
            return nodeTypes;
        }

        for (File jar : jars) {

            logger.debug("Loading {}", jar.getAbsolutePath());
            try {
                NodeType readType = readType(jar);

                if (readType == null) {
                    continue;
                }
                if (nodeTypes.containsKey(readType.getName())) {
                    logger.error("Node type {} is already loaded. Discarding it form {} as it already exists", readType.getName(), jar.getName());
                    continue;
                }

                logger.debug("Adding valid node type {} to collection", readType.getName());
                nodeTypes.put(readType.getName(), readType);
            } catch (IOException e) {
                logger.error("Error while reading {}. {}.Would be skipped", e, jar.getName());
            }
        }

        return nodeTypes;
    }

    public static NodeType readType(String jarPath) throws IOException {
        if (jarPath == null) {
            logger.error("Provided JAR path is null. Will not be able to load node types");
            return null;
        }

        return readType(new File(jarPath));
    }

    public static NodeType readType(File jarPath) throws IOException {
        if (jarPath == null) {
            logger.error("Provided JAR  path is null. Will not be able to load node types");
            return null;
        }

        if (!jarPath.exists()) {
            logger.error("Provided JAR path {} does not exists. Will not be able to load node types", jarPath.getAbsolutePath());
            return null;
        }
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toURI().toURL()},
                NodeTypeConfigLoader.class.getClassLoader());

        final String nodeTypeName = jarPath.getName().replaceAll("[.][^.]+$", "");
        logger.debug("Node type name expected is: {}", nodeTypeName);

        final String configName = nodeTypeName + ".json";
        logger.debug("Finding {} from loaded jar {}", configName, jarPath.getName());

        URL config = classLoader.findResource(configName);

        if (config == null) {
            logger.error("Failed to find {} in {}. The jar must contain the required resource.", configName, jarPath.getName());
            throw new IOException("Failed to find " + configName + " in " + jarPath.getName());
        }

        logger.debug("Found {}", config.toString());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(config.openStream()))) {
            Gson gson = new Gson();
            NodeType readType = gson.fromJson(reader, NodeType.class);
            logger.debug("Read node type named: {}", readType.getName());
            if (!nodeTypeName.equals(readType.getName())) {
                logger.error("'name' property value '{}' is not same as jar name {}", readType.getName(), nodeTypeName);
                throw new IOException("Value of 'name' property in config must be same as jar name (excluding .jar extension)");
            }
            return readType;
        }

    }
}
