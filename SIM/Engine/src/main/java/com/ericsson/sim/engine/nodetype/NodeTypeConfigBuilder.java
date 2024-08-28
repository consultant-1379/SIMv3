package com.ericsson.sim.engine.nodetype;

import com.ericsson.sim.common.Constants;
import com.ericsson.sim.engine.nodetype.model.NodeType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class NodeTypeConfigBuilder {
    private static final Logger logger = LogManager.getLogger(NodeTypeConfigBuilder.class);

    public static Map<String, NodeType> mergeHierarchy(Map<String, NodeType> nodeTypes) {
        Map<String, NodeType> mergedTypes = new HashMap<>();
        logger.debug("Merging all node type hierarchies");

        for (final String key : nodeTypes.keySet()) {
            NodeType nodeType = nodeTypes.get(key);
            logger.debug("Starting merge for {}", nodeType.getName());
            try {
                if (key.equals(nodeType.getBaseType())) {
                    //same as base, no need to merge with itself
                    logger.debug("Base type is set same as node type. Skipping");
                    continue;
                }
                if (Constants.PRIMARY_TYPE.equals(nodeType.getName())) {
                    logger.debug("{} is primary type, skipping as its not expected to be derived by any other type", nodeType.getName());
                    continue;
                }
                if (hasCircularDependency(nodeTypes, nodeType)) {
                    logger.error("Not going to merge node type {} as it has circular dependencies", nodeType.getName());
                    continue;
                }
                if (!isDerivedFromPrimary(nodeTypes, nodeType)) {
                    logger.error("Node type {} or it base type(s) do no drive from primary type {}. Loaded configuration for node types {} may be incomplete",
                            nodeType.getName(), Constants.PRIMARY_TYPE, nodeType.getName());
                }
                nodeType = mergeHierarchy(nodeTypes, nodeType);
            } finally {
                mergedTypes.put(key, nodeType);
            }
        }

        return mergedTypes;
    }

    public static NodeType mergeHierarchy(final Map<String, NodeType> nodeTypes, final NodeType nodeType) {
        if (nodeType == null) {
            logger.debug("Passed node type is NULL");
            return null;
        } else if (nodeType.isMerged()) {
            logger.debug("{} is already merged", nodeType.getName());
            return nodeType;
        }
        logger.debug("Recursively merging hierarchy for {}", nodeType.getName());
        NodeType base = mergeHierarchy(nodeTypes, nodeTypes.get(nodeType.getBaseType()));
        if (base == null) {
            return nodeType;
        } else {
            logger.debug("Merging {} into {}", nodeType.getName(), base.getName());
            for (final String name : base.getProperties().keySet()) {
                if (!nodeType.getProperties().containsKey(name)) {
                    logger.debug("Adding property {} from {} to {}", name, base.getName(), nodeType.getName());
                    nodeType.getProperties().put(name, base.getProperties().get(name));
                }
            }
            logger.debug("Node type {} merged", nodeType.getName());
            if (logger.isTraceEnabled()) {
                logger.trace("\n{}", nodeType.toString());
            }
            nodeType.setMerged(true);
        }

        return nodeType;
    }

    private static boolean hasCircularDependency(final Map<String, NodeType> nodeTypes, final NodeType nodeType) {
//        NodeType current = nodeType;
        if (nodeType == null) {
            logger.debug("Node type passed is NULL");
            return false;
        } else if (nodeType.getBaseType() == null || "".equals(nodeType.getBaseType())) {
            logger.debug("Node type {} has no base type", nodeType.getName());
            return false;
        }
        logger.debug("Checking if {} has circular dependency", nodeType.getName());

        NodeType current = nodeType;
        while (current.getBaseType() != null && !"".equals(current.getBaseType())) {
            final String baseNode = current.getBaseType();
            logger.trace("Base type for {} is {}", current.getName(), baseNode);

            current = nodeTypes.get(baseNode);
            if (current == null) {
                logger.debug("Unable to get base type {} from known types, but its concluded that there is not circular dependency.", baseNode);
                return false;
            }
            logger.trace("Checking {} with {}", nodeType.getName(), current.getName());
            if (current.getName().equals(current.getBaseType())) {
                logger.warn("Node type {} has circular dependency with itself in base type", current.getName());
                return true;
            } else if (nodeType.getName().equals(baseNode)) {
                logger.warn("Node type {} has circular dependency in base type {}", nodeType.getName(), current.getName());
                return true;
            }
        }
        return false;
    }

    private static boolean isDerivedFromPrimary(final Map<String, NodeType> nodeTypes, NodeType nodeType) {
        while (nodeType != null) {
            logger.debug("Checking if {} is derived from primary", nodeType.getName());
            if (nodeType.getBaseType() == null) {
                return Constants.PRIMARY_TYPE.equals(nodeType.getName());
            }
            nodeType = nodeTypes.get(nodeType.getBaseType());
        }
        return false;
    }
}
