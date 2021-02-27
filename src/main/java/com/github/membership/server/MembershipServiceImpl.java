package com.github.membership.server;

import com.github.membership.rpc.AcquireLockRequest;
import com.github.membership.rpc.AcquireLockResponse;
import com.github.membership.rpc.Cohort;
import com.github.membership.rpc.CohortDataUpdateRequest;
import com.github.membership.rpc.CohortDataUpdateResponse;
import com.github.membership.rpc.CohortType;
import com.github.membership.rpc.CohortUpdate;
import com.github.membership.rpc.CohortUpdatesRequest;
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
import com.github.membership.rpc.MembershipServiceGrpc.MembershipServiceImplBase;
import com.github.membership.rpc.MembershipUpdate;
import com.github.membership.rpc.MembershipUpdatesRequest;
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
import com.github.membership.rpc.NodeUpdate;
import com.github.membership.rpc.NodeUpdatesRequest;
import com.github.membership.rpc.PurgeNamespaceRequest;
import com.github.membership.rpc.PurgeNamespaceResponse;
import com.github.membership.rpc.ReleaseLockRequest;
import com.github.membership.rpc.ReleaseLockResponse;
import com.google.protobuf.ByteString;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.Status.Code;
import io.grpc.stub.StreamObserver;

/**
 * A grpc-based implementation for Membership Service. Note that this is not to be used directly, users should rely on the MembershipClient instead.
 */
final class MembershipServiceImpl extends MembershipServiceImplBase {
    private static final Logger logger = LogManager.getLogger(MembershipServiceImpl.class.getSimpleName());

    private MembershipDelegate membershipDelegate;

    MembershipServiceImpl() {
    }

    void setDelegate(final MembershipDelegate membershipDelegate) {
        this.membershipDelegate = membershipDelegate;
    }

