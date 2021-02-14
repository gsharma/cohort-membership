package com.github.membership.domain;

public final class Member {
    private String memberId;
    private String nodeId;
    private String cohortId;
    private CohortType cohortType;
    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
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

    @Override
    public String toString() {
        return "Member [memberId=" + memberId + ", nodeId=" + nodeId + ", cohortId=" + cohortId + ", cohortType=" + cohortType + ", path=" + path
                + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((cohortId == null) ? 0 : cohortId.hashCode());
        result = prime * result + ((cohortType == null) ? 0 : cohortType.hashCode());
        result = prime * result + ((memberId == null) ? 0 : memberId.hashCode());
        result = prime * result + ((nodeId == null) ? 0 : nodeId.hashCode());
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Member)) {
            return false;
        }
        Member other = (Member) obj;
        if (cohortId == null) {
            if (other.cohortId != null) {
                return false;
            }
        } else if (!cohortId.equals(other.cohortId)) {
            return false;
        }
        if (cohortType != other.cohortType) {
            return false;
        }
        if (memberId == null) {
            if (other.memberId != null) {
                return false;
            }
        } else if (!memberId.equals(other.memberId)) {
            return false;
        }
        if (nodeId == null) {
            if (other.nodeId != null) {
                return false;
            }
        } else if (!nodeId.equals(other.nodeId)) {
            return false;
        }
        if (path == null) {
            if (other.path != null) {
                return false;
            }
        } else if (!path.equals(other.path)) {
            return false;
        }
        return true;
    }

}
