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

    /**
     * Create a new namespace.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    NewNamespaceResponse newNamespace(final NewNamespaceRequest request) throws MembershipClientException;

    /**
     * Create a new cohort type.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    NewCohortTypeResponse newCohortType(final NewCohortTypeRequest request) throws MembershipClientException;

    /**
     * Create a new cohort.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipClientException;

    /**
     * Create a new Node.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    NewNodeResponse newNode(final NewNodeRequest request) throws MembershipClientException;

    /**
     * List all cohorts in the given namespace.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipClientException;

    /**
     * Join the cohort.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipClientException;

    /**
     * Describe the given cohort.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipClientException;

    /**
     * Leave the cohort.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipClientException;

    /**
     * Delete the cohort and dissociate all its child members.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    DeleteCohortResponse deleteCohort(final DeleteCohortRequest request) throws MembershipClientException;

    /**
     * Delete the given cohort type.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    DeleteCohortTypeResponse deleteCohortType(final DeleteCohortTypeRequest request) throws MembershipClientException;

    /**
     * List all nodes in the given namespace.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    ListNodesResponse listNodes(final ListNodesRequest request) throws MembershipClientException;

    /**
     * Delete the given node.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    DeleteNodeResponse deleteNode(final DeleteNodeRequest request) throws MembershipClientException;

    /**
     * Purge the given namespace.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    PurgeNamespaceResponse purgeNamespace(final PurgeNamespaceRequest request) throws MembershipClientException;

    /**
     * Acquire a leased lock on the entity.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    AcquireLockResponse acquireLock(final AcquireLockRequest request) throws MembershipClientException;

    /**
     * Release a previously-held lock on an entity.
     * 
     * @param request
     * @return
     * @throws MembershipClientException
     */
    ReleaseLockResponse releaseLock(final ReleaseLockRequest request) throws MembershipClientException;

    /**
     * Get a convenient handle to the MembershipClient.
     * 
     * @param serverHost
     * @param serverPort
     * @param serverDeadlineSeconds
     * @param workerCount
     * @return
     */
    static MembershipClient getClient(final String serverHost, final int serverPort, final long serverDeadlineSeconds,
            final int workerCount) {
        return new MembershipClientImpl(serverHost, serverPort, serverDeadlineSeconds, workerCount);
    }

}
