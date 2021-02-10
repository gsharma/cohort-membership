package com.github.membership.domain;

public final class PurgeNamespaceResponse {
    private String namespace;
    private boolean success;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        return "PurgeNamespaceResponse [namespace=" + namespace + ", success=" + success + "]";
    }
}
