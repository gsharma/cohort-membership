package com.github.membership.domain;

public final class LeaveCohortRequest {
    private String cohortId;
    private CohortType cohortType;
    private String memberId;

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

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    @Override
    public String toString() {
        return "LeaveCohortRequest [cohortId=" + cohortId + ", cohortType=" + cohortType + ", memberId=" + memberId + "]";
    }
}
