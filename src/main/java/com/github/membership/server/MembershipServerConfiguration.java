package com.github.membership.server;

public final class MembershipServerConfiguration {
    private String serverHost;
    private int serverPort;
    private int workerCount;

    private String connectString;
    private int clientSessionTimeoutMillis;
    private long clientSessionEstablishmentTimeoutSeconds;

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
        return "MembershipServerConfiguration [connectString=" + connectString + ", serverHost=" + serverHost
                + ", serverPort=" + serverPort + ", workerCount=" + workerCount + ", clientSessionTimeoutMillis="
                + clientSessionTimeoutMillis + ", clientSessionEstablishmentTimeoutSeconds="
                + clientSessionEstablishmentTimeoutSeconds + "]";
    }

}
