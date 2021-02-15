package com.github.membership.server;

import com.github.membership.rpc.Cohort;
import com.github.membership.rpc.CohortType;
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
import com.github.membership.rpc.Member;
import com.github.membership.rpc.NewCohortRequest;
import com.github.membership.rpc.NewCohortResponse;
import com.github.membership.rpc.NewCohortTypeRequest;
import com.github.membership.rpc.NewCohortTypeResponse;
import com.github.membership.rpc.NewNamespaceRequest;
import com.github.membership.rpc.NewNamespaceResponse;
import com.github.membership.rpc.NewNodeRequest;
import com.github.membership.rpc.NewNodeResponse;
import com.github.membership.rpc.Node;
import com.github.membership.rpc.PurgeNamespaceRequest;
import com.github.membership.rpc.PurgeNamespaceResponse;
import com.github.membership.rpc.MembershipServiceGrpc.MembershipServiceImplBase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.grpc.stub.StreamObserver;

public final class MembershipServiceImpl extends MembershipServiceImplBase {
    private static final Logger logger = LogManager.getLogger(MembershipServiceImpl.class.getSimpleName());

    private MembershipDelegate membershipDelegate;

    public MembershipServiceImpl(final MembershipDelegate membershipDelegate) {
        this.membershipDelegate = membershipDelegate;
    }

    /**
     */
    @Override
    public void newNamespace(final NewNamespaceRequest request, final StreamObserver<NewNamespaceResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void newCohortType(final NewCohortTypeRequest request, final StreamObserver<NewCohortTypeResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void newCohort(final NewCohortRequest request, final StreamObserver<NewCohortResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void newNode(final NewNodeRequest request, final StreamObserver<NewNodeResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void listNodes(final ListNodesRequest request, final StreamObserver<ListNodesResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void listCohorts(final ListCohortsRequest request, final StreamObserver<ListCohortsResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void joinCohort(final JoinCohortRequest request, final StreamObserver<JoinCohortResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void describeCohort(final DescribeCohortRequest request, final StreamObserver<DescribeCohortResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void leaveCohort(final LeaveCohortRequest request, final StreamObserver<LeaveCohortResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void deleteCohort(final DeleteCohortRequest request, final StreamObserver<DeleteCohortResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void deleteCohortType(final DeleteCohortTypeRequest request, final StreamObserver<DeleteCohortTypeResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void deleteNode(final DeleteNodeRequest request, final StreamObserver<DeleteNodeResponse> responseObserver) {
        // TODO
    }

    /**
     */
    @Override
    public void purgeNamespace(final PurgeNamespaceRequest request, final StreamObserver<PurgeNamespaceResponse> responseObserver) {
        // TODO
    }

}
