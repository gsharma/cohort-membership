package com.github.membership.server;

public final class Cohort {
    private String id;
    private CohortType type;
    private String path;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CohortType getType() {
        return type;
    }

    public void setType(CohortType type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "Cohort [id=" + id + ", type=" + type + ", path=" + path + "]";
    }

}
