package com.github.membership.server;

// TODO: modularize configurations
public final class MembershipServerConfiguration {
    private String serverHost;
    private int serverPort;
    private int workerCount;

    private String connectString;
    private int clientConnectionTimeoutMillis;
    private int clientSessionTimeoutMillis;
    private long clientSessionEstablishmentTimeoutSeconds;

    private String serverUser;
    private String serverPassword;

    public String getServerUser() {
        return serverUser;
    }

    public void setServerUser(String serverUser) {
        this.serverUser = serverUser;
    }

    public String getServerPassword() {
        return serverPassword;
    }

    public void setServerPassword(String serverPassword) {
        this.serverPassword = serverPassword;
    }

    public int getClientConnectionTimeoutMillis() {
        return clientConnectionTimeoutMillis;
    }

    public void setClientConnectionTimeoutMillis(int clientConnectionTimeoutMillis) {
        this.clientConnectionTimeoutMillis = clientConnectionTimeoutMillis;
    }

    public String getConnectString() {
        return connectString;
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public int getClientSessionTimeoutMillis() {
        return clientSessionTimeoutMillis;
    }

    public void setClientSessionTimeoutMillis(int clientSessionTimeoutMillis) {
        this.clientSessionTimeoutMillis = clientSessionTimeoutMillis;
    }

    public long getClientSessionEstablishmentTimeoutSeconds() {
        return clientSessionEstablishmentTimeoutSeconds;
    }

    public void setClientSessionEstablishmentTimeoutSeconds(long clientSessionEstablishmentTimeoutSeconds) {
        this.clientSessionEstablishmentTimeoutSeconds = clientSessionEstablishmentTimeoutSeconds;
    }

    @Override
    public String toString() {
        return "MembershipServerConfiguration [serverHost=" + serverHost + ", serverPort=" + serverPort + ", workerCount=" + workerCount
                + ", connectString=" + connectString + ", clientConnectionTimeoutMillis=" + clientConnectionTimeoutMillis + ", serverUser="
                + serverUser + ", serverPassword=" + serverPassword + ", clientSessionTimeoutMillis=" + clientSessionTimeoutMillis
                + ", clientSessionEstablishmentTimeoutSeconds=" + clientSessionEstablishmentTimeoutSeconds + "]";
    }

}
