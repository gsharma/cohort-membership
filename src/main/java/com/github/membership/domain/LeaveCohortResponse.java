package com.github.membership.domain;

public final class LeaveCohortResponse {
    private String cohortId;
    private CohortType cohortType;
    private String memberId;
    private boolean success;

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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        return "LeaveCohortResponse [cohortId=" + cohortId + ", cohortType=" + cohortType + ", memberId=" + memberId + ", success=" + success + "]";
    }
}
