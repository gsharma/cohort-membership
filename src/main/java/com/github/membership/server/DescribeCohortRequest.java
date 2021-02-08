package com.github.membership.server;

public final class DescribeCohortRequest {
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
        return "DescribeCohortRequest [cohortId=" + cohortId + ", cohortType=" + cohortType + "]";
    }
}
