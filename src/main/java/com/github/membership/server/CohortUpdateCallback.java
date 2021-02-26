package com.github.membership.server;

import com.github.membership.rpc.CohortUpdate;

/**
 * Accept updates to cohort of interest.
 */
interface CohortUpdateCallback {
    void accept(final CohortUpdate update);
}
