package com.github.membership.domain;

import java.net.InetSocketAddress;

public final class NewNodeRequest {
    private String nodeId;
    private InetSocketAddress address;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "NewNodeRequest [nodeId=" + nodeId + ", address=" + address + "]";
    }
}
