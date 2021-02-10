package com.github.membership.domain;

public final class JoinCohortRequest {
    private String nodeId;
    private CohortType cohortType;
    private String cohortId;
    private String memberId;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public CohortType getCohortType() {
        return cohortType;
    }

    public void setCohortType(CohortType cohortType) {
        this.cohortType = cohortType;
    }

    public String getCohortId() {
        return cohortId;
    }

    public void setCohortId(String cohortId) {
        this.cohortId = cohortId;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    @Override
    public String toString() {
        return "JoinCohortRequest [nodeId=" + nodeId + ", cohortType=" + cohortType + ", cohortId=" + cohortId + ", memberId=" + memberId + "]";
    }
}
