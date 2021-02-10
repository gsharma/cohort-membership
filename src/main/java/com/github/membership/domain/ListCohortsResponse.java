package com.github.membership.domain;

import java.util.List;

public final class ListCohortsResponse {
    private List<Cohort> cohorts;

    public List<Cohort> getCohorts() {
        return cohorts;
    }

    public void setCohorts(List<Cohort> cohorts) {
        this.cohorts = cohorts;
    }

    @Override
    public String toString() {
        return "ListCohortsResponse [cohorts=" + cohorts + "]";
    }
}
