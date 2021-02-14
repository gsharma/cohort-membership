package com.github.membership.server;

public final class MembershipServiceConfiguration {
    private String connectString;

    public String getConnectString() {
        return connectString;
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }

    @Override
    public String toString() {
        return "MembershipServiceConfiguration [connectString=" + connectString + "]";
    }
}
