package com.github.membership.domain;

public final class NewCohortTypeRequest {
    private CohortType cohortType;

    public CohortType getCohortType() {
        return cohortType;
    }

    public void setCohortType(CohortType cohortType) {
        this.cohortType = cohortType;
    }

    @Override
    public String toString() {
        return "NewCohortTypeRequest [cohortType=" + cohortType + "]";
    }
}
