package com.github.membership.server;

import java.util.List;

import com.github.membership.lib.Lifecycle;
import com.github.membership.rpc.Cohort;
import com.github.membership.rpc.CohortType;
import com.github.membership.rpc.Node;
import com.github.membership.rpc.NodePersona;

interface MembershipDelegate extends Lifecycle {
    // reloadable

    boolean newNamespace(final String namespace, final byte[] namespaceMetadata) throws MembershipServerException;

    boolean newCohortType(final String namespace, final CohortType cohortType, final byte[] cohortTypeMetadata) throws MembershipServerException;

    Cohort newCohort(final String namespace, final String cohortId, final CohortType cohortType, final byte[] cohortMetadata)
            throws MembershipServerException;

    Node newNode(final String namespace, final String nodeId, final NodePersona persona, final byte[] nodeMetadata)
            throws MembershipServerException;

    List<Node> listNodes(final String namespace) throws MembershipServerException;

    List<Cohort> listCohorts(final String namespace) throws MembershipServerException;

    Cohort joinCohort(final String namespace, final String memberId, final String cohortId, final CohortType cohortType,
            final String nodeId, final byte[] memberMetadata) throws MembershipServerException;

    Cohort describeCohort(final String namespace, final String cohortId, final CohortType cohortType)
            throws MembershipServerException;

    Cohort updateCohort(final String namespace, final String cohortId, final CohortType cohortType, final byte[] cohortMetadata)
            throws MembershipServerException;

    boolean leaveCohort(final String namespace, final String cohortId, final CohortType cohortType,
            final String memberId) throws MembershipServerException;

    boolean deleteCohort(final String namespace, final String cohortId, final CohortType cohortType)
            throws MembershipServerException;

    boolean deleteCohortType(final String namespace, final CohortType cohortType) throws MembershipServerException;

    boolean deleteNode(final String namespace, final String nodeId) throws MembershipServerException;

    boolean purgeNamespace(final String namespace) throws MembershipServerException;

    boolean acquireLock(final String namespace, final String entity, final long waitSeconds) throws MembershipServerException;

    boolean releaseLock(final String namespace, final String entity) throws MembershipServerException;

    void streamMembershipChanges(final String namespace, final String cohortId, final CohortType cohortType,
            final MembershipUpdateCallback membershipUpdateCallback)
            throws MembershipServerException;

    void streamNodeChanges(final String namespace, final NodeUpdateCallback nodeUpdateCallback) throws MembershipServerException;

    void streamCohortChanges(final String namespace, final String cohortId, final CohortType cohortType,
            final CohortUpdateCallback cohortUpdateCallback)
            throws MembershipServerException;

    enum DelegateMode {
        ZK_DIRECT, CURATOR;
    }

    static MembershipDelegate getDelegate(final MembershipServerConfiguration configuration, final DelegateMode mode) {
        return new ZkMembershipDelegate(configuration, mode);
    }

}
