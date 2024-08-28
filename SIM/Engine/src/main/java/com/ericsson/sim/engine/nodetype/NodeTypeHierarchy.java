package com.ericsson.sim.engine.nodetype;

import java.util.ArrayList;
import java.util.List;

public class NodeTypeHierarchy {
    private NodeTypeHierarchy parent = null;
    private final List<NodeTypeHierarchy> child = new ArrayList<>(10);

    private final String nodeType = null;

    public NodeTypeHierarchy(NodeTypeHierarchy parent) {
        this.parent = parent;
    }

    public boolean isRoot() {
        return parent == null;
    }

    public void add(NodeTypeHierarchy child) {
        this.child.add(child);
    }


}


