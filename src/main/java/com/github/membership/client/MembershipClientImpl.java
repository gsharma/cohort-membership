package com.github.membership.client;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.membership.client.MembershipClientException.Code;

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
import com.github.membership.rpc.MembershipServiceGrpc;
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

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

public final class MembershipClientImpl implements MembershipClient {
    private static final Logger logger = LogManager.getLogger(MembershipClientImpl.class.getSimpleName());

    private final String identity = UUID.randomUUID().toString();

    private final AtomicBoolean running;
    private final AtomicBoolean ready;

    private final String serverHost;
    private final int serverPort;
    private final long serverDeadlineSeconds;
    private final int workerCount;

    private ManagedChannel channel;
    private MembershipServiceGrpc.MembershipServiceBlockingStub serviceStub;
    private ThreadPoolExecutor clientExecutor;

    MembershipClientImpl(final String serverHost, final int serverPort, final long serverDeadlineSeconds, final int workerCount) {
        this.running = new AtomicBoolean(false);
        this.ready = new AtomicBoolean(false);
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.serverDeadlineSeconds = serverDeadlineSeconds;
        this.workerCount = workerCount;
    }

    @Override
    public void start() throws Exception {
        if (running.compareAndSet(false, true)) {
            final long startNanos = System.nanoTime();
            clientExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(workerCount, new ThreadFactory() {
                private final AtomicInteger threadIter = new AtomicInteger();
                private final String threadNamePattern = "membership-client-%d";

                @Override
                public Thread newThread(final Runnable runnable) {
                    return new Thread(runnable, String.format(threadNamePattern, threadIter.incrementAndGet()));
                }
            });
            final ClientInterceptor deadlineInterceptor = new ClientInterceptor() {
                @Override
                public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                        final MethodDescriptor<ReqT, RespT> method, final CallOptions callOptions, final Channel next) {
                    logger.debug("Intercepted {}", method.getFullMethodName());
                    return next.newCall(method, callOptions.withDeadlineAfter(serverDeadlineSeconds, TimeUnit.SECONDS));
                }
            };
            channel = ManagedChannelBuilder.forAddress(serverHost, serverPort).usePlaintext()
                    .executor(clientExecutor).offloadExecutor(clientExecutor)
                    .intercept(deadlineInterceptor)
                    .userAgent("membership-client").build();
            serviceStub = MembershipServiceGrpc.newBlockingStub(channel).withWaitForReady();
            ready.set(true);
            logger.info("Started MembershipClient [{}] connected to {}:{} in {} millis", getIdentity(), serverHost, serverPort,
                    TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS));
        }
    }

    @Override
    public void stop() throws Exception {
        if (running.compareAndSet(true, false)) {
            try {
                ready.set(false);
                channel.shutdownNow().awaitTermination(1L, TimeUnit.SECONDS);
                channel.shutdown();
                if (clientExecutor != null && !clientExecutor.isTerminated()) {
                    clientExecutor.shutdown();
                    clientExecutor.awaitTermination(2L, TimeUnit.SECONDS);
                    clientExecutor.shutdownNow();
                    logger.info("Stopped membership client worker threads");
                }
                logger.info("Stopped MembershipClient [{}] connected to {}:{}", getIdentity(), serverHost, serverPort);
            } catch (Exception tiniProblem) {
                logger.error(tiniProblem);
            }
        }
    }

    @Override
    public NewNamespaceResponse newNamespace(final NewNamespaceRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        NewNamespaceResponse response = null;
        try {
            response = serviceStub.newNamespace(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public NewCohortTypeResponse newCohortType(final NewCohortTypeRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        NewCohortTypeResponse response = null;
        try {
            response = serviceStub.newCohortType(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        NewCohortResponse response = null;
        try {
            response = serviceStub.newCohort(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public NewNodeResponse newNode(final NewNodeRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        NewNodeResponse response = null;
        try {
            response = serviceStub.newNode(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        ListCohortsResponse response = null;
        try {
            response = serviceStub.listCohorts(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        JoinCohortResponse response = null;
        try {
            response = serviceStub.joinCohort(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        DescribeCohortResponse response = null;
        try {
            response = serviceStub.describeCohort(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        LeaveCohortResponse response = null;
        try {
            response = serviceStub.leaveCohort(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public DeleteCohortResponse deleteCohort(final DeleteCohortRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        DeleteCohortResponse response = null;
        try {
            response = serviceStub.deleteCohort(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public DeleteCohortTypeResponse deleteCohortType(final DeleteCohortTypeRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        DeleteCohortTypeResponse response = null;
        try {
            response = serviceStub.deleteCohortType(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public ListNodesResponse listNodes(final ListNodesRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        ListNodesResponse response = null;
        try {
            response = serviceStub.listNodes(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public DeleteNodeResponse deleteNode(final DeleteNodeRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        DeleteNodeResponse response = null;
        try {
            response = serviceStub.deleteNode(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public PurgeNamespaceResponse purgeNamespace(final PurgeNamespaceRequest request) throws MembershipClientException {
        if (!isRunning()) {
            throw new MembershipClientException(Code.INVALID_MEMBERSHIP_CLIENT_LCM,
                    "Invalid attempt to operate an already stopped membership client");
        }
        PurgeNamespaceResponse response = null;
        try {
            response = serviceStub.purgeNamespace(request);
        } catch (Throwable problem) {
            toMembershipClientException(problem);
        }
        return response;
    }

    @Override
    public String getIdentity() {
        return identity;
    }

    @Override
    public boolean isRunning() {
        return running.get() && ready.get();
    }

    private static void toMembershipClientException(final Throwable problem) throws MembershipClientException {
        if (problem instanceof StatusException) {
            final StatusException statusException = StatusException.class.cast(problem);
            final String status = statusException.getStatus().toString();
            throw new MembershipClientException(Code.MEMBERSHIP_SERVER_ERROR, status, statusException);
        } else if (problem instanceof StatusRuntimeException) {
            final StatusRuntimeException statusRuntimeException = StatusRuntimeException.class.cast(problem);
            final String status = statusRuntimeException.getStatus().toString();
            throw new MembershipClientException(Code.MEMBERSHIP_SERVER_ERROR, status, statusRuntimeException);
        } else if (problem instanceof MembershipClientException) {
            throw MembershipClientException.class.cast(problem);
        } else {
            throw new MembershipClientException(Code.UNKNOWN_FAILURE, problem);
        }
    }

}
