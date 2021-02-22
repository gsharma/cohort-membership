package com.github.membership.server;

import com.github.membership.rpc.MembershipUpdate;

/**
 * Accept updates to cohort members of interest.
 */
interface MembershipUpdateCallback {
    void accept(final MembershipUpdate update);
}
