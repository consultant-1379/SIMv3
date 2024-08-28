package com.ericsson.sim.engine.config;

import com.ericsson.sim.common.Constants;
import com.ericsson.sim.common.config.AppConfig;
import com.ericsson.sim.common.util.HelpUtil;
import com.ericsson.sim.engine.model.RuntimeConfig;
import com.ericsson.sim.engine.nodetype.NodeTypeConfigBuilder;
import com.ericsson.sim.engine.nodetype.NodeTypeConfigLoader;
import com.ericsson.sim.engine.nodetype.model.NodeType;
import com.ericsson.sim.engine.nodetype.model.Properties;
import com.ericsson.sim.engine.nodetype.model.converter.Converter;
import com.ericsson.sim.engine.nodetype.model.converter.ConverterException;
import com.ericsson.sim.engine.nodetype.model.converter.ConverterFactory;
import com.ericsson.sim.plugins.rmi.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.rmi.Remote;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigService implements Remote {
    /**
     * There are multiple configurations that are in play here
     * 1- Application configuration that can handle different aspects of application
     * 2- Note type configuration that are either read at start of program, or when pushed through the RMI interface
     * 3- NE configuration which can be changed any time and should be read without external pushing
     * 4- Runtime configuration which is combination of NE configuration and node type
     * <p>
     * Configuration are used by jobs, so they should read whatever is available and then work on it during their execution
     * Config server can provide a copy of these configurations
     */

    private static final Logger logger = LogManager.getLogger(ConfigService.class);


    private Map<String, NodeType> nodeTypes;
    private final AppConfig appConfig;

    public ConfigService(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }

    /**
     * Add a new node type to existing types
     *
     * @param jarPath Path to jar file to be loaded
     * @return Update map of node type
     */
    public synchronized Server.AddNodeResult addNodeType(String jarPath) throws IOException {
        logger.debug("Going to read node type from jar {}", jarPath);
        if (jarPath == null) {
            logger.error("Provided JAR path is null. Will not be able to load node types");
            return Server.AddNodeResult.INVALID_PATH;
        }

        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            logger.error("Provided JAR path {} does not exists. Will not be able to load node types", jarPath);
            return Server.AddNodeResult.INVALID_PATH;
        }

        NodeType nodeType = NodeTypeConfigLoader.readType(jarFile);

        if (nodeType == null) {
            return Server.AddNodeResult.FAILURE;
        }
        if (nodeTypes.containsKey(nodeType.getName())) {
            logger.warn("Node type {} is already loaded. Will not load it again from {}", nodeType.getName(), jarPath);
            return Server.AddNodeResult.ALREADY_EXISTS;
        }

        logger.debug("Read type {}", nodeType.getName());
        logger.debug("Merging {} with its base types", nodeType.getName());
        nodeType = NodeTypeConfigBuilder.mergeHierarchy(nodeTypes, nodeType);
        if (nodeType == null) {
            return Server.AddNodeResult.FAILURE;
        }

        nodeTypes.put(nodeType.getName(), nodeType);
        return Server.AddNodeResult.ADDED;
    }

    /**
     * Load node types from configured path.
     *
     * @param reload Reload all node types again, discarding the old ones
     * @return Map containing node types. Map keys are node type names, and value hold {@link NodeType} object
     */
    public synchronized Map<String, NodeType> getNodeTypes(boolean reload) {
        if (!reload && nodeTypes != null && nodeTypes.size() != 0) {
            return nodeTypes;
        }

        try {
            nodeTypes = NodeTypeConfigLoader.readTypes(appConfig.getPaths().getNodeTypes());
            if (nodeTypes == null) {
                return null;
            }
        } catch (IOException e) {
            logger.error("Failed to load node types", e);
            return null;
        }
        nodeTypes = NodeTypeConfigBuilder.mergeHierarchy(nodeTypes);
        if (nodeTypes.size() == 0) {
            logger.error("No node types read.");
            return null;
        }

        return nodeTypes;
    }

    /**
     * Read NE config file and return list of lines split according to delimiter
     *
     * @param raiseError Raise {@link RuntimeException} if an error is encountered
     * @return List of NE config lines or null in case any error is encountered and {@code raiseError} is false
     */
    public List<NetworkElementConfigReader.Line> loadNetworkElementConfig(boolean raiseError) {
        String configPath = appConfig.getPaths().getNeConfig();
        NetworkElementConfigReader neConfigReader = new NetworkElementConfigReader(configPath, appConfig.getNeConfig().getCommentStart(), appConfig.getNeConfig().getDelimiter());
        List<NetworkElementConfigReader.Line> configuredNE = null;
        try {
            configuredNE = neConfigReader.read();
        } catch (Throwable e) {
            logger.error("Unable to read NE config", e);
            if (raiseError) {
                throw new RuntimeException(e);
            }
            return null;
        }

        if (configuredNE == null || configuredNE.size() == 0) {
            logger.error("NE config contains no network element.");
            return null;
        }
        return configuredNE;
    }

    /**
     * Create an effective configuration for current node types defined and network elements configuration
     *
     * @param nodeTypes    Known node types
     * @param configuredNE NE config read
     * @param raiseError   Raise {@link RuntimeException} if an error is encountered
     * @return Map containing runtime configuration. Key is combined primary keys defined in node type. Value is the runtime configuration.
     * A null map can be returned in case {@code raiseError} is set to false
     */
    public Map<String, RuntimeConfig> getRuntimeConfig(Map<String, NodeType> nodeTypes, List<NetworkElementConfigReader.Line> configuredNE, boolean raiseError) {
        Map<String, RuntimeConfig> runtimeConfig = new LinkedHashMap<>();
        logger.debug("Building runtime config from node types and network element config");

        final NetworkElementConfigReader.Line headers = configuredNE.get(0);
        final int pluginNameIndex = getPluginNameIndex(headers.line);

        if (pluginNameIndex < 0) {
            logger.error("Mandatory property '{}' not found. Cannot use this config", Constants.HEADER_PLUGIN_NAMES);
            if (raiseError) {
                throw new RuntimeException("Configuration contain errors. Mandatory property '" + Constants.HEADER_PLUGIN_NAMES + "' not found.");
            }
            return runtimeConfig;
        }

        for (int row = 1; row < configuredNE.size(); row++) {
            final NetworkElementConfigReader.Line line = configuredNE.get(row);
            if (logger.isDebugEnabled()) {
                logger.debug("Taking line: {}", String.join(",", line.line));
            }
            if (line.line.length != headers.line.length) {
                logger.error("Number of properties in line {} are not same as number of headers. {} != {}. Line ignored", line.row, line.line.length, headers.line.length);
                if (raiseError) {
                    throw new RuntimeException("Configuration contain errors. Number of properties in line " + line.row + " are not same as number of header");
                }
                continue;
            }

            final String pluginName = line.line[pluginNameIndex];
            final NodeType nodeType = nodeTypes.get(pluginName);

            if (nodeType == null) {
                logger.error("Plugin {} does not correspond to any known node type. Node at line {} will be ignored", pluginName, line.row);
                if (raiseError) {
                    throw new RuntimeException("Configuration contain errors. Plugin '" + pluginName + "' does not correspond to any know node type");
                }
                continue;
            }

            AbstractMap.SimpleEntry<String, RuntimeConfig> entry = buildRuntimeConfigFor(nodeType, headers.line, line.line, line.row, raiseError);
            if (entry != null) {
                //if there is a local file pattern provided, there is extra verification that should be done now
                final String localFilePattern = entry.getValue().getPropertyAsString(Constants.CONFIG_FILE_RENAME_PATTERN, null);
                final String remoteFilePattern = entry.getValue().getPropertyAsString(Constants.CONFIG_REMOTE_FILE_REGEX, null);
                if (localFilePattern != null && !"".equals(localFilePattern)) {
                    logger.debug("{} provided {}", Constants.CONFIG_FILE_RENAME_PATTERN, localFilePattern);
                    if (remoteFilePattern != null && !"".equals(remoteFilePattern)) {
                        logger.debug("{} provided {}", Constants.CONFIG_REMOTE_FILE_REGEX, remoteFilePattern);
                        verifyLocalFileNamePattern(remoteFilePattern, localFilePattern, raiseError);
                    } else {
                        logger.info("A {} is provided without {}. Will not be able to verify the {}",
                                Constants.CONFIG_FILE_RENAME_PATTERN, Constants.CONFIG_REMOTE_FILE_REGEX, Constants.CONFIG_FILE_RENAME_PATTERN);
                    }
                }


                logger.info("Adding {} to runtime config", entry.getKey());
                runtimeConfig.put(entry.getKey(), entry.getValue());
            }
        }

        return runtimeConfig;
    }

    private AbstractMap.SimpleEntry<String, RuntimeConfig> buildRuntimeConfigFor(NodeType nodeType, String[] headers, String[] line, int row, boolean raiseError) {
        StringBuilder primaryKey = new StringBuilder();
        RuntimeConfig runtimeConfig = new RuntimeConfig();

        //Build the map for easy access
        HashMap<String, String> neConfigMap = new HashMap<>(headers.length);
        for (int column = 0; column < headers.length; column++) {
            neConfigMap.put(headers[column], line[column]);
        }

        List<String> primaryKeyHolder = new ArrayList<>(10);

        //Loop though node type configurations and set the values that were provided, leaving the rest
        for (final Map.Entry<String, Properties> entry : nodeType.getProperties().entrySet()) {
            final String value = neConfigMap.getOrDefault(entry.getKey(), null);
            final Properties property = entry.getValue();

            logger.debug("Taking {}={}", entry.getKey(), value);
            if (value == null && property.isRequired()) {
                logger.error("Value for a required property {} was not provided. Node at line {} will be ignored", entry.getKey(), row);
                if (raiseError) {
                    throw new RuntimeException("Value for a required property " + entry.getValue() + " was not provided for node at line " + row);
                }
                return null;
            }

            try {
                if (property.getType() == null) {
                    logger.error("Missing dataType for node type {}. Cannot use configuration for node at line {}", property.getName(), row);
                    if (raiseError) {
                        throw new RuntimeException("Missing dataType for node type " + property.getName() + " at line " + row);
                    }
                    return null;
                }
                logger.debug("Converting {} using property type {}", value, property.getType().getType());
                Converter<?> converter = ConverterFactory.getInstance().getConverter(property);
                Object convertedValue = converter.convert(value, property);
                logger.debug("Adding {}={} after conversion", entry.getKey(), convertedValue);

                if (property.isPrimary()) {
//                    primaryKey.append("_").append(convertedValue);
                    primaryKeyHolder.add("" + convertedValue);
                }

                runtimeConfig.addProperty(entry.getKey(), convertedValue);

            } catch (ConverterException e) {
                logger.error("{}. Node at line {} will be ignored", e.getMessage(), row);
                if (raiseError) {
                    throw new RuntimeException("Node configuration at line " + row + " has errors. " + e.getMessage());
                }
                return null;
            }
        }

        //Check if any headers will be ignored as they are not supported by node type
        for (final String key : neConfigMap.keySet()) {
            if (!nodeType.getProperties().containsKey(key)) {
                logger.warn("A configuration key '{}' is not known to node type {}. Header will be ignored", key, nodeType.getName());
            }
        }

        //sort primary key holder
        Collections.sort(primaryKeyHolder);
        for (String key : primaryKeyHolder) {
            primaryKey.append("_").append(key);
        }

        return new AbstractMap.SimpleEntry<>(primaryKey.length() > 0 ? primaryKey.substring(1) : primaryKey.toString(), runtimeConfig);
    }

    private int getPluginNameIndex(String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            if (Constants.HEADER_PLUGIN_NAMES.equals(headers[i])) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Additional verification to make sure local file name rename pattern provided can be used.
     * It checks:
     * 1. Group references used and if they can be captured from remote file name expression
     * 2. Replacement variables used are supported or not. It only gives warning in logs in this case and do not raise error
     */
    private boolean verifyLocalFileNamePattern(String remoteFileRegex, String localFilePattern, boolean raiseError) {
        final int groupCount = HelpUtil.captureGroupCount(remoteFileRegex);
        final int maxReplace = maxGroupUsed(localFilePattern);

        if (maxReplace > groupCount) {
            logger.error("Max group replace in local file pattern '" + localFilePattern + " is outside range of capturable groups. Total capturable group are " + groupCount);
            if (raiseError) {
                throw new RuntimeException("Max group replace in local file pattern '" + localFilePattern + " is outside range of capturable groups");
            }
            return false;
        }

        //create a sample to verify the findings
        //first create a sample regex

        logger.debug("Check pattern against sample regex");
        int replicate = maxReplace;
        if (maxReplace <= 0) {
            //we still check it once. There is no harm, but it does check if there is something like $a in pattern
            replicate = 1;
        }
        final Pattern sampleRegex = Pattern.compile(String.join("", Collections.nCopies(replicate, "(\\d)")));
        final String sampleString = String.join("", Collections.nCopies(replicate, "1"));

        logger.debug("Sample regex created: {}", sampleRegex.pattern());
        logger.debug("Sample string created: {}", sampleString);

        Matcher sampleMatcher = sampleRegex.matcher(sampleString);
        try {
            sampleMatcher.matches();

            logger.debug("Replacing group references in pattern from captured groups from sample string");
            sampleMatcher.replaceFirst(localFilePattern);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to verify the pattern '" + localFilePattern + "'. If a $ is used it should either be escaped with \\ for literal use, or have a positive number after for proper replacement i.e. $1", e);
            if (raiseError) {
                throw new RuntimeException("Illegal group reference in pattern: " + localFilePattern);
            }
            return false;
        } catch (IndexOutOfBoundsException e) {
            logger.warn("Failed to verify the pattern '" + localFilePattern + "'. Pattern may have a group reference that is not captured by regex", e);
            if (raiseError) {
                throw new RuntimeException("A group reference is outside bound of captured groups for pattern: " + localFilePattern);
            }
            return false;
        }

        return true;
    }

    /**
     * Checks the max number of group reference used in a pattern i.e. localfilename_$1_$2.xml would return 2
     *
     * @param pattern Local file name replace pattern
     * @return Max number of group reference used
     */
    public static int maxGroupUsed(final String pattern) {
        logger.debug("Counting groups references in pattern: {}", pattern);
        int max = 0;
        if (pattern == null) {
            return max;
        }

        Pattern replacePattern = Pattern.compile("(\\$\\d+)");
//        Pattern replacePattern = Pattern.compile("(\\$-?\\d+)");
//        Pattern replacePattern = Pattern.compile("((?<!\\\\)\\$-?\\d+)");
        Matcher localFileMatcher = replacePattern.matcher(pattern);

        while (localFileMatcher.find()) {
            for (int i = 1; i <= localFileMatcher.groupCount(); i++) {
                String captured = localFileMatcher.group(1).replace("$", "");
                logger.debug("Checking capture group reference: {}", captured);
                if (captured.startsWith("-")) {
                    logger.warn("A group reference points to a negative number. Groups reference must be equal to or greater than 0. We got: {}", captured);
                    throw new RuntimeException("Illegal group reference '" + captured + "' in pattern: " + pattern);
                }
                if (HelpUtil.isInteger(captured)) {
                    int replacementId = Integer.parseInt(captured);
//                    if (replacementId < 0) {
//                        throw new IllegalArgumentException("In " + pattern + ", group reference must not be negative");
//                    }
                    max = Math.max(replacementId, max);
                }
            }
        }

        logger.debug("Maximum group reference found is: {}", max);
        return max;
    }
}
