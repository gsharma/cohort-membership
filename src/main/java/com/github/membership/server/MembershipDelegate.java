package com.github.membership.server;

import com.github.membership.lib.Lifecycle;
import com.github.membership.rpc.DeleteCohortRequest;
import com.github.membership.rpc.DeleteCohortResponse;
import com.github.membership.rpc.DeleteCohortTypeRequest;
import com.github.membership.rpc.DeleteCohortTypeResponse;
import com.github.membership.rpc.DeleteNodeRequest;
import com.github.membership.rpc.DeleteNodeResponse;
import com.github.membership.rpc.DescribeCohortRequest;
import com.github.membership.rpc.DescribeCohortResponse;
import com.github.membership.rpc.JoinCohortRequest;
import com.github.membership.rpc.JoinCohortResponse;
import com.github.membership.rpc.LeaveCohortRequest;
import com.github.membership.rpc.LeaveCohortResponse;
import com.github.membership.rpc.ListCohortsRequest;
import com.github.membership.rpc.ListCohortsResponse;
import com.github.membership.rpc.ListNodesRequest;
import com.github.membership.rpc.ListNodesResponse;
import com.github.membership.rpc.NewCohortRequest;
import com.github.membership.rpc.NewCohortResponse;
import com.github.membership.rpc.NewCohortTypeRequest;
import com.github.membership.rpc.NewCohortTypeResponse;
import com.github.membership.rpc.NewNamespaceRequest;
import com.github.membership.rpc.NewNamespaceResponse;
import com.github.membership.rpc.NewNodeRequest;
import com.github.membership.rpc.NewNodeResponse;
import com.github.membership.rpc.PurgeNamespaceRequest;
import com.github.membership.rpc.PurgeNamespaceResponse;

interface MembershipDelegate extends Lifecycle {
    // reloadable

    NewNamespaceResponse newNamespace(final NewNamespaceRequest request) throws MembershipServerException;

    NewCohortTypeResponse newCohortType(final NewCohortTypeRequest request) throws MembershipServerException;

    NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipServerException;

    NewNodeResponse newNode(final NewNodeRequest request) throws MembershipServerException;

    ListNodesResponse listNodes(final ListNodesRequest request) throws MembershipServerException;

    ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipServerException;

    JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipServerException;

    DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipServerException;

    LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipServerException;

    DeleteCohortResponse deleteCohort(final DeleteCohortRequest request) throws MembershipServerException;

    DeleteCohortTypeResponse deleteCohortType(final DeleteCohortTypeRequest request) throws MembershipServerException;

    DeleteNodeResponse deleteNode(final DeleteNodeRequest request) throws MembershipServerException;

    PurgeNamespaceResponse purgeNamespace(final PurgeNamespaceRequest request) throws MembershipServerException;

    // AcquireLockResponse acquireLock(final AcquireLockRequest request) throws MembershipServerException;

    // ReleaseLockResponse releaseLock(final ReleaseLockRequest request) throws MembershipServerException;

    static MembershipDelegate getDelegate(final MembershipDelegateConfiguration configuration) {
        return new ZkMembershipDelegate(configuration);
    }

}
