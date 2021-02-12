package com.github.membership.domain;

import java.net.InetSocketAddress;

public final class NewNodeRequest {
    private String namespace;
    private String nodeId;
    private NodePersona persona;
    private InetSocketAddress address;

    public boolean validate() {
        // TODO
        return true;
    }

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

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public NodePersona getPersona() {
        return persona;
    }

    public void setPersona(NodePersona persona) {
        this.persona = persona;
    }

    @Override
    public String toString() {
        return "NewNodeRequest [namespace=" + namespace + ", nodeId=" + nodeId + ", persona=" + persona + ", address=" + address + "]";
    }
}
