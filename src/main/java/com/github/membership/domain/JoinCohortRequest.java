package com.github.membership.domain;

public final class JoinCohortRequest {
    private String namespace;
    private String nodeId;
    private CohortType cohortType;
    private String cohortId;
    private String memberId;

    public boolean validate() {
        return namespace != null && !namespace.trim().isEmpty() && nodeId != null && !nodeId.trim().isEmpty() && cohortType != null
                && cohortId != null && !cohortId.trim().isEmpty() && memberId != null && !memberId.trim().isEmpty();
    }

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

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public String toString() {
        return "JoinCohortRequest [namespace=" + namespace + ", nodeId=" + nodeId + ", cohortType=" + cohortType + ", cohortId=" + cohortId
                + ", memberId=" + memberId + "]";
    }
}
