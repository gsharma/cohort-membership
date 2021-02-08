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

                // create namespace below root
                namespacePath = serverProxy.create("/" + namespace, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                logger.info("Created namespace:{}", namespacePath);

                cohortRootPath = serverProxy.create(namespacePath + "/cohorts", null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                logger.info("Created cohort root:{}", cohortRootPath);

                // for (final CohortType cohortType : CohortType.values()) {
                // final String cohortChildPath = serverProxy.create(cohortRootPath + "/" + cohortType, null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                // CreateMode.PERSISTENT);
                // cohortPaths.put(cohortType, cohortChildPath);
                // logger.info("Created cohort:{}", cohortChildPath);
                // }

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
            cohortChildPath = cohortRootPath + "/" + cohortType;
            logger.info("Creating cohort {}", cohortChildPath);
            cohortChildPath = serverProxy.create(cohortChildPath, cohortId.getBytes(),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            cohortPaths.put(cohortType, cohortChildPath);
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
        return null;
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

}
