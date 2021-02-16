package com.github.membership.server;

public final class MembershipDelegateConfiguration {
    private String connectString;
    private int clientSessionTimeoutMillis;

    public String getConnectString() {
        return connectString;
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }

    public int getClientSessionTimeoutMillis() {
        return clientSessionTimeoutMillis;
    }

    public void setClientSessionTimeoutMillis(int clientSessionTimeoutMillis) {
        this.clientSessionTimeoutMillis = clientSessionTimeoutMillis;
    }

    @Override
    public String toString() {
        return "MembershipDelegateConfiguration [connectString=" + connectString + ", clientSessionTimeoutMillis=" + clientSessionTimeoutMillis + "]";
    }
}
