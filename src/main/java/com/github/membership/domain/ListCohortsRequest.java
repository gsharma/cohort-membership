package com.github.membership.domain;

public final class ListCohortsRequest {
    private String namespace;

    public boolean validate() {
        // TODO
        return true;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public String toString() {
        return "ListCohortsRequest [namespace=" + namespace + "]";
    }

}