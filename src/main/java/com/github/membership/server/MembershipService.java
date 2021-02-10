package com.github.membership.server;

import java.net.InetSocketAddress;
import java.util.List;

import com.github.membership.domain.DeleteCohortRequest;
import com.github.membership.domain.DeleteCohortResponse;
import com.github.membership.domain.DeleteCohortTypeRequest;
import com.github.membership.domain.DeleteCohortTypeResponse;
import com.github.membership.domain.DescribeCohortRequest;
import com.github.membership.domain.DescribeCohortResponse;
import com.github.membership.domain.JoinCohortRequest;
import com.github.membership.domain.JoinCohortResponse;
import com.github.membership.domain.LeaveCohortRequest;
import com.github.membership.domain.LeaveCohortResponse;
import com.github.membership.domain.ListCohortsRequest;
import com.github.membership.domain.ListCohortsResponse;
import com.github.membership.domain.NewCohortRequest;
import com.github.membership.domain.NewCohortResponse;
import com.github.membership.domain.NewCohortTypeRequest;
import com.github.membership.domain.NewCohortTypeResponse;
import com.github.membership.domain.NewNamespaceRequest;
import com.github.membership.domain.NewNamespaceResponse;
import com.github.membership.domain.NewNodeRequest;
import com.github.membership.domain.NewNodeResponse;
import com.github.membership.domain.PurgeNamespaceRequest;
import com.github.membership.domain.PurgeNamespaceResponse;
import com.github.membership.lib.Lifecycle;

interface MembershipService extends Lifecycle {
    // reloadable

    NewNamespaceResponse newNamespace(final NewNamespaceRequest request) throws MembershipServerException;

    NewCohortTypeResponse newCohortType(final NewCohortTypeRequest request) throws MembershipServerException;

    NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipServerException;

    NewNodeResponse newNode(final NewNodeRequest request) throws MembershipServerException;

    ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipServerException;

    JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipServerException;

    DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipServerException;

    LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipServerException;

    DeleteCohortResponse deleteCohort(final DeleteCohortRequest request) throws MembershipServerException;

    DeleteCohortTypeResponse deleteCohortType(final DeleteCohortTypeRequest request) throws MembershipServerException;

    PurgeNamespaceResponse purgeNamespace(final PurgeNamespaceRequest request) throws MembershipServerException;

    static MembershipService getService(final List<InetSocketAddress> serverAddresses) {
        return new ZkMembershipService(serverAddresses);
    }

}
