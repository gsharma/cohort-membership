package com.github.membership.domain;

public final class DeleteNodeRequest {
    private String namespace;
    private String nodeId;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public String toString() {
        return "DeleteNodeRequest [namespace=" + namespace + ", nodeId=" + nodeId + "]";
    }
}
