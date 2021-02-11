package com.github.membership.domain;

import java.net.InetSocketAddress;

public final class Node {
    private String id;
    private InetSocketAddress address;
    private NodePersona persona;
    private String path;

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public NodePersona getPersona() {
        return persona;
    }

    public void setPersona(NodePersona persona) {
        this.persona = persona;
    }

    @Override
    public String toString() {
        return "Node [id=" + id + ", address=" + address + ", persona=" + persona + ", path=" + path + "]";
    }
}
