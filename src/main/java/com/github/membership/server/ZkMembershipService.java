package com.github.membership.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

import com.github.membership.server.MembershipServerException.Code;

final class ZkMembershipService implements MembershipService {
    private static final Logger logger = LogManager.getLogger(ZkMembershipService.class.getSimpleName());

    private final String identity = UUID.randomUUID().toString();

    private final AtomicBoolean running;
    private final AtomicBoolean ready;

    private final List<InetSocketAddress> serverAddresses;
    private final String namespace;
    private Map<CohortType, String> cohortPaths;

    private ZooKeeper serverProxy;
    private long serverSessionId;
    private String namespacePath;
    private String cohortRootPath;
    private String nodeRootPath;

    ZkMembershipService(final List<InetSocketAddress> serverAddresses, final String namespace) {
        this.running = new AtomicBoolean(false);
        this.ready = new AtomicBoolean(false);

        this.serverAddresses = new ArrayList<>();
        this.serverAddresses.addAll(serverAddresses);
        this.namespace = namespace;
        this.cohortPaths = new ConcurrentHashMap<>();
    }

    @Override
    public String getIdentity() {
        return identity;
    }

    @Override
    public void start() throws MembershipServerException {
        logger.info("Starting ZkCohortMembership [{}]", getIdentity());
        if (running.compareAndSet(false, true)) {
            serverProxy = null;
            serverSessionId = 0;
            namespacePath = null;
            cohortPaths.clear();

            final CountDownLatch transitionedToConnected = new CountDownLatch(1);
            final StringBuilder connectString = new StringBuilder();
            for (final InetSocketAddress serverAddress : serverAddresses) {
                connectString.append(serverAddress.getHostName()).append(':').append(serverAddress.getPort()).append(',');
            }
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
                serverProxy = new ZooKeeper(connectString.toString(), sessionTimeoutMillis, watcher);
                logger.debug("Server proxy connection state:{}", serverProxy.getState());
                transitionedToConnected.await();

                // create namespace node
                namespacePath = serverProxy.create("/" + namespace, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                logger.info("Created namespace:{}", namespacePath);

                // create cohorts root node
                cohortRootPath = serverProxy.create(namespacePath + "/cohorts", null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                logger.info("Created cohorts root:{}", cohortRootPath);

                // create nodes root node
                nodeRootPath = serverProxy.create(namespacePath + "/nodes", null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                logger.info("Created nodes root:{}", nodeRootPath);

                serverSessionId = serverProxy.getSessionId();
                ready.set(true);
                logger.info("Started ZkCohortMembership [{}], state:{}, sessionId:{}, namespace:{}, connectedTo:[{}]",
                        getIdentity(), serverProxy.getState(), serverSessionId, namespacePath, connectString);
            } catch (final KeeperException keeperException) {
                if (keeperException instanceof KeeperException.NodeExistsException) {
                    // node already exists
                } else {
                    throw new MembershipServerException(Code.MEMBERSHIP_INIT_FAILURE, keeperException);
                }
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
                logger.info("Remaining tree nodes:{}", flattenTree(namespacePath));
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
                namespacePath = null;
            }
            serverAddresses.clear();
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
    public NewCohortTypeResponse newCohortType(final NewCohortTypeRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        final CohortType cohortType = request.getCohortType();
        String cohortTypePath = cohortRootPath + "/" + cohortType;
        try {
            if (serverProxy.exists(cohortTypePath, false) == null) {
                logger.info("Creating cohort type {}", cohortTypePath);
                cohortTypePath = serverProxy.create(cohortTypePath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                // logger.debug("Created cohort type {}", cohortTypePath);
                cohortPaths.put(cohortType, cohortTypePath);
            } else {
                logger.warn("Failed to locate cohort type tree {}", cohortTypePath);
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
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();
        String cohortChildPath = null;
        try {
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
        final String nodeId = request.getNodeId();
        final InetSocketAddress address = request.getAddress();
        String nodeChildPath = null;
        try {
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
        return null;
    }

    @Override
    public JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipServerException {
        // TODO
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        final String memberId = request.getMemberId();
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();
        final String nodeId = request.getNodeId();
        String memberChildPath = null;
        try {
            final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
            if (serverProxy.exists(cohortMembersPath, false) == null) {
                logger.warn("Failed to locate member tree {}", cohortMembersPath);
                throw new MembershipServerException(Code.UNKNOWN_FAILURE);
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
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();

        final List<Member> members = new ArrayList<>();
        try {
            final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
            if (serverProxy.exists(cohortMembersPath, false) == null) {
                logger.warn("Failed to locate member tree {}", cohortMembersPath);
                throw new MembershipServerException(Code.UNKNOWN_FAILURE);
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
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();
        final String memberId = request.getMemberId();
        try {
            final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
            if (serverProxy.exists(cohortMembersPath, false) == null) {
                logger.warn("Failed to locate member tree {}", cohortMembersPath);
                throw new MembershipServerException(Code.UNKNOWN_FAILURE);
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
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();
        try {
            final String cohortPath = cohortRootPath + "/" + cohortType + "/" + cohortId;
            if (serverProxy.exists(cohortPath, false) == null) {
                logger.warn("Failed to locate cohort {}", cohortPath);
                throw new MembershipServerException(Code.UNKNOWN_FAILURE);
            }

            logger.info("Delete cohort {}", cohortPath);
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
        final CohortType cohortType = request.getCohortType();
        try {
            final String cohortTypePath = cohortRootPath + "/" + cohortType;
            if (serverProxy.exists(cohortTypePath, false) == null) {
                logger.warn("Failed to locate cohort type {}", cohortTypePath);
                throw new MembershipServerException(Code.UNKNOWN_FAILURE);
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
