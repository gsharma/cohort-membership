package com.github.membership.server;

import java.util.List;

import com.github.membership.lib.Lifecycle;
import com.github.membership.rpc.Cohort;
import com.github.membership.rpc.CohortType;
import com.github.membership.rpc.Node;
import com.github.membership.rpc.NodePersona;

public interface MembershipDelegate extends Lifecycle {
    // reloadable

    boolean newNamespace(final String namespace) throws MembershipServerException;

    boolean newCohortType(final String namespace, final CohortType cohortType) throws MembershipServerException;

    Cohort newCohort(final String namespace, final String cohortId, final CohortType cohortType)
            throws MembershipServerException;

    Node newNode(final String namespace, final String nodeId, final NodePersona persona, final String address)
            throws MembershipServerException;

    List<Node> listNodes(final String namespace) throws MembershipServerException;

    List<Cohort> listCohorts(final String namespace) throws MembershipServerException;

    Cohort joinCohort(final String namespace, final String memberId, final String cohortId, final CohortType cohortType,
            final String nodeId) throws MembershipServerException;

    Cohort describeCohort(final String namespace, final String cohortId, final CohortType cohortType)
            throws MembershipServerException;

    boolean leaveCohort(final String namespace, final String cohortId, final CohortType cohortType,
            final String memberId) throws MembershipServerException;

    boolean deleteCohort(final String namespace, final String cohortId, final CohortType cohortType)
            throws MembershipServerException;

    boolean deleteCohortType(final String namespace, final CohortType cohortType) throws MembershipServerException;

    boolean deleteNode(final String namespace, final String nodeId) throws MembershipServerException;

    boolean purgeNamespace(final String namespace) throws MembershipServerException;

    // AcquireLockResponse acquireLock(final AcquireLockRequest request) throws
    // MembershipServerException;

    // ReleaseLockResponse releaseLock(final ReleaseLockRequest request) throws
    // MembershipServerException;

    static MembershipDelegate getDelegate(final MembershipServerConfiguration configuration) {
        return new ZkMembershipDelegate(configuration);
    }

}
