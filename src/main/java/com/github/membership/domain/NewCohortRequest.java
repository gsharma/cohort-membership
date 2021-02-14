package com.github.membership.domain;

public final class NewCohortRequest {
    private String namespace;
    private String cohortId;
    private CohortType cohortType;

    public boolean validate() {
        // TODO
        return true;
    }

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
        return "NewCohortRequest [namespace=" + namespace + ", cohortId=" + cohortId + ", cohortType=" + cohortType + "]";
    }

}