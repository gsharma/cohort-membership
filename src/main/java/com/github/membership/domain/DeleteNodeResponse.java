package com.github.membership.domain;

public final class DeleteNodeResponse {
    private String namespace;
    private String nodeId;
    private boolean success;

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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        return "DeleteNodeResponse [namespace=" + namespace + ", nodeId=" + nodeId + ", success=" + success + "]";
    }

}
