package com.github.membership.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooKeeper.States;

import com.github.membership.domain.Cohort;
import com.github.membership.domain.CohortType;
import com.github.membership.domain.DeleteCohortRequest;
import com.github.membership.domain.DeleteCohortResponse;
import com.github.membership.domain.DeleteCohortTypeRequest;
import com.github.membership.domain.DeleteCohortTypeResponse;
import com.github.membership.domain.DeleteNodeRequest;
import com.github.membership.domain.DeleteNodeResponse;
import com.github.membership.domain.DescribeCohortRequest;
import com.github.membership.domain.DescribeCohortResponse;
import com.github.membership.domain.JoinCohortRequest;
import com.github.membership.domain.JoinCohortResponse;
import com.github.membership.domain.LeaveCohortRequest;
import com.github.membership.domain.LeaveCohortResponse;
import com.github.membership.domain.ListCohortsRequest;
import com.github.membership.domain.ListCohortsResponse;
import com.github.membership.domain.Member;
import com.github.membership.domain.NewCohortRequest;
import com.github.membership.domain.NewCohortResponse;
import com.github.membership.domain.NewCohortTypeRequest;
import com.github.membership.domain.NewCohortTypeResponse;
import com.github.membership.domain.NewNamespaceRequest;
import com.github.membership.domain.NewNamespaceResponse;
import com.github.membership.domain.NewNodeRequest;
import com.github.membership.domain.NewNodeResponse;
import com.github.membership.domain.Node;
import com.github.membership.domain.PurgeNamespaceRequest;
import com.github.membership.domain.PurgeNamespaceResponse;
import com.github.membership.server.MembershipServerException.Code;

/**
 * TODO: switch to using multi() where possible
 */
final class ZkMembershipService implements MembershipService {
    private static final Logger logger = LogManager.getLogger(ZkMembershipService.class.getSimpleName());

    private String identity;

    private final AtomicBoolean running;
    private final AtomicBoolean ready;

    // private final List<InetSocketAddress> serverAddresses;
    private String connectString;

    private ZooKeeper serverProxy;
    private long serverSessionId;

    private Set<String> trackedNamespaces;

    ZkMembershipService(final String connectString) {
        this.running = new AtomicBoolean(false);
        this.ready = new AtomicBoolean(false);

        // this.serverAddresses = new ArrayList<>();
        // this.serverAddresses.addAll(serverAddresses);
        this.connectString = connectString;

        this.trackedNamespaces = new CopyOnWriteArraySet<>();
    }

    @Override
    public String getIdentity() {
        return identity;
    }

    @Override
    public void start() throws MembershipServerException {
        identity = UUID.randomUUID().toString();
        logger.info("Starting ZkCohortMembership [{}]", getIdentity());
        if (running.compareAndSet(false, true)) {
            serverProxy = null;
            serverSessionId = 0L;

            final CountDownLatch transitionedToConnected = new CountDownLatch(1);
            // final StringBuilder connectString = new StringBuilder();
            //for (final InetSocketAddress serverAddress : serverAddresses) {
            //    connectString.append(serverAddress.getHostName()).append(':').append(serverAddress.getPort()).append(',');
            //}
            final int sessionTimeoutMillis = 2000;
            final Watcher watcher = new Watcher() {
                @Override
                public void process(final WatchedEvent watchedEvent) {
                    switch (watchedEvent.getState()) {
                        case SyncConnected:
                            transitionedToConnected.countDown();
                            break;
                        case Expired:
                            logger.info("Proxy session expired, watchedEvent: {}", watchedEvent);
                            break;
                        default:
                            break;
                    }
                }
            };
            try {
                serverProxy = new ZooKeeper(connectString, sessionTimeoutMillis, watcher);
                logger.debug("Server proxy connection state:{}", serverProxy.getState());
                transitionedToConnected.await();

                serverSessionId = serverProxy.getSessionId();
                ready.set(true);
                logger.info("Started ZkCohortMembership [{}], state:{}, sessionId:{}, connectedTo:[{}]",
                        getIdentity(), serverProxy.getState(), serverSessionId, connectString);
            } catch (final IOException zkConnectProblem) {
                throw new MembershipServerException(Code.MEMBERSHIP_INIT_FAILURE, "Failed to start membership service");
            } catch (final InterruptedException zkConnectWaitProblem) {
                throw new MembershipServerException(Code.MEMBERSHIP_INIT_FAILURE, "Failed to start membership service");
            }
        } else {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to start an already running membership service");
        }
    }

