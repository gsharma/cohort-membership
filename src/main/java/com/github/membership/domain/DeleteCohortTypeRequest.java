package com.github.membership.domain;

public final class DeleteCohortTypeRequest {
    private String namespace;
    private CohortType cohortType;

    public boolean validate() {
        // TODO
        return true;
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
        return "DeleteCohortTypeRequest [namespace=" + namespace + ", cohortType=" + cohortType + "]";
    }
}