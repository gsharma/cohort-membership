package com.github.membership.server;

public final class NewCohortRequest {
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
        return "NewCohortRequest [cohortId=" + cohortId + ", cohortType=" + cohortType + "]";
    }

}
