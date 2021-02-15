package com.github.membership.server;

public final class MembershipDelegateConfiguration {
    private String connectString;

    public String getConnectString() {
        return connectString;
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }

    @Override
    public String toString() {
        return "MembershipDelegateConfiguration [connectString=" + connectString + "]";
    }
}
