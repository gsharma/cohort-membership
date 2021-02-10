package com.github.membership.server;

public final class DeleteCohortTypeRequest {
    private CohortType cohortType;

    public CohortType getCohortType() {
        return cohortType;
    }

    public void setCohortType(CohortType cohortType) {
        this.cohortType = cohortType;
    }

    @Override
    public String toString() {
        return "DeleteCohortTypeRequest [cohortType=" + cohortType + "]";
    }
}
