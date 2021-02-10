package com.github.membership.server;

public final class DeleteCohortTypeResponse {
    private CohortType cohortType;
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

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        return "DeleteCohortTypeResponse [cohortType=" + cohortType + ", success=" + success + "]";
    }
}