    @Override
    public void stop() throws MembershipServerException {
        logger.info("Stopping ZkCohortMembership [{}], state:{}, sessionId:{}",
                getIdentity(), serverProxy.getState(), serverSessionId);
        if (running.compareAndSet(true, false)) {
            ready.set(false);
            States state = null;
            try {
                for (final String namespace : trackedNamespaces) {
                    logger.info("Remaining tree nodes:{}", flattenTree("/" + namespace));
                }
                serverProxy.close();
                state = serverProxy.getState();
                // logger.info("Server proxy connection state:{}, sessionId:{}", serverProxy.getState(), serverSessionId);
            } catch (KeeperException keeperProblem) {
                // mostly ignore
                logger.error(keeperProblem);
            } catch (InterruptedException problem) {
                Thread.currentThread().interrupt();
            } finally {
                serverProxy = null;
            }
            // serverAddresses.clear();
            logger.info("Stopped ZkCohortMembership [{}], state:{}, sessionId:{}",
                    getIdentity(), state, serverSessionId);
        } else {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to stop an already stopped membership service");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get() && ready.get();
    }

    @Override
    public NewNamespaceResponse newNamespace(final NewNamespaceRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        String namespacePath = "/" + namespace;
        try {
            // create namespace node
            if (serverProxy.exists(namespacePath, false) == null) {
                logger.info("Creating namespace {}", namespacePath);
                namespacePath = serverProxy.create(namespacePath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                // create cohorts root node
                final String cohortRootPath = serverProxy.create(namespacePath + "/cohorts", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
                logger.info("Created cohorts root:{}", cohortRootPath);

                // create nodes root node
                final String nodeRootPath = serverProxy.create(namespacePath + "/nodes", null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                logger.info("Created nodes root:{}", nodeRootPath);

                trackedNamespaces.add(namespace);
            } else {
                logger.warn("Namespace already exists {}", namespacePath);
            }
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final NewNamespaceResponse response = new NewNamespaceResponse();
        response.setNamespace(namespace);
        response.setPath(namespacePath);
        response.setSuccess(true);
        logger.debug(response);
        return response;
    }

    @Override
    public PurgeNamespaceResponse purgeNamespace(final PurgeNamespaceRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        try {
            final String namespacePath = "/" + namespace;
            final List<String> childNodes = flattenTree(namespacePath);
            // start with leaves, work up from there
            for (int iter = childNodes.size() - 1; iter >= 0; iter--) {
                final String path = childNodes.get(iter);
                logger.info("Deleting {}", path);
                serverProxy.delete(path, -1);
            }

            trackedNamespaces.remove(namespace);
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final PurgeNamespaceResponse response = new PurgeNamespaceResponse();
        response.setNamespace(namespace);
        response.setSuccess(true);
        logger.debug(response);
        return response;
    }

    @Override
    public NewCohortTypeResponse newCohortType(final NewCohortTypeRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        final CohortType cohortType = request.getCohortType();
        final String cohortRootPath = "/" + namespace + "/cohorts";
        String cohortTypePath = cohortRootPath + "/" + cohortType;
        try {
            if (serverProxy.exists(cohortTypePath, false) == null) {
                logger.info("Creating cohort type {}", cohortTypePath);
                cohortTypePath = serverProxy.create(cohortTypePath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                logger.warn("Cohort type tree already exsts {}", cohortTypePath);
            }
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final NewCohortTypeResponse response = new NewCohortTypeResponse();
        response.setCohortType(cohortType);
        response.setPath(cohortTypePath);
        response.setSuccess(true);
        logger.debug(response);
        return response;
    }

    @Override
    public NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();
        String cohortChildPath = null;
        try {
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortTypePath = cohortRootPath + "/" + cohortType;
            if (serverProxy.exists(cohortTypePath, false) == null) {
                logger.warn("Failed to locate cohortType tree {}", cohortTypePath);
                throw new MembershipServerException(Code.UNKNOWN_FAILURE);
            }
            cohortChildPath = cohortTypePath + "/" + cohortId;
            if (serverProxy.exists(cohortChildPath, false) == null) {
                logger.info("Creating cohort {}", cohortChildPath);
                cohortChildPath = serverProxy.create(cohortChildPath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                // optional for debugging
                if (serverProxy.exists(cohortChildPath, false) == null) {
                    logger.warn("Failed to create cohort {}", cohortChildPath);
                }

                String membersChildPath = cohortChildPath + "/members";
                logger.debug("Creating members root {}", membersChildPath);
                membersChildPath = serverProxy.create(membersChildPath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                // optional for debugging
                if (serverProxy.exists(membersChildPath, false) == null) {
                    logger.warn("Failed to create members root {}", membersChildPath);
                }
            } else {
                logger.warn("Failed to locate cohort child tree {}", cohortChildPath);
            }
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final Cohort cohort = new Cohort();
        cohort.setId(cohortId);
        cohort.setPath(cohortChildPath);
        cohort.setType(cohortType);

        final NewCohortResponse response = new NewCohortResponse();
        response.setCohort(cohort);
        logger.debug(response);
        return response;
    }

    @Override
    public NewNodeResponse newNode(final NewNodeRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        final String nodeId = request.getNodeId();
        final InetSocketAddress address = request.getAddress();
        String nodeChildPath = null;
        try {
            final String nodeRootPath = "/" + namespace + "/nodes";
            nodeChildPath = nodeRootPath + "/" + nodeId;
            if (serverProxy.exists(nodeChildPath, false) == null) {
                logger.info("Creating node {}", nodeChildPath);
                // TODO: save data in znode
                nodeChildPath = serverProxy.create(nodeChildPath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                logger.warn("Failed to locate node tree {}", nodeChildPath);
            }
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final Node node = new Node();
        node.setId(nodeId);
        node.setAddress(address);
        node.setPath(nodeChildPath);

        final NewNodeResponse response = new NewNodeResponse();
        response.setNode(node);
        logger.debug(response);
        return response;
    }

    @Override
    public ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        final List<Cohort> cohorts = new ArrayList<>();
        try {
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final List<String> cohortTypes = serverProxy.getChildren(cohortRootPath, false);
            for (final String cohortType : cohortTypes) {
                final String cohortTypePath = cohortRootPath + "/" + cohortType;
                final List<String> cohortIds = serverProxy.getChildren(cohortTypePath, false);
                for (final String cohortId : cohortIds) {
                    final Cohort cohort = new Cohort();
                    cohort.setId(cohortId);
                    cohort.setPath(cohortTypePath + "/" + cohortId);
                    cohort.setType(CohortType.fromString(cohortType));
                    cohorts.add(cohort);
                }
            }
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final ListCohortsResponse response = new ListCohortsResponse();
        response.setCohorts(cohorts);
        logger.debug(response);
        return response;
    }

    @Override
    public JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        final String memberId = request.getMemberId();
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();
        final String nodeId = request.getNodeId();

        String memberChildPath = null;
        try {
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
            if (serverProxy.exists(cohortMembersPath, false) == null) {
                final String warning = "Failed to locate member tree " + cohortMembersPath;
                logger.warn(warning);
                throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
            }
            memberChildPath = cohortMembersPath + "/" + memberId;
            logger.info("Creating member {}", memberChildPath);
            // TODO: save data in znode
            memberChildPath = serverProxy.create(memberChildPath, null,
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final Member member = new Member();
        member.setMemberId(memberId);
        member.setCohortType(cohortType);
        member.setCohortId(cohortId);
        member.setNodeId(nodeId);
        member.setPath(memberChildPath);

        final JoinCohortResponse response = new JoinCohortResponse();
        response.setMember(member);
        logger.debug(response);
        return response;
    }

    @Override
    public DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();

        final List<Member> members = new ArrayList<>();
        try {
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
            if (serverProxy.exists(cohortMembersPath, false) == null) {
                final String warning = "Failed to locate member tree " + cohortMembersPath;
                logger.warn(warning);
                throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
            }
            final List<String> memberIds = serverProxy.getChildren(cohortMembersPath, false);
            for (final String memberId : memberIds) {
                final Member member = new Member();
                member.setCohortId(cohortId);
                member.setCohortType(cohortType);
                member.setMemberId(memberId);
                member.setNodeId(null); // TODO
                member.setPath(cohortMembersPath + "/" + memberId);
                members.add(member);
            }
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final DescribeCohortResponse response = new DescribeCohortResponse();
        response.setCohortId(cohortId);
        response.setCohortType(cohortType);
        response.setMembers(members);
        logger.debug(response);
        return response;
    }

    @Override
    public LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();
        final String memberId = request.getMemberId();
        try {
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
            if (serverProxy.exists(cohortMembersPath, false) == null) {
                final String warning = "Failed to locate member tree " + cohortMembersPath;
                logger.warn(warning);
                throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
            }
            serverProxy.delete(cohortMembersPath + "/" + memberId, -1);
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final LeaveCohortResponse response = new LeaveCohortResponse();
        response.setCohortId(cohortId);
        response.setCohortType(cohortType);
        response.setMemberId(memberId);
        response.setSuccess(true);
        logger.debug(response);
        return response;
    }

    @Override
    public DeleteCohortResponse deleteCohort(final DeleteCohortRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();
        try {
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortPath = cohortRootPath + "/" + cohortType + "/" + cohortId;
            if (serverProxy.exists(cohortPath, false) == null) {
                final String warning = "Failed to locate cohort " + cohortPath;
                logger.warn(warning);
                throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
            }

            logger.info("Deleting cohort {}", cohortPath);
            final List<String> childNodes = flattenTree(cohortPath);
            logger.debug(childNodes);

            // start with leaves, work up from there
            for (int iter = childNodes.size() - 1; iter >= 0; iter--) {
                final String path = childNodes.get(iter);
                logger.info("Deleting {}", path);
                serverProxy.delete(path, -1);
            }
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final DeleteCohortResponse response = new DeleteCohortResponse();
        response.setCohortId(cohortId);
        response.setCohortType(cohortType);
        response.setSuccess(true);
        logger.debug(response);
        return response;
    }

    @Override
    public DeleteCohortTypeResponse deleteCohortType(final DeleteCohortTypeRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        final CohortType cohortType = request.getCohortType();
        try {
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortTypePath = cohortRootPath + "/" + cohortType;
            if (serverProxy.exists(cohortTypePath, false) == null) {
                final String warning = "Failed to locate cohort type " + cohortTypePath;
                logger.warn(warning);
                throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
            }

            logger.info("Delete cohortType {}", cohortTypePath);
            final List<String> childNodes = flattenTree(cohortTypePath);
            logger.debug(childNodes);

            // start with leaves, work up from there
            for (int iter = childNodes.size() - 1; iter >= 0; iter--) {
                final String path = childNodes.get(iter);
                logger.info("Deleting {}", path);
                serverProxy.delete(path, -1);
            }
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final DeleteCohortTypeResponse response = new DeleteCohortTypeResponse();
        response.setCohortType(cohortType);
        response.setSuccess(true);
        logger.debug(response);
        return response;
    }

    @Override
    public DeleteNodeResponse deleteNode(final DeleteNodeRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        if (!request.validate()) {
            throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        }
        final String namespace = request.getNamespace();
        final String nodeId = request.getNodeId();
        try {
            final String nodeRootPath = "/" + namespace + "/nodes";
            final String nodePath = nodeRootPath + "/" + nodeId;
            if (serverProxy.exists(nodePath, false) == null) {
                final String warning = "Failed to locate node " + nodePath;
                logger.warn(warning);
                throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
            }

            logger.info("Deleting node {}", nodePath);
            serverProxy.delete(nodePath, -1);
        } catch (final KeeperException keeperException) {
            if (keeperException instanceof KeeperException.NodeExistsException) {
                // node already exists
            } else {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
            }
        } catch (final InterruptedException interruptedException) {
            // fix later
            throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
        }

        final DeleteNodeResponse response = new DeleteNodeResponse();
        response.setNamespace(namespace);
        response.setNodeId(nodeId);
        response.setSuccess(true);
        logger.debug(response);
        return response;
    }

    private static boolean checkIdempotency(final KeeperException keeperException) {
        boolean idempotent = false;
        switch (keeperException.code()) {
            case CONNECTIONLOSS:
            case SESSIONEXPIRED:
            case SESSIONMOVED:
            case OPERATIONTIMEOUT:
                idempotent = true;
        }
        return idempotent;
    }

    private List<String> flattenTree(final String subTreeRoot) throws KeeperException, InterruptedException {
        final List<String> flattened = new ArrayList<>();
        if (subTreeRoot != null && !subTreeRoot.trim().isEmpty()) {
            // run breadth first search
            final Queue<String> queue = new ArrayDeque<>();
            queue.add(subTreeRoot);
            flattened.add(subTreeRoot);
            while (!queue.isEmpty()) {
                final String node = queue.poll();
                final List<String> childNodes = serverProxy.getChildren(node, false);
                for (final String childNode : childNodes) {
                    final String childNodePath = node + "/" + childNode;
                    queue.add(childNodePath);
                    flattened.add(childNodePath);
                }
            }
        }
        return flattened;
    }

}
