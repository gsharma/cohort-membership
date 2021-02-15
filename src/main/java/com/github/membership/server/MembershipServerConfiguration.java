package com.github.membership.server;

public final class MembershipServerConfiguration {
    private String connectString;
    private String serverHost;
    private int serverPort;
    private int workerCount;

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

    @Override
    public String toString() {
        return "MembershipServerConfiguration [connectString=" + connectString + ", serverHost=" + serverHost + ", serverPort=" + serverPort
                + ", workerCount=" + workerCount + "]";
    }
}
