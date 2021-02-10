package com.github.membership.domain;

public final class DeleteCohortResponse {
    private String cohortId;
    private CohortType cohortType;
    private boolean success;

    public String getCohortId() {
        return cohortId;
    }

    public void setCohortId(String cohortId) {
        this.cohortId = cohortId;
    }

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
        return "DeleteCohortResponse [cohortId=" + cohortId + ", cohortType=" + cohortType + ", success=" + success + "]";
    }
}
