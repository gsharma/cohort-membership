package com.github.membership.domain;

import java.util.List;

public final class DescribeCohortResponse {
    private String cohortId;
    private CohortType cohortType;
    private List<Member> members;

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

    public List<Member> getMembers() {
        return members;
    }

    public void setMembers(List<Member> members) {
        this.members = members;
    }

    @Override
    public String toString() {
        return "DescribeCohortResponse [cohortId=" + cohortId + ", cohortType=" + cohortType + ", members=" + members + "]";
    }
}
