package com.github.membership.domain;

public final class NewCohortTypeResponse {
    private CohortType cohortType;
    private String path;
    private boolean success;

    public CohortType getCohortType() {
        return cohortType;
    }

    public void setCohortType(CohortType cohortType) {
        this.cohortType = cohortType;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        return "NewCohortTypeResponse [cohortType=" + cohortType + ", path=" + path + ", success=" + success + "]";
    }

}
