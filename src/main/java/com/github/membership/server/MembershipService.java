package com.github.membership.server;

import java.net.InetSocketAddress;
import java.util.List;

import com.github.membership.lib.Lifecycle;

interface MembershipService extends Lifecycle {
    // reload

    NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipServerException;

    ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipServerException;

    JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipServerException;

    DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipServerException;

    LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipServerException;

    static MembershipService getMembership(final List<InetSocketAddress> serverAddresses, final String namespace) {
        return new ZkMembershipService(serverAddresses, namespace);
    }

}
