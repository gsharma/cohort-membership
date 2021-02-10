package com.github.membership.domain;

public final class NewNodeResponse {
    private Node node;

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    @Override
    public String toString() {
        return "NewNodeResponse [node=" + node + "]";
    }
}
