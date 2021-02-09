package com.github.membership.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                logger.info("Started ZkCohortMembership [{}], state:{}, sessionId:{}, namespace:{}",
                        getIdentity(), serverProxy.getState(), serverSessionId, namespacePath);
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
        if (running.compareAndSet(true, false)) {
            ready.set(false);
            States state = null;
            try {
                serverProxy.close();
                state = serverProxy.getState();
                // logger.info("Server proxy connection state:{}, sessionId:{}", serverProxy.getState(), serverSessionId);
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
    public NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipServerException {
        // TODO
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        final String cohortId = request.getCohortId();
        final CohortType cohortType = request.getCohortType();
        String cohortChildPath = null;
        try {
            String cohortTypePath = cohortRootPath + "/" + cohortType;
            if (serverProxy.exists(cohortTypePath, false) == null) {
                logger.info("Creating cohort type {}", cohortTypePath);
                cohortTypePath = serverProxy.create(cohortTypePath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                cohortPaths.put(cohortType, cohortTypePath);
            } else {
                logger.warn("Failed to locate cohort type tree {}", cohortTypePath);
            }
            cohortChildPath = cohortTypePath + "/" + cohortId;
            if (serverProxy.exists(cohortChildPath, false) == null) {
                logger.info("Creating cohort child {}", cohortChildPath);
                cohortChildPath = serverProxy.create(cohortChildPath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                final String membersChildPath = cohortChildPath + "/members";
                logger.info("Creating members child {}", membersChildPath);
                serverProxy.create(membersChildPath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                logger.info("Created members child {}", membersChildPath);
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
        return response;
    }

    @Override
    public NewNodeResponse newNode(final NewNodeRequest request) throws MembershipServerException {
        // TODO
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
                logger.info("Creating node child {}", nodeChildPath);
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
        return response;
    }

    @Override
    public ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipServerException {
        // TODO
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        return null;
    }

    @Override
    public JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipServerException {
        // TODO
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
                memberChildPath = cohortMembersPath + "/" + memberId;
                logger.info("Creating member child {}", memberChildPath);
                // TODO: save data in znode
                memberChildPath = serverProxy.create(memberChildPath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            } else {
                logger.warn("Failed to locate member tree {}", cohortMembersPath);
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

        final Member member = new Member();
        member.setMemberId(memberId);
        member.setCohortType(cohortType);
        member.setCohortId(cohortId);
        member.setNodeId(nodeId);
        member.setPath(memberChildPath);

        final JoinCohortResponse response = new JoinCohortResponse();
        response.setMember(member);
        return response;
    }

    @Override
    public DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipServerException {
        // TODO
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        return null;
    }

    @Override
    public LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipServerException {
        // TODO
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        return null;
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

}
