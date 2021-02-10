package com.github.membership.domain;

public final class DescribeCohortRequest {
    private String namespace;
    private String cohortId;
    private CohortType cohortType;

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

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public String toString() {
        return "DescribeCohortRequest [namespace=" + namespace + ", cohortId=" + cohortId + ", cohortType=" + cohortType + "]";
    }
}
