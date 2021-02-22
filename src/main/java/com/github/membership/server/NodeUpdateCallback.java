package com.github.membership.server;

import com.github.membership.rpc.NodeUpdate;

/**
 * Accept updates to nodes of interest.
 */
interface NodeUpdateCallback {
    void accept(final NodeUpdate update);
}
