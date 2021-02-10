package com.github.membership.domain;

public final class DeleteCohortRequest {
    private String cohortId;
    private CohortType cohortType;

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

    @Override
    public String toString() {
        return "DeleteCohortRequest [cohortId=" + cohortId + ", cohortType=" + cohortType + "]";
    }
}