    @Override
    public void newNamespace(final NewNamespaceRequest request,
            final StreamObserver<NewNamespaceResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final boolean success = membershipDelegate.newNamespace(namespace, null);
            final NewNamespaceResponse response = NewNamespaceResponse.newBuilder().setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void newCohortType(final NewCohortTypeRequest request,
            final StreamObserver<NewCohortTypeResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final CohortType cohortType = request.getCohortType();
            final boolean success = membershipDelegate.newCohortType(namespace, cohortType, null);
            final NewCohortTypeResponse response = NewCohortTypeResponse.newBuilder().setSuccess(success).build();
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
            final Cohort cohort = membershipDelegate.newCohort(namespace, cohortId, cohortType, null);
            final NewCohortResponse response = NewCohortResponse.newBuilder().setCohort(cohort).build();
            logger.debug(response);
            final MembershipUpdateCallback membershipUpdateCallback = new MembershipUpdateCallback() {
                @Override
                public void accept(final MembershipUpdate update) {
                    logger.info("Membership update event, type:{}, update:[{}]", update.getUpdateType(), update);
                }
            };
            membershipDelegate.streamMembershipChanges(namespace, cohortId, cohortType, membershipUpdateCallback);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void membershipUpdates(final MembershipUpdatesRequest request, final StreamObserver<MembershipUpdate> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final MembershipUpdateCallback membershipUpdateCallback = new MembershipUpdateCallback() {
                @Override
                public void accept(final MembershipUpdate update) {
                    logger.info("Membership update event, type:{}, update:[{}]", update.getUpdateType(), update);
                    responseObserver.onNext(update);
                }
            };
            membershipDelegate.streamMembershipChanges(namespace, cohortId, cohortType, membershipUpdateCallback);
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
            // final String address = request.getAddress();
            final NodePersona persona = request.getPersona();
            final Node node = membershipDelegate.newNode(namespace, nodeId, persona, null);
            final NewNodeResponse response = NewNodeResponse.newBuilder().setNode(node).build();
            logger.debug(response);
            final NodeUpdateCallback nodeUpdateCallback = new NodeUpdateCallback() {
                @Override
                public void accept(final NodeUpdate update) {
                    logger.info("Node update event, type:{} update:[{}]", update.getUpdateType(), update);
                }
            };
            membershipDelegate.streamNodeChanges(namespace, nodeUpdateCallback);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void nodeUpdates(final NodeUpdatesRequest request, final StreamObserver<NodeUpdate> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final NodeUpdateCallback nodeUpdateCallback = new NodeUpdateCallback() {
                @Override
                public void accept(final NodeUpdate update) {
                    logger.info("Node update event, type:{} update:[{}]", update.getUpdateType(), update);
                    responseObserver.onNext(update);
                }
            };
            membershipDelegate.streamNodeChanges(namespace, nodeUpdateCallback);
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
            final ListNodesResponse response = ListNodesResponse.newBuilder().addAllNodes(nodes).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void listCohorts(final ListCohortsRequest request,
            final StreamObserver<ListCohortsResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final List<Cohort> cohorts = membershipDelegate.listCohorts(namespace);
            final ListCohortsResponse response = ListCohortsResponse.newBuilder().addAllCohorts(cohorts).build();
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
            final Cohort cohort = membershipDelegate.joinCohort(namespace, memberId, cohortId, cohortType, nodeId, null);
            final JoinCohortResponse response = JoinCohortResponse.newBuilder().setCohort(cohort).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void describeCohort(final DescribeCohortRequest request,
            final StreamObserver<DescribeCohortResponse> responseObserver) {
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
    public void leaveCohort(final LeaveCohortRequest request,
            final StreamObserver<LeaveCohortResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final String memberId = request.getMemberId();
            final boolean success = membershipDelegate.leaveCohort(namespace, cohortId, cohortType, memberId);
            final LeaveCohortResponse response = LeaveCohortResponse.newBuilder().setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void deleteCohort(final DeleteCohortRequest request,
            final StreamObserver<DeleteCohortResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final boolean success = membershipDelegate.deleteCohort(namespace, cohortId, cohortType);
            final DeleteCohortResponse response = DeleteCohortResponse.newBuilder().setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void deleteCohortType(final DeleteCohortTypeRequest request,
            final StreamObserver<DeleteCohortTypeResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final CohortType cohortType = request.getCohortType();
            final boolean success = membershipDelegate.deleteCohortType(namespace, cohortType);
            final DeleteCohortTypeResponse response = DeleteCohortTypeResponse.newBuilder().setSuccess(success).build();
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
            final DeleteNodeResponse response = DeleteNodeResponse.newBuilder().setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void purgeNamespace(final PurgeNamespaceRequest request,
            final StreamObserver<PurgeNamespaceResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final boolean success = membershipDelegate.purgeNamespace(namespace);
            final PurgeNamespaceResponse response = PurgeNamespaceResponse.newBuilder().setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void acquireLock(final AcquireLockRequest request,
            final StreamObserver<AcquireLockResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String lockEntity = request.getLockEntity();
            final long waitSeconds = request.getWaitSeconds();
            // logger.debug("Acquire lock, namespace:{}, entity:{}, waitSeconds:{}", namespace, lockEntity, waitSeconds);
            final boolean success = membershipDelegate.acquireLock(namespace, lockEntity, waitSeconds);
            final AcquireLockResponse response = AcquireLockResponse.newBuilder().setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void releaseLock(final ReleaseLockRequest request,
            final StreamObserver<ReleaseLockResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String lockEntity = request.getLockEntity();
            final boolean success = membershipDelegate.releaseLock(namespace, lockEntity);
            final ReleaseLockResponse response = ReleaseLockResponse.newBuilder().setSuccess(success).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void cohortUpdates(final CohortUpdatesRequest request,
            final StreamObserver<CohortUpdate> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final CohortUpdateCallback cohortUpdateCallback = new CohortUpdateCallback() {
                @Override
                public void accept(final CohortUpdate update) {
                    logger.info("Cohort update event, type:{} update:[{}]", update.getUpdateType(), update);
                    responseObserver.onNext(update);
                }
            };
            membershipDelegate.streamCohortChanges(namespace, cohortId, cohortType, cohortUpdateCallback);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    @Override
    public void updateCohort(final CohortDataUpdateRequest request,
            final StreamObserver<CohortDataUpdateResponse> responseObserver) {
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final ByteString payloadByteString = request.getPayload();
            byte[] payload = null;
            if (payloadByteString != null) {
                payload = payloadByteString.toByteArray();
            }
            final Cohort cohort = membershipDelegate.updateCohort(namespace, cohortId, cohortType, payload);
            final CohortDataUpdateResponse response = CohortDataUpdateResponse.newBuilder().setCohort(cohort).build();
            logger.debug(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (MembershipServerException membershipProblem) {
            responseObserver.onError(toStatusRuntimeException(membershipProblem));
        }
    }

    private static StatusRuntimeException toStatusRuntimeException(final MembershipServerException serverException) {
        return new StatusRuntimeException(Status.fromCode(Code.INTERNAL).withCause(serverException)
                .withDescription(serverException.getMessage()));
    }

}
