package com.github.membership.domain;

public final class NewNamespaceResponse {
    private String namespace;
    private String path;
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "NewNamespaceResponse [namespace=" + namespace + ", path=" + path + ", success=" + success + "]";
    }

}
