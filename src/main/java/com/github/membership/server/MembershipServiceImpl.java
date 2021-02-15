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
import com.github.membership.rpc.NodePersona;
import com.github.membership.rpc.PurgeNamespaceRequest;
import com.github.membership.rpc.PurgeNamespaceResponse;
import com.github.membership.rpc.MembershipServiceGrpc.MembershipServiceImplBase;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.Status.Code;
import io.grpc.stub.StreamObserver;

public final class MembershipServiceImpl extends MembershipServiceImplBase {
    private static final Logger logger = LogManager.getLogger(MembershipServiceImpl.class.getSimpleName());

    private MembershipDelegate membershipDelegate;

    public MembershipServiceImpl(final MembershipDelegate membershipDelegate) {
        this.membershipDelegate = membershipDelegate;
    }

    @Override
    public void newNamespace(final NewNamespaceRequest request, final StreamObserver<NewNamespaceResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final boolean success = membershipDelegate.newNamespace(namespace);
            final NewNamespaceResponse response = NewNamespaceResponse.newBuilder()
                    .setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void newCohortType(final NewCohortTypeRequest request, final StreamObserver<NewCohortTypeResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final CohortType cohortType = request.getCohortType();
            final boolean success = membershipDelegate.newCohortType(namespace, cohortType);
            final NewCohortTypeResponse response = NewCohortTypeResponse.newBuilder()
                    .setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void newCohort(final NewCohortRequest request, final StreamObserver<NewCohortResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final Cohort cohort = membershipDelegate.newCohort(namespace, cohortId, cohortType);
            final NewCohortResponse response = NewCohortResponse.newBuilder()
                    .setCohort(cohort).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void newNode(final NewNodeRequest request, final StreamObserver<NewNodeResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String nodeId = request.getNodeId();
            final String address = request.getAddress();
            final NodePersona persona = request.getPersona();
            final Node node = membershipDelegate.newNode(namespace, nodeId, persona, address);
            final NewNodeResponse response = NewNodeResponse.newBuilder()
                    .setNode(node).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void listNodes(final ListNodesRequest request, final StreamObserver<ListNodesResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final List<Node> nodes = membershipDelegate.listNodes(namespace);
            final ListNodesResponse response = ListNodesResponse.newBuilder()
                    .addAllNodes(nodes).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void listCohorts(final ListCohortsRequest request, final StreamObserver<ListCohortsResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final List<Cohort> cohorts = membershipDelegate.listCohorts(namespace);
            final ListCohortsResponse response = ListCohortsResponse.newBuilder()
                    .addAllCohorts(cohorts).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void joinCohort(final JoinCohortRequest request, final StreamObserver<JoinCohortResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final String memberId = request.getMemberId();
            final String nodeId = request.getNodeId();
            final Cohort cohort = membershipDelegate.joinCohort(namespace, memberId, cohortId, cohortType, nodeId);
            final JoinCohortResponse response = JoinCohortResponse.newBuilder()
                    .setCohort(cohort).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void describeCohort(final DescribeCohortRequest request, final StreamObserver<DescribeCohortResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final Cohort cohort = membershipDelegate.describeCohort(namespace, cohortId, cohortType);
            final DescribeCohortResponse response = DescribeCohortResponse.newBuilder().setCohort(cohort).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void leaveCohort(final LeaveCohortRequest request, final StreamObserver<LeaveCohortResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final String memberId = request.getMemberId();
            final boolean success = membershipDelegate.leaveCohort(namespace, cohortId, cohortType, memberId);
            final LeaveCohortResponse response = LeaveCohortResponse.newBuilder()
                    .setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void deleteCohort(final DeleteCohortRequest request, final StreamObserver<DeleteCohortResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final boolean success = membershipDelegate.deleteCohort(namespace, cohortId, cohortType);
            final DeleteCohortResponse response = DeleteCohortResponse.newBuilder()
                    .setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void deleteCohortType(final DeleteCohortTypeRequest request, final StreamObserver<DeleteCohortTypeResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final CohortType cohortType = request.getCohortType();
            final boolean success = membershipDelegate.deleteCohortType(namespace, cohortType);
            final DeleteCohortTypeResponse response = DeleteCohortTypeResponse.newBuilder()
                    .setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void deleteNode(final DeleteNodeRequest request, final StreamObserver<DeleteNodeResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String nodeId = request.getNodeId();
            final boolean success = membershipDelegate.deleteNode(namespace, nodeId);
            final DeleteNodeResponse response = DeleteNodeResponse.newBuilder()
                    .setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void purgeNamespace(final PurgeNamespaceRequest request, final StreamObserver<PurgeNamespaceResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final boolean success = membershipDelegate.purgeNamespace(namespace);
            final PurgeNamespaceResponse response = PurgeNamespaceResponse.newBuilder()
                    .setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    private static StatusRuntimeException toStatusRuntimeException(final MembershipServerException serverException) {
        return new StatusRuntimeException(Status.fromCode(Code.INTERNAL).withCause(serverException).withDescription(serverException.getMessage()));
    }
}
