package com.github.membership.domain;

public final class DescribeCohortResponse {
    private Cohort cohort;

    public Cohort getCohort() {
        return cohort;
    }

    public void setCohort(Cohort cohort) {
        this.cohort = cohort;
    }

    @Override
    public String toString() {
        return "DescribeCohortResponse [cohort=" + cohort + "]";
    }
}
