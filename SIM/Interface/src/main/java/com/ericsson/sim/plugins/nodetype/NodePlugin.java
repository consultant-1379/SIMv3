package com.ericsson.sim.plugins.nodetype;

import com.ericsson.sim.plugins.Plugin;

import java.util.Map;

public interface NodePlugin<String, V> extends Plugin {
    String getNodeType();

    Map<String, V> getConfig();
}
