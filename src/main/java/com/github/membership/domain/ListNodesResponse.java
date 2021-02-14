package com.github.membership.domain;

import java.util.List;

public final class ListNodesResponse {
    private List<Node> nodes;

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    @Override
    public String toString() {
        return "ListNodesResponse [nodes=" + nodes + "]";
    }
}
