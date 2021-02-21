package com.github.membership.client;

import com.github.membership.lib.Lifecycle;
import com.github.membership.rpc.AcquireLockRequest;
import com.github.membership.rpc.AcquireLockResponse;
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
import com.github.membership.rpc.ReleaseLockRequest;
import com.github.membership.rpc.ReleaseLockResponse;

/**
 * Client interface for the membership service.
 */
public interface MembershipClient extends Lifecycle {

    NewNamespaceResponse newNamespace(final NewNamespaceRequest request) throws MembershipClientException;

    NewCohortTypeResponse newCohortType(final NewCohortTypeRequest request) throws MembershipClientException;

    NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipClientException;

    NewNodeResponse newNode(final NewNodeRequest request) throws MembershipClientException;

    ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipClientException;

    JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipClientException;

    DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipClientException;

    LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipClientException;

    DeleteCohortResponse deleteCohort(final DeleteCohortRequest request) throws MembershipClientException;

    DeleteCohortTypeResponse deleteCohortType(final DeleteCohortTypeRequest request) throws MembershipClientException;

    ListNodesResponse listNodes(final ListNodesRequest request) throws MembershipClientException;

    DeleteNodeResponse deleteNode(final DeleteNodeRequest request) throws MembershipClientException;

    PurgeNamespaceResponse purgeNamespace(final PurgeNamespaceRequest request) throws MembershipClientException;

    AcquireLockResponse acquireLock(final AcquireLockRequest request) throws MembershipClientException;

    ReleaseLockResponse releaseLock(final ReleaseLockRequest request) throws MembershipClientException;

    static MembershipClient getClient(final String serverHost, final int serverPort, final long serverDeadlineSeconds,
            final int workerCount) {
        return new MembershipClientImpl(serverHost, serverPort, serverDeadlineSeconds, workerCount);
    }

}
