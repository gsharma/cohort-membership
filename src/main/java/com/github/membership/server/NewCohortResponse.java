package com.github.membership.server;

public final class NewCohortResponse {
    private Cohort cohort;

    public Cohort getCohort() {
        return cohort;
    }

    public void setCohort(final Cohort cohort) {
        this.cohort = cohort;
    }

    @Override
    public String toString() {
        return "NewCohortResponse [cohort=" + cohort + "]";
    }

}
