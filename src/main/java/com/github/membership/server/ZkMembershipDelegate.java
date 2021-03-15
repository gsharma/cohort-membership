package com.github.membership.server;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.api.transaction.CuratorOp;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
// import org.apache.curator.framework.recipes.watch.PersistentWatcher;
import org.apache.curator.retry.BoundedExponentialBackoffRetry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;

import com.github.membership.lib.Lifecycle;
import com.github.membership.rpc.Cohort;
import com.github.membership.rpc.CohortType;
import com.github.membership.rpc.CohortUpdate;
import com.github.membership.rpc.CohortUpdateType;
import com.github.membership.rpc.Member;
import com.github.membership.rpc.MembershipUpdate;
import com.github.membership.rpc.MembershipUpdateType;
import com.github.membership.rpc.Namespace;
import com.github.membership.rpc.Node;
import com.github.membership.rpc.NodeUpdate;
import com.github.membership.rpc.NodeUpdateType;
import com.github.membership.server.MembershipServerException.Code;
import com.google.protobuf.ByteString;

/**
 * A Zookeeper-backed Cohort Membership Service.
 * 
 * TODO: switch to using multi() where possible
 */
final class ZkMembershipDelegate implements MembershipDelegate {
    private static final Logger logger = LogManager.getLogger(ZkMembershipDelegate.class.getSimpleName());

    private final MembershipServerConfiguration configuration;
    private final DelegateMode mode;
    private final AtomicBoolean running;
    private final AtomicBoolean ready;

    private String identity;

    private CuratorFramework serverProxyCurator;
    private ZooKeeper serverProxyZk;
    private volatile long serverSessionId;

    private final Set<String> trackedNamespaces;
    private final ConcurrentMap<String, InterProcessLock> trackedLocks;

    ZkMembershipDelegate(final MembershipServerConfiguration configuration, final DelegateMode mode) {
        this.running = new AtomicBoolean(false);
        this.ready = new AtomicBoolean(false);

        // this.serverAddresses = new ArrayList<>();
        // this.serverAddresses.addAll(serverAddresses);
        this.configuration = configuration;
        this.mode = mode;

        this.trackedNamespaces = new CopyOnWriteArraySet<>();
        this.trackedLocks = new ConcurrentHashMap<>();
    }

    @Override
    public String getIdentity() {
        return identity;
    }

    @Override
    public void start() throws MembershipServerException {
        identity = UUID.randomUUID().toString();
        logger.info("Starting ZkCohortMembership [{}], mode:{}", getIdentity(), mode);
        logger.debug(configuration.toString());
        if (running.compareAndSet(false, true)) {
            final long startNanos = System.nanoTime();
            ready.set(false);
            serverProxyZk = null;
            serverSessionId = 0L;

            final String connectString = configuration.getConnectString();
            final int sessionTimeoutMillis = configuration.getClientSessionTimeoutMillis(); // 60*1000
                                                                                            // or
                                                                                            // more
            final int connectTimeoutMillis = configuration.getClientConnectionTimeoutMillis(); // 15*1000
                                                                                               // or
                                                                                               // more
            final long sessionEstablishmentTimeoutSeconds = configuration.getClientSessionEstablishmentTimeoutSeconds();

            final String serverUser = configuration.getServerUser();
            final String serverPassword = configuration.getServerPassword();
            final boolean isAuth = serverUser != null && !serverUser.trim().isEmpty() && serverPassword != null
                    && !serverPassword.trim().isEmpty();

            switch (mode) {
            case ZK_DIRECT: {
                final CountDownLatch transitionedToConnected = new CountDownLatch(1);
                // final StringBuilder connectString = new StringBuilder();
                // for (final InetSocketAddress serverAddress : serverAddresses)
                // {
                // connectString.append(serverAddress.getHostName()).append(':').append(serverAddress.getPort()).append(',');
                // }
                // Handle zk connection states
                final Watcher watcher = new Watcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        switch (watchedEvent.getState()) {
                        case SyncConnected:
                        case ConnectedReadOnly:
                            serverSessionId = serverProxyZk.getSessionId();
                            logger.info("ZkCohortMembership connected to zk, sessionId:{}, servers:[{}]",
                                    getServerSessionId(), connectString);
                            transitionedToConnected.countDown();
                            break;
                        case Expired:
                            // TODO: handle session expiration
                            logger.info("ZkCohortMembership zk session expired, sessionId:{}, servers:[{}]",
                                    getServerSessionId(), connectString);
                            try {
                                handleSessionExpiration();
                            } catch (MembershipServerException problem) {
                                logger.error("handleSessionExpiration encountered a problem", problem);
                            }
                            break;
                        case Disconnected:
                            logger.info("ZkCohortMembership disconnected from zk, sessionId:{}, servers:[{}]",
                                    getServerSessionId(), connectString);
                            try {
                                handleServerDisconnection();
                            } catch (MembershipServerException problem) {
                                logger.error("handleServerDisconnection encountered a problem", problem);
                            }
                            break;
                        case AuthFailed:
                            logger.info("ZkCohortMembership zk auth failed, sessionId:{}, servers:[{}]",
                                    getServerSessionId(), connectString);
                            break;
                        // case Closed:
                        // logger.info("ZkCohortMembership zk session closed,
                        // sessionId:{},
                        // servers:[{}]",
                        // getServerSessionId(), connectString);
                        // break;
                        default:
                            logger.info("ZkCohortMembership encountered:{}, sessionId:{}, servers:[{}]", watchedEvent,
                                    getServerSessionId(), connectString);
                            break;
                        }
                    }
                };
                try {
                    serverProxyZk = new ZooKeeper(connectString, sessionTimeoutMillis, watcher);

                    logger.debug("Server proxy connection state:{}", serverProxyZk.getState());
                    // serverSessionId = serverProxy.getSessionId();
                    if (transitionedToConnected.await(sessionEstablishmentTimeoutSeconds, TimeUnit.SECONDS)) {
                        ready.set(true);
                        if (isAuth) {
                            final byte[] auth = (serverUser + ":" + serverPassword).getBytes();
                            final String scheme = "digest";
                            serverProxyZk.addAuthInfo(scheme, auth);
                        }
                        logger.info(
                                "Started ZkCohortMembership [{}], state:{}, mode:{}, sessionId:{}, connectedTo:[{}] in {} millis",
                                getIdentity(), serverProxyZk.getState(), mode, getServerSessionId(), connectString,
                                TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS));
                    } else {
                        throw new MembershipServerException(Code.MEMBERSHIP_INIT_FAILURE,
                                "Failed to start membership service");
                    }
                } catch (final IOException zkConnectProblem) {
                    throw new MembershipServerException(Code.MEMBERSHIP_INIT_FAILURE,
                            "Failed to start membership service", zkConnectProblem);
                } catch (final InterruptedException zkConnectWaitProblem) {
                    throw new MembershipServerException(Code.MEMBERSHIP_INIT_FAILURE,
                            "Failed to start membership service", zkConnectWaitProblem);
                }
                break;
            }
            case CURATOR: {
                try {
                    // TODO: properties
                    final int baseSleepTimeMs = 1000;
                    final int maxSleepTimeMs = 1000;
                    final int maxRetries = 3;
                    final RetryPolicy retryPolicy = new BoundedExponentialBackoffRetry(baseSleepTimeMs, maxSleepTimeMs,
                            maxRetries);
                    final CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                            .connectString(connectString).connectionTimeoutMs(connectTimeoutMillis)
                            .sessionTimeoutMs(sessionTimeoutMillis).retryPolicy(retryPolicy);
                    if (isAuth) {
                        final String authString = serverUser + ":" + serverPassword;
                        final Id authId = new Id("auth", authString);
                        final List<ACL> aclList = Collections.singletonList(new ACL(ZooDefs.Perms.ALL, authId));
                        builder.authorization("digest", authString.getBytes()).aclProvider(new ACLProvider() {

                            @Override
                            public List<ACL> getDefaultAcl() {
                                return aclList;
                            }

                            @Override
                            public List<ACL> getAclForPath(String s) {
                                return aclList;
                            }
                        });
                    }
                    serverProxyCurator = builder.build();
                    serverProxyCurator.start();
                    serverSessionId = serverProxyCurator.getZookeeperClient().getZooKeeper().getSessionId();
                    ready.set(true);
                    logger.info(
                            "Started ZkCohortMembership [{}], state:{}, mode:{}, sessionId:{}, connectedTo:[{}] in {} millis",
                            getIdentity(), serverProxyCurator.getState(), mode, getServerSessionId(), connectString,
                            TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS));
                } catch (final Exception curatorProblem) {
                    throw new MembershipServerException(Code.MEMBERSHIP_INIT_FAILURE,
                            "Failed to start membership service", curatorProblem);
                }
                break;
            }
            }
        } else {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to start an already running membership service");
        }
    }

    private void handleSessionExpiration() throws MembershipServerException {
        logger.info("ZkCohortMembership handling session expiration, sessionId:{}, servers:[{}]", getServerSessionId(),
                configuration.getConnectString());
        if (isRunning()) {
            // potentially dangerous
            // consider retries
            stop();
            start();
            if (!isRunning()) {
                logger.warn("ZkCohortMembership failed to restart");
            }
        }
    }

    private void handleServerDisconnection() throws MembershipServerException {
        logger.info("ZkCohortMembership handling server disconnection, sessionId:{}, servers:[{}]",
                getServerSessionId(), configuration.getConnectString());
        if (isRunning()) {
            // TODO: figure out if self-healing is even worth it. A safer option
            // is to propagate this upto client and let them decide.
            // potentially dangerous
            // consider retries
            // stop();
            // start();
            // if (!isRunning()) {
            // logger.warn("ZkCohortMembership failed to restart");
            // }
        }
    }

    private long getServerSessionId() {
        switch (mode) {
        case ZK_DIRECT: {
            serverSessionId = serverProxyZk.getSessionId();
            break;
        }
        case CURATOR: {
            try {
                serverSessionId = serverProxyCurator.getZookeeperClient().getZooKeeper().getSessionId();
            } catch (Exception ignored) {
            }
            break;
        }
        }
        return serverSessionId;
    }

    @Override
    public void stop() throws MembershipServerException {
        if (running.compareAndSet(true, false)) {
            ready.set(false);
            switch (mode) {
            case ZK_DIRECT: {
                if (serverProxyZk != null) {
                    logger.info("Stopping ZkCohortMembership [{}], state:{}, mode:{}, sessionId:{}", getIdentity(),
                            serverProxyZk.getState(), mode, getServerSessionId());
                    if (serverProxyZk.getState().isConnected()) {
                        try {
                            for (final String namespace : trackedNamespaces) {
                                logger.info("Remaining tree nodes:{}", flattenTree("/" + namespace));
                            }
                            serverProxyZk.close();
                            // logger.info("Server proxy connection state:{},
                            // sessionId:{}",
                            // serverProxy.getState(), serverSessionId);
                            logger.info("Stopped ZkCohortMembership [{}], state:{}, mode:{}, sessionId:{}",
                                    getIdentity(), serverProxyZk.getState(), mode, getServerSessionId());
                        } catch (InterruptedException problem) {
                            Thread.currentThread().interrupt();
                        } finally {
                            serverProxyZk = null;
                        }
                    }
                }
                // serverAddresses.clear();
                break;
            }
            case CURATOR: {
                if (serverProxyCurator != null) {
                    try {
                        logger.info("Stopping ZkCohortMembership [{}], state:{}, mode:{}, sessionId:{}", getIdentity(),
                                serverProxyCurator.getState(), mode, getServerSessionId());
                        for (final String namespace : trackedNamespaces) {
                            logger.info("Remaining tree nodes:{}", flattenTree("/" + namespace));
                        }
                        serverProxyCurator.close();
                        logger.info("Stopped ZkCohortMembership [{}], state:{}, mode:{}, sessionId:{}", getIdentity(),
                                serverProxyCurator.getState(), mode, getServerSessionId());
                    } catch (final Exception problem) {
                        // mostly ignore
                        logger.error(problem);
                    } finally {
                        serverProxyCurator = null;
                    }
                }
                break;
            }
            }
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
    public Namespace newNamespace(final String name, final byte[] namespaceMetadata) throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        Namespace namespace = null;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                String namespacePath = "/" + name;

                // create namespace node
                if (serverProxyZk.exists(namespacePath, false) == null) {
                    logger.debug("Creating namespace {}", namespacePath);
                    final Stat namespaceStat = new Stat();
                    namespacePath = serverProxyZk.create(namespacePath, namespaceMetadata, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT, namespaceStat);
                    logger.debug("namespace:{}, stat:{}", namespacePath, namespaceStat);
                    logger.info("Created namespace:{}, zxid:{}", namespacePath, namespaceStat.getCzxid());

                    // create cohorts root node
                    final Stat cohortRootStat = new Stat();
                    final String cohortRootPath = serverProxyZk.create(namespacePath + "/cohorts", null,
                            ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT/* , cohortRootStat */);
                    logger.debug("cohorts root:{}, stat:{}", cohortRootPath, cohortRootStat);
                    logger.info("Created cohorts root:{}, zxid:{}", cohortRootPath, cohortRootStat.getCzxid());

                    // create nodes root node
                    final Stat nodeRootStat = new Stat();
                    final String nodeRootPath = serverProxyZk.create(namespacePath + "/nodes", null,
                            ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT/* , nodeRootStat */);
                    logger.debug("nodes root:{}, stat:{}", nodeRootPath, nodeRootStat);
                    logger.info("Created nodes root:{}, zxid:{}", nodeRootPath, nodeRootStat.getCzxid());

                    final Namespace.Builder namespaceBuilder = Namespace.newBuilder().setName(name)
                            .setVersion(namespaceStat.getVersion());
                    if (namespaceMetadata != null) {
                        namespaceBuilder.setPayload(toByteString(namespaceMetadata));
                    }
                    namespace = namespaceBuilder.build();

                    trackedNamespaces.add(name);
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
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                String namespacePath = "/" + name;

                // create namespace node
                if (serverProxyCurator.checkExists().forPath(namespacePath) == null) {
                    logger.debug("Creating namespace {}", namespacePath);
                    final Stat namespaceStat = new Stat();
                    namespacePath = serverProxyCurator.create().storingStatIn(namespaceStat)
                            .withMode(CreateMode.PERSISTENT).forPath(namespacePath, namespaceMetadata);
                    logger.debug("namespace:{}, stat:{}", namespacePath, namespaceStat);
                    logger.info("Created namespace:{}, zxid:{}", namespacePath, namespaceStat.getCzxid());

                    // create cohorts root node
                    final Stat cohortRootStat = new Stat();
                    final String cohortRootPath = serverProxyCurator.create().storingStatIn(cohortRootStat)
                            .withMode(CreateMode.PERSISTENT).forPath(namespacePath + "/cohorts");
                    logger.debug("cohorts root:{}, stat:{}", cohortRootPath, cohortRootStat);
                    logger.info("Created cohorts root:{}, zxid:{}", cohortRootPath, cohortRootStat.getCzxid());

                    // create nodes root node
                    final Stat nodeRootStat = new Stat();
                    final String nodeRootPath = serverProxyCurator.create().storingStatIn(nodeRootStat)
                            .withMode(CreateMode.PERSISTENT).forPath(namespacePath + "/nodes");
                    logger.debug("nodes root:{}, stat:{}", nodeRootPath, nodeRootStat);
                    logger.info("Created nodes root:{}, zxid:{}", nodeRootPath, nodeRootStat.getCzxid());

                    final Namespace.Builder namespaceBuilder = Namespace.newBuilder().setName(name)
                            .setVersion(namespaceStat.getVersion());
                    if (namespaceMetadata != null) {
                        namespaceBuilder.setPayload(toByteString(namespaceMetadata));
                    }
                    namespace = namespaceBuilder.build();

                    trackedNamespaces.add(name);
                } else {
                    logger.warn("Namespace already exists {}", namespacePath);
                }
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return namespace;
    }

    @Override
    public boolean purgeNamespace(final String namespace) throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        boolean success = false;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                final String namespacePath = "/" + namespace;
                logger.debug("Purging namespace {}", namespacePath);
                final List<String> childNodes = flattenTree(namespacePath);
                // start with leaves, work up from there
                for (int iter = childNodes.size() - 1; iter >= 0; iter--) {
                    final String path = childNodes.get(iter);
                    serverProxyZk.delete(path, -1);
                    logger.info("Deleted {}", path);
                }
                success = true;
                trackedNamespaces.remove(namespace);
                logger.info("Purged namespace {}", namespacePath);
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
            break;
        }
        case CURATOR: {
            try {
                final String namespacePath = "/" + namespace;
                logger.debug("Purging namespace {}", namespacePath);
                serverProxyCurator.delete().deletingChildrenIfNeeded().forPath(namespacePath);
                success = true;
                trackedNamespaces.remove(namespace);
                logger.info("Purged namespace {}", namespacePath);
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return success;
    }

    @Override
    public boolean newCohortType(final String namespace, final CohortType cohortType, final byte[] cohortTypeMetadata)
            throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        boolean success = false;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                // final CohortType cohortType = request.getCohortType();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                String cohortTypePath = cohortRootPath + "/" + cohortType;

                if (serverProxyZk.exists(cohortTypePath, false) == null) {
                    logger.debug("Creating cohort type {}", cohortTypePath);
                    final Stat cohortTypeStat = new Stat();
                    cohortTypePath = serverProxyZk.create(cohortTypePath, cohortTypeMetadata,
                            ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, cohortTypeStat);
                    success = true;
                    logger.debug("cohort type:{}, stat:{}", cohortTypePath, cohortTypeStat);
                    logger.info("Created cohort type:{}, zxid:{}", cohortTypePath, cohortTypeStat.getCzxid());
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
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                // final CohortType cohortType = request.getCohortType();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                String cohortTypePath = cohortRootPath + "/" + cohortType;
                if (serverProxyCurator.checkExists().forPath(cohortTypePath) == null) {
                    logger.debug("Creating cohort type {}", cohortTypePath);
                    final Stat cohortTypeStat = new Stat();
                    cohortTypePath = serverProxyCurator.create().storingStatIn(cohortTypeStat)
                            .withMode(CreateMode.PERSISTENT).forPath(cohortTypePath, cohortTypeMetadata);
                    success = true;
                    logger.debug("cohort type:{}, stat:{}", cohortTypePath, cohortTypeStat);
                    logger.info("Created cohort type:{}, zxid:{}", cohortTypePath, cohortTypeStat.getCzxid());
                } else {
                    logger.warn("Cohort type tree already exsts {}", cohortTypePath);
                }
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return success;
    }

    @Override
    public Cohort newCohort(final String namespace, final String cohortId, final CohortType cohortType,
            final byte[] cohortMetadata) throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        Cohort cohort = null;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                // final String cohortId = request.getCohortId();
                // final CohortType cohortType = request.getCohortType();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortTypePath = cohortRootPath + "/" + cohortType;
                if (serverProxyZk.exists(cohortTypePath, false) == null) {
                    logger.warn("Failed to locate cohortType tree {}", cohortTypePath);
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE);
                }
                String cohortChildPath = cohortTypePath + "/" + cohortId;
                if (serverProxyZk.exists(cohortChildPath, false) == null) {
                    logger.debug("Creating cohort {}", cohortChildPath);
                    final Stat cohortStat = new Stat();
                    cohortChildPath = serverProxyZk.create(cohortChildPath, cohortMetadata, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT, cohortStat);
                    logger.debug("cohort:{}, stat:{}", cohortChildPath, cohortStat);
                    logger.info("Created cohort:{}, zxid:{}", cohortChildPath, cohortStat.getCzxid());

                    // for debugging
                    // if (serverProxy.exists(cohortChildPath, false) == null) {
                    // logger.warn("Failed to create cohort {}",
                    // cohortChildPath);
                    // }

                    String membersChildPath = cohortChildPath + "/members";
                    logger.debug("Creating members root {}", membersChildPath);
                    final Stat membersChildStat = new Stat();
                    membersChildPath = serverProxyZk.create(membersChildPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT, membersChildStat);
                    logger.debug("members root:{}, stat:{}", membersChildPath, membersChildStat);
                    logger.info("Created members root:{}, zxid:{}", membersChildPath, membersChildStat.getCzxid());

                    final Cohort.Builder cohortBuilder = Cohort.newBuilder().setType(cohortType).setId(cohortId)
                            .setPath(cohortChildPath).setVersion(cohortStat.getVersion());
                    if (cohortMetadata != null) {
                        cohortBuilder.setPayload(toByteString(cohortMetadata));
                    }
                    cohort = cohortBuilder.build();

                    // for debugging
                    // if (serverProxy.exists(membersChildPath, false) == null)
                    // {
                    // logger.warn("Failed to create members root {}",
                    // membersChildPath);
                    // }

                    final Watcher membershipChangedWatcher = new Watcher() {
                        @Override
                        public void process(final WatchedEvent watchedEvent) {
                            logger.debug("Membership changed, {}", watchedEvent);
                            switch (watchedEvent.getType()) {
                            case NodeCreated:
                                logger.info("Member added, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            case NodeDeleted:
                                logger.info("Member left or died, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            case NodeDataChanged:
                                logger.info("Member data changed, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            case NodeChildrenChanged:
                                logger.info("Membership changed, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            default:
                                logger.info("Membership change triggered, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent);
                                break;
                            }
                            List<Member> updatedMembers = null;
                            try {
                                final Cohort updatedCohort = describeCohort(namespace, cohortId, cohortType);
                                if (updatedCohort != null) {
                                    updatedMembers = updatedCohort.getMembersList();
                                    if (updatedMembers != null) {
                                        logger.info("Updated members:[{}]", updatedMembers);
                                    }
                                }
                            } catch (final MembershipServerException problem) {
                                switch (problem.getCode()) {
                                case INVALID_MEMBERSHIP_LCM:
                                    break;
                                default:
                                    logger.error(
                                            String.format("Problem encountered while trying to describe cohortId:%s",
                                                    cohortId),
                                            problem);
                                    break;
                                }
                            }
                        }
                    };
                    serverProxyZk.getChildren(membersChildPath, membershipChangedWatcher);
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
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                // final String cohortId = request.getCohortId();
                // final CohortType cohortType = request.getCohortType();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortTypePath = cohortRootPath + "/" + cohortType;
                if (serverProxyCurator.checkExists().forPath(cohortTypePath) == null) {
                    logger.warn("Failed to locate cohortType tree {}", cohortTypePath);
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE);
                }
                String cohortChildPath = cohortTypePath + "/" + cohortId;
                if (serverProxyCurator.checkExists().forPath(cohortChildPath) == null) {
                    logger.debug("Creating cohort {}", cohortChildPath);
                    final Stat cohortStat = new Stat();
                    cohortChildPath = serverProxyCurator.create().storingStatIn(cohortStat)
                            .withMode(CreateMode.PERSISTENT).forPath(cohortChildPath, cohortMetadata);
                    logger.debug("cohort:{}, stat:{}", cohortChildPath, cohortStat);
                    logger.info("Created cohort:{}, zxid:{}", cohortChildPath, cohortStat.getCzxid());

                    // for debugging
                    // if (serverProxy.exists(cohortChildPath, false) == null) {
                    // logger.warn("Failed to create cohort {}",
                    // cohortChildPath);
                    // }

                    String membersChildPath = cohortChildPath + "/members";
                    logger.debug("Creating members root {}", membersChildPath);
                    final Stat membersChildStat = new Stat();
                    membersChildPath = serverProxyCurator.create().storingStatIn(membersChildStat)
                            .withMode(CreateMode.PERSISTENT).forPath(membersChildPath);
                    logger.debug("members root:{}, stat:{}", membersChildPath, membersChildStat);
                    logger.info("Created members root:{}, zxid:{}", membersChildPath, membersChildStat.getCzxid());

                    final Cohort.Builder cohortBuilder = Cohort.newBuilder().setType(cohortType).setId(cohortId)
                            .setPath(cohortChildPath).setVersion(cohortStat.getVersion());
                    if (cohortMetadata != null) {
                        cohortBuilder.setPayload(toByteString(cohortMetadata));
                    }
                    cohort = cohortBuilder.build();

                    // for debugging
                    // if (serverProxy.exists(membersChildPath, false) == null)
                    // {
                    // logger.warn("Failed to create members root {}",
                    // membersChildPath);
                    // }

                    final CuratorWatcher membershipChangedWatcher = new CuratorWatcher() {
                        @Override
                        public void process(final WatchedEvent watchedEvent) {
                            logger.info("Membership changed, {}", watchedEvent);
                            switch (watchedEvent.getType()) {
                            case NodeCreated:
                                logger.info("Member added, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            case NodeDeleted:
                                logger.info("Member left or died, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            case NodeDataChanged:
                                logger.info("Member data changed, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            case NodeChildrenChanged:
                                logger.info("Membership changed, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            default:
                                logger.info("Membership change triggered, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent);
                                break;
                            }
                            List<Member> updatedMembers = null;
                            try {
                                final Cohort updatedCohort = describeCohort(namespace, cohortId, cohortType);
                                if (updatedCohort != null) {
                                    updatedMembers = updatedCohort.getMembersList();
                                    if (updatedMembers != null) {
                                        logger.info("Updated members:[{}]", updatedMembers);
                                    }
                                }
                            } catch (final MembershipServerException problem) {
                                switch (problem.getCode()) {
                                case INVALID_MEMBERSHIP_LCM:
                                    break;
                                default:
                                    logger.error(
                                            String.format("Problem encountered while trying to describe cohortId:%s",
                                                    cohortId),
                                            problem);
                                    break;
                                }
                            }
                        }
                    };
                    serverProxyCurator.getChildren().usingWatcher(membershipChangedWatcher).forPath(membersChildPath);
                } else {
                    logger.warn("Failed to locate cohort child tree {}", cohortChildPath);
                }
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return cohort;
    }

    @Override
    public List<Node> listNodes(final String namespace) throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        final List<Node> nodes = new ArrayList<>();
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                final String nodeRootPath = "/" + namespace + "/nodes";
                logger.debug("List nodes under: {}", nodeRootPath);
                final Stat nodeRootStat = new Stat();
                List<String> nodeIds = null;
                if (serverProxyZk.exists(nodeRootPath, false) != null) {
                    nodeIds = serverProxyZk.getChildren(nodeRootPath, false, nodeRootStat);
                    logger.debug("node root:{}, stat:{}", nodeRootPath, nodeRootStat);
                    for (final String nodeId : nodeIds) {
                        final String nodeIdPath = nodeRootPath + "/" + nodeId;
                        if (serverProxyZk.exists(nodeIdPath, false) != null) {
                            final Node.Builder nodeBuilder = Node.newBuilder().setId(nodeId).setPath(nodeIdPath);
                            final Stat nodeIdStat = new Stat();
                            final byte[] nodePayload = serverProxyZk.getData(nodeIdPath, false, nodeIdStat);
                            if (nodePayload != null) {
                                nodeBuilder.setPayload(toByteString(nodePayload));
                            }
                            nodeBuilder.setVersion(nodeIdStat.getVersion());
                            final Node node = nodeBuilder.build();
                            nodes.add(node);
                        }
                    }
                }
            } catch (final KeeperException keeperException) {
                logger.error("Problem encountered during listNodes for namespace:" + namespace, keeperException);
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
            break;
        }
        case CURATOR: {
            try {
                final String nodeRootPath = "/" + namespace + "/nodes";
                logger.debug("List nodes under: {}", nodeRootPath);
                final Stat nodeRootStat = new Stat();
                List<String> nodeIds = null;
                if (serverProxyCurator.checkExists().forPath(nodeRootPath) != null) {
                    nodeIds = serverProxyCurator.getChildren().storingStatIn(nodeRootStat).forPath(nodeRootPath);
                    logger.debug("node root:{}, stat:{}", nodeRootPath, nodeRootStat);
                    for (final String nodeId : nodeIds) {
                        final String nodeIdPath = nodeRootPath + "/" + nodeId;
                        if (serverProxyCurator.checkExists().forPath(nodeIdPath) != null) {
                            final Node.Builder nodeBuilder = Node.newBuilder().setId(nodeId).setPath(nodeIdPath);
                            final Stat nodeIdStat = new Stat();
                            final byte[] nodePayload = serverProxyCurator.getData().storingStatIn(nodeIdStat)
                                    .forPath(nodeIdPath);
                            if (nodePayload != null) {
                                nodeBuilder.setPayload(toByteString(nodePayload));
                            }
                            nodeBuilder.setVersion(nodeIdStat.getVersion());
                            final Node node = nodeBuilder.build();
                            nodes.add(node);
                        }
                    }
                }
            } catch (final Exception curatorException) {
                logger.error("Problem encountered during listNodes for namespace:" + namespace, curatorException);
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return nodes;
    }

    @Override
    public Node newNode(final String namespace, final String nodeId, final byte[] nodeMetadata)
            throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        Node node = null;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                // final String nodeId = request.getNodeId();
                // final String address = request.getAddress();
                final String nodeRootPath = "/" + namespace + "/nodes";
                String nodeIdPath = nodeRootPath + "/" + nodeId;
                if (serverProxyZk.exists(nodeIdPath, false) == null) {
                    logger.debug("Creating node {}", nodeIdPath);
                    final Stat nodeStat = new Stat();
                    nodeIdPath = serverProxyZk.create(nodeIdPath, nodeMetadata, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.EPHEMERAL, nodeStat);
                    logger.debug("node:{}, stat:{}", nodeIdPath, nodeStat);

                    final Node.Builder nodeBuilder = Node.newBuilder().setId(nodeId).setPath(nodeIdPath);
                    if (nodeMetadata != null) {
                        nodeBuilder.setPayload(toByteString(nodeMetadata));
                    }
                    nodeBuilder.setVersion(nodeStat.getVersion());
                    node = nodeBuilder.build();
                    logger.info("Created node {}, zxid:{}", node, nodeStat.getCzxid());

                    final Watcher nodeChangedWatcher = new Watcher() {
                        @Override
                        public void process(final WatchedEvent watchedEvent) {
                            logger.debug("Node changed, {}", watchedEvent);
                            if (watchedEvent.getType() != EventType.None) {
                                switch (watchedEvent.getType()) {
                                case NodeCreated:
                                    logger.info("Node added, sessionId:{}, {}", getServerSessionId(),
                                            watchedEvent.getPath());
                                    break;
                                case NodeDeleted:
                                    logger.info("Node left or died, sessionId:{}, {}", getServerSessionId(),
                                            watchedEvent.getPath());
                                    break;
                                case NodeDataChanged:
                                    logger.info("Node data changed, sessionId:{}, {}", getServerSessionId(),
                                            watchedEvent.getPath());
                                    break;
                                case NodeChildrenChanged:
                                    logger.info("Node changed, sessionId:{}, {}", getServerSessionId(),
                                            watchedEvent.getPath());
                                    break;
                                default:
                                    logger.info("Node change triggered, sessionId:{}, {}", getServerSessionId(),
                                            watchedEvent);
                                    break;
                                }
                                List<Node> updatedNodes = null;
                                try {
                                    updatedNodes = listNodes(namespace);
                                    if (updatedNodes != null) {
                                        logger.info("Updated nodes:[{}]", updatedNodes);
                                    }
                                } catch (final MembershipServerException problem) {
                                    switch (problem.getCode()) {
                                    case INVALID_MEMBERSHIP_LCM:
                                        break;
                                    default:
                                        logger.error(String.format(
                                                "Problem encountered while trying to list nodes for namespace:%s",
                                                namespace), problem);
                                        break;
                                    }
                                }
                            }
                        }
                    };
                    serverProxyZk.getChildren(nodeRootPath, nodeChangedWatcher);
                } else {
                    logger.warn("Failed to locate node tree {}", nodeIdPath);
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
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                // final String nodeId = request.getNodeId();
                // final String address = request.getAddress();
                final String nodeRootPath = "/" + namespace + "/nodes";
                String nodeIdPath = nodeRootPath + "/" + nodeId;
                if (serverProxyCurator.checkExists().forPath(nodeIdPath) == null) {
                    logger.debug("Creating node {}", nodeIdPath);
                    final Stat nodeStat = new Stat();
                    nodeIdPath = serverProxyCurator.create().storingStatIn(nodeStat).withMode(CreateMode.EPHEMERAL)
                            .forPath(nodeIdPath, nodeMetadata);
                    logger.debug("node:{}, stat:{}", nodeIdPath, nodeStat);

                    final Node.Builder nodeBuilder = Node.newBuilder().setId(nodeId).setPath(nodeIdPath);
                    if (nodeMetadata != null) {
                        nodeBuilder.setPayload(toByteString(nodeMetadata));
                    }
                    nodeBuilder.setVersion(nodeStat.getVersion());
                    node = nodeBuilder.build();
                    logger.info("Created node {}, zxid:{}", node, nodeStat.getCzxid());

                    final CuratorWatcher nodeChangedWatcher = new CuratorWatcher() {
                        @Override
                        public void process(final WatchedEvent watchedEvent) {
                            logger.info("Node changed, {}", watchedEvent);
                            if (watchedEvent.getType() != EventType.None) {
                                switch (watchedEvent.getType()) {
                                case NodeCreated:
                                    logger.info("Node added, sessionId:{}, {}", getServerSessionId(),
                                            watchedEvent.getPath());
                                    break;
                                case NodeDeleted:
                                    logger.info("Node left or died, sessionId:{}, {}", getServerSessionId(),
                                            watchedEvent.getPath());
                                    break;
                                case NodeDataChanged:
                                    logger.info("Node data changed, sessionId:{}, {}", getServerSessionId(),
                                            watchedEvent.getPath());
                                    break;
                                case NodeChildrenChanged:
                                    logger.info("Node changed, sessionId:{}, {}", getServerSessionId(),
                                            watchedEvent.getPath());
                                    break;
                                default:
                                    logger.info("Node change triggered, sessionId:{}, {}", getServerSessionId(),
                                            watchedEvent);
                                    break;
                                }
                                List<Node> updatedNodes = null;
                                try {
                                    updatedNodes = listNodes(namespace);
                                    if (updatedNodes != null) {
                                        logger.info("Updated nodes:[{}]", updatedNodes);
                                    }
                                } catch (final MembershipServerException problem) {
                                    switch (problem.getCode()) {
                                    case INVALID_MEMBERSHIP_LCM:
                                        break;
                                    default:
                                        logger.error(String.format(
                                                "Problem encountered while trying to list nodes for namespace:%s",
                                                namespace), problem);
                                        break;
                                    }
                                }
                            }
                        }
                    };
                    serverProxyCurator.getChildren().usingWatcher(nodeChangedWatcher).forPath(nodeRootPath);
                } else {
                    logger.warn("Failed to locate node tree {}", nodeIdPath);
                }
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return node;
    }

    @Override
    public List<Cohort> listCohorts(final String namespace) throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        final List<Cohort> cohorts = new ArrayList<>();
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                logger.debug("List cohorts under {}", cohortRootPath);
                final Stat cohortRootStat = new Stat();
                List<String> cohortTypes = null;
                if (serverProxyZk.exists(cohortRootPath, false) != null) {
                    cohortTypes = serverProxyZk.getChildren(cohortRootPath, false, cohortRootStat);
                    logger.debug("cohort root:{}, stat:{}", cohortRootPath, cohortRootStat);
                    for (final String cohortType : cohortTypes) {
                        final String cohortTypePath = cohortRootPath + "/" + cohortType;
                        final Stat cohortTypeStat = new Stat();
                        final List<String> cohortIds = serverProxyZk.getChildren(cohortTypePath, false, cohortTypeStat);
                        logger.debug("cohort type:{}, stat:{}", cohortTypePath, cohortTypeStat);
                        for (final String cohortId : cohortIds) {
                            final Cohort cohort = describeCohort(namespace, cohortId, cohortTypeFromString(cohortType));
                            cohorts.add(cohort);
                        }
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
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                logger.debug("List cohorts under {}", cohortRootPath);
                final Stat cohortRootStat = new Stat();
                List<String> cohortTypes = null;
                if (serverProxyCurator.checkExists().forPath(cohortRootPath) != null) {
                    cohortTypes = serverProxyCurator.getChildren().storingStatIn(cohortRootStat)
                            .forPath(cohortRootPath);
                    logger.debug("cohort root:{}, stat:{}", cohortRootPath, cohortRootStat);
                    for (final String cohortType : cohortTypes) {
                        final String cohortTypePath = cohortRootPath + "/" + cohortType;
                        final Stat cohortTypeStat = new Stat();
                        final List<String> cohortIds = serverProxyCurator.getChildren().storingStatIn(cohortTypeStat)
                                .forPath(cohortTypePath);
                        logger.debug("cohort type:{}, stat:{}", cohortTypePath, cohortTypeStat);
                        for (final String cohortId : cohortIds) {
                            try {
                                final Cohort cohort = describeCohort(namespace, cohortId,
                                        cohortTypeFromString(cohortType));
                                cohorts.add(cohort);
                            } catch (Exception problem) {
                                logger.error("Problem describing cohortId:" + cohortId, problem);
                            }
                        }
                    }
                }
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }

        return cohorts;
    }

    @Override
    public Cohort joinCohort(final String namespace, final String memberId, final String cohortId,
            final CohortType cohortType, final String nodeId, final byte[] memberMetadata)
            throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }

        Cohort cohort = null;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                // final String memberId = request.getMemberId();
                // final String cohortId = request.getCohortId();
                // final CohortType cohortType = request.getCohortType();
                // final String nodeId = request.getNodeId();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
                if (serverProxyZk.exists(cohortMembersPath, false) == null) {
                    final String warning = "Failed to locate member tree " + cohortMembersPath;
                    logger.warn(warning);
                    throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
                }

                String memberChildPath = cohortMembersPath + "/" + memberId;
                logger.debug("Creating member {}", memberChildPath);
                final Stat memberStat = new Stat();
                memberChildPath = serverProxyZk.create(memberChildPath, memberMetadata, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.EPHEMERAL, memberStat);
                logger.debug("member:{}, stat:{}", memberChildPath, memberStat);

                final Member.Builder memberBuilder = Member.newBuilder().setMemberId(memberId).setCohortType(cohortType)
                        .setCohortId(cohortId).setNodeId(nodeId).setPath(memberChildPath);
                if (memberMetadata != null) {
                    memberBuilder.setPayload(toByteString(memberMetadata));
                }
                memberBuilder.setVersion(memberStat.getVersion());
                final Member member = memberBuilder.build();
                logger.info("Created member {}, zxid:{}", member, memberStat.getCzxid());

                final Watcher membershipChangedWatcher = new Watcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        // logger.info("Membership changed, {}", watchedEvent);
                        switch (watchedEvent.getType()) {
                        case NodeCreated:
                            logger.info("Member added, sessionId:{}, {}", getServerSessionId(), watchedEvent.getPath());
                            break;
                        case NodeDeleted:
                            logger.info("Member left or died, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        case NodeDataChanged:
                            logger.info("Member data changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        case NodeChildrenChanged:
                            logger.info("Membership changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        default:
                            logger.info("Membership change triggered, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent);
                            break;
                        }
                    }
                };
                // serverProxyZk.exists(memberChildPath,
                // membershipChangedWatcher);
                // serverProxy.getChildren(cohortMembersPath,
                // membershipChangedWatcher);

                cohort = describeCohort(namespace, cohortId, cohortType);
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
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                // final String memberId = request.getMemberId();
                // final String cohortId = request.getCohortId();
                // final CohortType cohortType = request.getCohortType();
                // final String nodeId = request.getNodeId();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
                if (serverProxyCurator.checkExists().forPath(cohortMembersPath) == null) {
                    final String warning = "Failed to locate member tree " + cohortMembersPath;
                    logger.warn(warning);
                    throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
                }

                String memberChildPath = cohortMembersPath + "/" + memberId;
                logger.debug("Creating member {}", memberChildPath);
                final Stat memberStat = new Stat();
                memberChildPath = serverProxyCurator.create().storingStatIn(memberStat).withMode(CreateMode.EPHEMERAL)
                        .forPath(memberChildPath, memberMetadata);
                logger.debug("member:{}, stat:{}", memberChildPath, memberStat);

                final Member.Builder memberBuilder = Member.newBuilder().setMemberId(memberId).setCohortType(cohortType)
                        .setCohortId(cohortId).setNodeId(nodeId).setPath(memberChildPath);
                if (memberMetadata != null) {
                    memberBuilder.setPayload(toByteString(memberMetadata));
                }
                memberBuilder.setVersion(memberStat.getVersion());
                final Member member = memberBuilder.build();
                logger.info("Created member {}, zxid:{}", member, memberStat.getCzxid());

                final CuratorWatcher membershipChangedCuratorWatcher = new CuratorWatcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        // logger.info("Membership changed, {}", watchedEvent);
                        switch (watchedEvent.getType()) {
                        case NodeCreated:
                            logger.info("Member added, sessionId:{}, {}", getServerSessionId(), watchedEvent.getPath());
                            break;
                        case NodeDeleted:
                            logger.info("Member left or died, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        case NodeDataChanged:
                            logger.info("Member data changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        case NodeChildrenChanged:
                            logger.info("Membership changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        default:
                            logger.info("Membership change triggered, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent);
                            break;
                        }
                    }
                };
                // serverProxyCurator.checkExists().usingWatcher(membershipChangedWatcher).forPath(memberChildPath);
                // serverProxyCurator.getChildren().usingWatcher(membershipChangedCuratorWatcher).forPath(cohortMembersPath);

                final Watcher membershipChangedWatcher = new Watcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        // logger.info("Membership changed, {}", watchedEvent);
                        switch (watchedEvent.getType()) {
                        case NodeCreated:
                            logger.info("Member added, sessionId:{}, {}", getServerSessionId(), watchedEvent.getPath());
                            break;
                        case NodeDeleted:
                            logger.info("Member left or died, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        case NodeDataChanged:
                            logger.info("Member data changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        case NodeChildrenChanged:
                            logger.info("Membership changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        default:
                            logger.info("Membership change triggered, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent);
                            break;
                        }
                    }
                };

                // final PersistentWatcher membershipEditWatcher = new
                // PersistentWatcher(serverProxyCurator, memberChildPath, true);
                // membershipEditWatcher.start();
                // membershipEditWatcher.getListenable().addListener(membershipChangedWatcher);

                cohort = describeCohort(namespace, cohortId, cohortType);
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return cohort;
    }

    @Override
    public Cohort describeCohort(final String namespace, final String cohortId, final CohortType cohortType)
            throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }

        Cohort cohort = null;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                // final String cohortId = request.getCohortId();
                // final CohortType cohortType = request.getCohortType();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortIdPath = cohortRootPath + "/" + cohortType + "/" + cohortId;

                final String cohortMembersPath = cohortIdPath + "/members";
                final List<Member> members = new ArrayList<>();
                if (serverProxyZk.exists(cohortMembersPath, false) != null) {
                    final List<String> memberIds = serverProxyZk.getChildren(cohortMembersPath, false);
                    for (final String memberId : memberIds) {
                        final String memberIdPath = cohortMembersPath + "/" + memberId;
                        if (serverProxyZk.exists(memberIdPath, false) != null) {
                            final Member.Builder memberBuilder = Member.newBuilder().setCohortId(cohortId)
                                    .setCohortType(cohortType).setMemberId(memberId).setPath(memberIdPath);
                            final Stat memberIdStat = new Stat();
                            final byte[] memberPayload = serverProxyZk.getData(memberIdPath, false, memberIdStat);
                            if (memberPayload != null) {
                                memberBuilder.setPayload(toByteString(memberPayload));
                            }
                            memberBuilder.setVersion(memberIdStat.getVersion());
                            final Member member = memberBuilder.build();
                            // member.setNodeId(null); // TODO
                            members.add(member);
                        }
                    }
                } else {
                    final String warning = "Failed to locate member tree " + cohortMembersPath;
                    logger.warn(warning);
                    return cohort;
                }
                final Cohort.Builder cohortBuilder = Cohort.newBuilder().setType(cohortType).addAllMembers(members)
                        .setId(cohortId).setPath(cohortIdPath);
                final Stat cohortStat = new Stat();
                final byte[] cohortPayload = serverProxyZk.getData(cohortIdPath, false, cohortStat);
                if (cohortPayload != null) {
                    cohortBuilder.setPayload(toByteString(cohortPayload));
                }
                cohortBuilder.setVersion(cohortStat.getVersion());
                cohort = cohortBuilder.build();
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
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                // final String cohortId = request.getCohortId();
                // final CohortType cohortType = request.getCohortType();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortIdPath = cohortRootPath + "/" + cohortType + "/" + cohortId;

                final String cohortMembersPath = cohortIdPath + "/members";
                final List<Member> members = new ArrayList<>();
                if (serverProxyCurator.checkExists().forPath(cohortMembersPath) != null) {
                    final List<String> memberIds = serverProxyCurator.getChildren().forPath(cohortMembersPath);
                    for (final String memberId : memberIds) {
                        final String memberIdPath = cohortMembersPath + "/" + memberId;
                        if (serverProxyCurator.checkExists().forPath(memberIdPath) != null) {
                            final Member.Builder memberBuilder = Member.newBuilder().setCohortId(cohortId)
                                    .setCohortType(cohortType).setMemberId(memberId).setPath(memberIdPath);
                            final Stat memberIdStat = new Stat();
                            final byte[] memberPayload = serverProxyCurator.getData().storingStatIn(memberIdStat)
                                    .forPath(memberIdPath);
                            if (memberPayload != null) {
                                memberBuilder.setPayload(toByteString(memberPayload));
                            }
                            memberBuilder.setVersion(memberIdStat.getVersion());
                            final Member member = memberBuilder.build();
                            // member.setNodeId(null); // TODO
                            members.add(member);
                        }
                    }
                } else {
                    final String warning = "Failed to locate member tree " + cohortMembersPath;
                    logger.warn(warning);
                    return cohort;
                }
                final Cohort.Builder cohortBuilder = Cohort.newBuilder().setType(cohortType).addAllMembers(members)
                        .setId(cohortId).setPath(cohortIdPath);
                final Stat cohortStat = new Stat();
                final byte[] cohortPayload = serverProxyCurator.getData().storingStatIn(cohortStat)
                        .forPath(cohortIdPath);
                if (cohortPayload != null) {
                    cohortBuilder.setPayload(toByteString(cohortPayload));
                }
                cohortBuilder.setVersion(cohortStat.getVersion());
                cohort = cohortBuilder.build();
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return cohort;
    }

    @Override
    public boolean leaveCohort(final String namespace, final String cohortId, final CohortType cohortType,
            final String memberId) throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        boolean success = false;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                // final String cohortId = request.getCohortId();
                // final CohortType cohortType = request.getCohortType();
                // final String memberId = request.getMemberId();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
                if (serverProxyZk.exists(cohortMembersPath, false) == null) {
                    final String warning = "Failed to locate member tree " + cohortMembersPath;
                    logger.warn(warning);
                    throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
                }
                serverProxyZk.delete(cohortMembersPath + "/" + memberId, -1);
                success = true;
                logger.info("{} left cohort {}", memberId, cohortMembersPath);
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
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                // final String cohortId = request.getCohortId();
                // final CohortType cohortType = request.getCohortType();
                // final String memberId = request.getMemberId();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
                if (serverProxyCurator.checkExists().forPath(cohortMembersPath) == null) {
                    final String warning = "Failed to locate member tree " + cohortMembersPath;
                    logger.warn(warning);
                    throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
                }
                serverProxyCurator.delete().forPath(cohortMembersPath + "/" + memberId);
                success = true;
                logger.info("{} left cohort {}", memberId, cohortMembersPath);
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return success;
    }

    @Override
    public boolean deleteCohort(final String namespace, final String cohortId, final CohortType cohortType,
            final int expectedVersion) throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        boolean success = false;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                // final String cohortId = request.getCohortId();
                // final CohortType cohortType = request.getCohortType();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortPath = cohortRootPath + "/" + cohortType + "/" + cohortId;
                if (serverProxyZk.exists(cohortPath, false) == null) {
                    final String warning = "Failed to locate cohort " + cohortPath;
                    logger.warn(warning);
                    throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
                }

                logger.debug("Deleting cohort {}", cohortPath);
                final List<String> childNodes = flattenTree(cohortPath);
                logger.debug(childNodes);

                // start with leaves, work up from there
                for (int iter = childNodes.size() - 1; iter >= 0; iter--) {
                    final String path = childNodes.get(iter);
                    serverProxyZk.delete(path, -1);
                    logger.info("Deleted {}", path);
                }
                success = true;
                logger.info("Deleted cohort {}", cohortPath);
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
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                // final String cohortId = request.getCohortId();
                // final CohortType cohortType = request.getCohortType();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortPath = cohortRootPath + "/" + cohortType + "/" + cohortId;
                if (serverProxyCurator.checkExists().forPath(cohortPath) == null) {
                    final String warning = "Failed to locate cohort " + cohortPath;
                    logger.warn(warning);
                    throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
                }

                logger.debug("Deleting cohort {}", cohortPath);
                serverProxyCurator.delete().deletingChildrenIfNeeded().withVersion(expectedVersion).forPath(cohortPath);
                success = true;
                logger.info("Deleted cohort {}", cohortPath);
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else if (curatorException instanceof KeeperException.BadVersionException) {
                    throw new MembershipServerException(Code.EXPECTED_VERSION_MISMATCH, curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return success;
    }

    @Override
    public boolean deleteCohortType(final String namespace, final CohortType cohortType)
            throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        boolean success = false;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                // final CohortType cohortType = request.getCohortType();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortTypePath = cohortRootPath + "/" + cohortType;
                if (serverProxyZk.exists(cohortTypePath, false) == null) {
                    final String warning = "Failed to locate cohort type " + cohortTypePath;
                    logger.warn(warning);
                    throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
                }

                logger.debug("Deleting cohortType {}", cohortTypePath);
                final List<String> childNodes = flattenTree(cohortTypePath);
                logger.debug(childNodes);

                // start with leaves, work up from there
                for (int iter = childNodes.size() - 1; iter >= 0; iter--) {
                    final String path = childNodes.get(iter);
                    serverProxyZk.delete(path, -1);
                    logger.info("Deleted {}", path);
                }
                success = true;
                logger.info("Deleted cohortType {}", cohortTypePath);
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
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                // final CohortType cohortType = request.getCohortType();
                final String cohortRootPath = "/" + namespace + "/cohorts";
                final String cohortTypePath = cohortRootPath + "/" + cohortType;
                if (serverProxyCurator.checkExists().forPath(cohortTypePath) == null) {
                    final String warning = "Failed to locate cohort type " + cohortTypePath;
                    logger.warn(warning);
                    throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
                }

                logger.debug("Deleting cohortType {}", cohortTypePath);
                serverProxyCurator.delete().deletingChildrenIfNeeded().forPath(cohortTypePath);
                success = true;
                logger.info("Deleted cohortType {}", cohortTypePath);
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return success;
    }

    @Override
    public boolean deleteNode(final String namespace, final String nodeId, final int expectedVersion)
            throws MembershipServerException {
        // logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }
        boolean success = false;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                // final String namespace = request.getNamespace();
                // final String nodeId = request.getNodeId();
                final String nodeRootPath = "/" + namespace + "/nodes";
                final String nodePath = nodeRootPath + "/" + nodeId;
                if (serverProxyZk.exists(nodePath, false) == null) {
                    final String warning = "Failed to locate node " + nodePath;
                    logger.warn(warning);
                    throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
                }

                serverProxyZk.delete(nodePath, expectedVersion);
                success = true;
                logger.info("Deleted node {}", nodePath);
            } catch (final KeeperException keeperException) {
                if (keeperException instanceof KeeperException.NodeExistsException) {
                    // TODO: node already exists
                } else if (keeperException instanceof KeeperException.BadVersionException) {
                    throw new MembershipServerException(Code.EXPECTED_VERSION_MISMATCH, keeperException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
                }
            } catch (final InterruptedException interruptedException) {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
            }
            break;
        }
        case CURATOR: {
            try {
                // final String namespace = request.getNamespace();
                // final String nodeId = request.getNodeId();
                final String nodeRootPath = "/" + namespace + "/nodes";
                final String nodePath = nodeRootPath + "/" + nodeId;
                final CuratorOp nodeExists = serverProxyCurator.transactionOp().check().forPath(nodePath);
                final CuratorOp nodeDelete = serverProxyCurator.transactionOp().delete().withVersion(expectedVersion)
                        .forPath(nodePath);
                final List<CuratorTransactionResult> transactionResults = serverProxyCurator.transaction()
                        .forOperations(nodeExists, nodeDelete);
                for (final CuratorTransactionResult result : transactionResults) {
                    switch (result.getType()) {
                    case CHECK: {
                        if (result.getError() != 0) {
                            final String warning = "Failed to locate node " + nodePath;
                            logger.warn(warning);
                            throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
                        }
                        break;
                    }
                    case DELETE: {
                        if (result.getError() != 0) {
                            logger.info("Failed to to delete node in namespace:%s, nodeId:%s", namespace, nodeId);
                        } else {
                            success = true;
                            logger.info("Deleted node {}", nodePath);
                        }
                        break;
                    }
                    default: {
                        break;
                    }
                    }
                }
                // if (serverProxyCurator.checkExists().forPath(nodePath) ==
                // null) {
                // final String warning = "Failed to locate node " + nodePath;
                // logger.warn(warning);
                // throw new
                // MembershipServerException(Code.PARENT_LOCATOR_FAILURE,
                // warning)// }
                // serverProxyCurator.delete().withVersion(expectedVersion).forPath(nodePath);
                // success = true;
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else if (curatorException instanceof KeeperException.BadVersionException) {
                    throw new MembershipServerException(Code.EXPECTED_VERSION_MISMATCH, curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return success;
    }

    @Override
    public boolean acquireLock(final String namespace, final String entity, final long waitSeconds)
            throws MembershipServerException {
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        boolean acquired = false;
        final String lockPath = "/" + namespace + "/" + entity;
        switch (mode) {
        case ZK_DIRECT: {
            // not implemented
            break;
        }
        case CURATOR: {
            try {
                final InterProcessLock lock = new InterProcessSemaphoreMutex(serverProxyCurator, lockPath);
                acquired = lock.acquire(waitSeconds, TimeUnit.SECONDS);
                if (acquired) {
                    trackedLocks.put(lockPath, lock);
                }
            } catch (final Exception curatorException) {
                logger.error(Code.LOCK_ACQUISITION_FAILURE.name(), curatorException);
                throw new MembershipServerException(Code.LOCK_ACQUISITION_FAILURE, curatorException);
            }
            break;
        }
        }
        logger.info("Acquire lock, entity:{}, acquired:{}", lockPath, acquired);
        return acquired;
    }

    @Override
    public boolean releaseLock(final String namespace, final String entity) throws MembershipServerException {
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        boolean released = false;
        final String lockPath = "/" + namespace + "/" + entity;
        switch (mode) {
        case ZK_DIRECT: {
            // not implemented
            break;
        }
        case CURATOR: {
            try {
                final InterProcessLock lock = trackedLocks.get(lockPath);
                if (lock != null) {
                    lock.release();
                    trackedLocks.remove(lockPath);
                    released = true;
                }
            } catch (final Exception curatorException) {
                logger.error(Code.LOCK_RELEASE_FAILURE.name(), curatorException);
                throw new MembershipServerException(Code.LOCK_RELEASE_FAILURE, curatorException);
            }
            break;
        }
        }
        logger.info("Release lock, entity:{}, released:{}", lockPath, released);
        return released;
    }

    @Override
    public void streamMembershipChanges(final String namespace, final String cohortId, final CohortType cohortType,
            final MembershipUpdateCallback membershipUpdateCallback) throws MembershipServerException {
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        final String membersChildPath = "/" + namespace + "/cohorts/" + cohortType + "/" + cohortId + "/members";
        switch (mode) {
        case ZK_DIRECT: {
            try {
                final Watcher membershipChangedWatcher = new Watcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        logger.debug("Membership changed, {}", watchedEvent);
                        switch (watchedEvent.getType()) {
                        case NodeCreated: {
                            logger.info("Member added, sessionId:{}, {}", getServerSessionId(), watchedEvent.getPath());
                            break;
                        }
                        case NodeDeleted: {
                            logger.info("Member left or died, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        case NodeDataChanged: {
                            logger.info("Member data changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        case NodeChildrenChanged: {
                            logger.info("Membership changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        default: {
                            logger.info("Membership change triggered, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent);
                            break;
                        }
                        }
                        List<Member> updatedMembers = null;
                        try {
                            final Cohort updatedCohort = describeCohort(namespace, cohortId, cohortType);
                            if (updatedCohort != null) {
                                updatedMembers = updatedCohort.getMembersList();
                                if (updatedMembers != null) {
                                    logger.info("Updated members:[{}]", updatedMembers);
                                    final MembershipUpdate update = MembershipUpdate.newBuilder()
                                            .setNamespace(namespace)
                                            .setUpdateType(MembershipUpdateType.UPDATED_COHORT_MEMBERS)
                                            .setCohortId(cohortId).setCohortType(cohortType)
                                            .addAllMembers(updatedMembers).build();
                                    membershipUpdateCallback.accept(update);
                                }
                            } else {
                                logger.info("Deleted cohort:{}", cohortId);
                                final MembershipUpdate update = MembershipUpdate.newBuilder().setNamespace(namespace)
                                        .setUpdateType(MembershipUpdateType.DELETED_COHORT).setCohortId(cohortId)
                                        .setCohortType(cohortType).build();
                                membershipUpdateCallback.accept(update);
                            }
                        } catch (final MembershipServerException problem) {
                            switch (problem.getCode()) {
                            case INVALID_MEMBERSHIP_LCM: {
                                break;
                            }
                            default: {
                                logger.error(String.format("Problem encountered while trying to describe cohortId:%s",
                                        cohortId), problem);
                                break;
                            }
                            }
                        }
                    }
                };
                serverProxyZk.getChildren(membersChildPath, membershipChangedWatcher);
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
            break;
        }
        case CURATOR: {
            try {
                final CuratorWatcher membershipChangedWatcher = new CuratorWatcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        logger.info("Membership changed, {}", watchedEvent);
                        switch (watchedEvent.getType()) {
                        case NodeCreated: {
                            logger.info("Member added, sessionId:{}, {}", getServerSessionId(), watchedEvent.getPath());
                            break;
                        }
                        case NodeDeleted: {
                            logger.info("Member left or died, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        case NodeDataChanged: {
                            logger.info("Member data changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        case NodeChildrenChanged: {
                            logger.info("Membership changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        default: {
                            logger.info("Membership change triggered, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent);
                            break;
                        }
                        }
                        List<Member> updatedMembers = null;
                        try {
                            final Cohort updatedCohort = describeCohort(namespace, cohortId, cohortType);
                            if (updatedCohort != null) {
                                updatedMembers = updatedCohort.getMembersList();
                                if (updatedMembers != null) {
                                    logger.info("Updated members:[{}]", updatedMembers);
                                    final MembershipUpdate update = MembershipUpdate.newBuilder()
                                            .setNamespace(namespace)
                                            .setUpdateType(MembershipUpdateType.UPDATED_COHORT_MEMBERS)
                                            .setCohortId(cohortId).setCohortType(cohortType)
                                            .addAllMembers(updatedMembers).build();
                                    membershipUpdateCallback.accept(update);
                                }
                            } else {
                                logger.info("Deleted cohort:{}", cohortId);
                                final MembershipUpdate update = MembershipUpdate.newBuilder().setNamespace(namespace)
                                        .setUpdateType(MembershipUpdateType.DELETED_COHORT).setCohortId(cohortId)
                                        .setCohortType(cohortType).build();
                                membershipUpdateCallback.accept(update);
                            }
                        } catch (final MembershipServerException problem) {
                            switch (problem.getCode()) {
                            case INVALID_MEMBERSHIP_LCM: {
                                break;
                            }
                            default: {
                                logger.error(String.format("Problem encountered while trying to describe cohortId:%s",
                                        cohortId), problem);
                                break;
                            }
                            }
                        }
                    }
                };
                serverProxyCurator.getChildren().usingWatcher(membershipChangedWatcher).forPath(membersChildPath);
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
    }

    @Override
    public void streamNodeChanges(final String namespace, final NodeUpdateCallback nodeUpdateCallback)
            throws MembershipServerException {
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        switch (mode) {
        case ZK_DIRECT: {
            try {
                final String nodeRootPath = "/" + namespace + "/nodes";
                final Watcher nodeChangedWatcher = new Watcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        logger.debug("Node changed, {}", watchedEvent);
                        if (watchedEvent.getType() != EventType.None) {
                            switch (watchedEvent.getType()) {
                            case NodeCreated: {
                                logger.info("Node added, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            }
                            case NodeDeleted: {
                                logger.info("Node left or died, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            }
                            case NodeDataChanged: {
                                logger.info("Node data changed, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            }
                            case NodeChildrenChanged: {
                                logger.info("Node changed, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            }
                            default: {
                                logger.info("Node change triggered, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent);
                                break;
                            }
                            }
                            List<Node> updatedNodes = null;
                            try {
                                updatedNodes = listNodes(namespace);
                                if (updatedNodes != null) {
                                    final NodeUpdate update = NodeUpdate.newBuilder()
                                            .setUpdateType(NodeUpdateType.UPDATED_NODE).setNamespace(namespace)
                                            .addAllNodes(updatedNodes).build();
                                    nodeUpdateCallback.accept(update);
                                    logger.info("Updated nodes:[{}]", updatedNodes);
                                } else {
                                    final NodeUpdate update = NodeUpdate.newBuilder()
                                            .setUpdateType(NodeUpdateType.DELETED_NODE).setNamespace(namespace).build();
                                    nodeUpdateCallback.accept(update);
                                    logger.info("Deleted nodes");
                                }
                            } catch (final MembershipServerException problem) {
                                switch (problem.getCode()) {
                                case INVALID_MEMBERSHIP_LCM: {
                                    break;
                                }
                                default: {
                                    logger.error(String.format(
                                            "Problem encountered while trying to list nodes for namespace:%s",
                                            namespace), problem);
                                    break;
                                }
                                }
                            }
                        }
                    }
                };
                serverProxyZk.getChildren(nodeRootPath, nodeChangedWatcher);
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
            break;
        }
        case CURATOR: {
            try {
                final String nodeRootPath = "/" + namespace + "/nodes";
                final CuratorWatcher nodeChangedWatcher = new CuratorWatcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        logger.info("Node changed, {}", watchedEvent);
                        if (watchedEvent.getType() != EventType.None) {
                            switch (watchedEvent.getType()) {
                            case NodeCreated: {
                                logger.info("Node added, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            }
                            case NodeDeleted: {
                                logger.info("Node left or died, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            }
                            case NodeDataChanged: {
                                logger.info("Node data changed, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            }
                            case NodeChildrenChanged: {
                                logger.info("Node changed, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent.getPath());
                                break;
                            }
                            default: {
                                logger.info("Node change triggered, sessionId:{}, {}", getServerSessionId(),
                                        watchedEvent);
                                break;
                            }
                            }
                            List<Node> updatedNodes = null;
                            try {
                                updatedNodes = listNodes(namespace);
                                if (updatedNodes != null) {
                                    final NodeUpdate update = NodeUpdate.newBuilder()
                                            .setUpdateType(NodeUpdateType.UPDATED_NODE).setNamespace(namespace)
                                            .addAllNodes(updatedNodes).build();
                                    nodeUpdateCallback.accept(update);
                                    logger.info("Updated nodes:[{}]", updatedNodes);
                                } else {
                                    final NodeUpdate update = NodeUpdate.newBuilder()
                                            .setUpdateType(NodeUpdateType.DELETED_NODE).setNamespace(namespace).build();
                                    nodeUpdateCallback.accept(update);
                                    logger.info("Deleted nodes");
                                }
                            } catch (final MembershipServerException problem) {
                                switch (problem.getCode()) {
                                case INVALID_MEMBERSHIP_LCM: {
                                    break;
                                }
                                default: {
                                    logger.error(String.format(
                                            "Problem encountered while trying to list nodes for namespace:%s",
                                            namespace), problem);
                                    break;
                                }
                                }
                            }
                        }
                    }
                };
                serverProxyCurator.getChildren().usingWatcher(nodeChangedWatcher).forPath(nodeRootPath);
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
    }

    @Override
    public Cohort updateCohort(final String namespace, final String cohortId, final CohortType cohortType,
            final byte[] cohortMetadata, final int expectedVersion) throws MembershipServerException {
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE,
        // request.toString());
        // }

        final String cohortIdPath = "/" + namespace + "/cohorts/" + cohortType + "/" + cohortId;
        Cohort cohort = null;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                if (serverProxyZk.exists(cohortIdPath, false) != null) {
                    serverProxyZk.setData(cohortIdPath, cohortMetadata, expectedVersion);
                    // final int cohortDataVersion = cohortStat.getVersion();

                    cohort = describeCohort(namespace, cohortId, cohortType);
                } else {
                    logger.info("Failed to locate cohort to update for namespace:%s, cohortId:%s, cohortType:%s",
                            namespace, cohortId, cohortType);
                }
            } catch (final KeeperException keeperException) {
                if (keeperException instanceof KeeperException.NodeExistsException) {
                    // node already exists
                } else if (keeperException instanceof KeeperException.BadVersionException) {
                    throw new MembershipServerException(Code.EXPECTED_VERSION_MISMATCH, keeperException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, keeperException);
                }
            } catch (final InterruptedException interruptedException) {
                // fix later
                throw new MembershipServerException(Code.UNKNOWN_FAILURE, interruptedException);
            }
            break;
        }
        case CURATOR: {
            try {
                final CuratorOp checkCohortExists = serverProxyCurator.transactionOp().check().forPath(cohortIdPath);
                final CuratorOp updateCohort = serverProxyCurator.transactionOp().setData().withVersion(expectedVersion)
                        .forPath(cohortIdPath, cohortMetadata);
                final List<CuratorTransactionResult> transactionResults = serverProxyCurator.transaction()
                        .forOperations(checkCohortExists, updateCohort);
                for (final CuratorTransactionResult result : transactionResults) {
                    switch (result.getType()) {
                    case CHECK: {
                        if (result.getError() != 0) {
                            logger.info(
                                    "Failed to locate cohort to update for namespace:%s, cohortId:%s, cohortType:%s",
                                    namespace, cohortId, cohortType);
                        }
                        break;
                    }
                    case SET_DATA: {
                        if (result.getError() != 0) {
                            logger.info("Failed to to update cohort in namespace:%s, cohortId:%s, cohortType:%s",
                                    namespace, cohortId, cohortType);
                        } else {
                            cohort = describeCohort(namespace, cohortId, cohortType);
                        }
                        break;
                    }
                    default: {
                        break;
                    }
                    }
                }

                // if (serverProxyCurator.checkExists().forPath(cohortIdPath) !=
                // null) {
                // serverProxyCurator.setData().withVersion(expectedVersion).forPath(cohortIdPath,
                // cohortMetadata);
                // final int cohortDataVersion = cohortStat.getVersion();

                // cohort = describeCohort(namespace, cohortId, cohortType);
                // } else {
                // logger.info("Failed to locate cohort to update for
                // namespace:%s, cohortId:%s,
                // cohortType:%s",
                // namespace, cohortId, cohortType);
                // }
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else if (curatorException instanceof KeeperException.BadVersionException) {
                    // throw new
                    // MembershipServerException(Code.EXPECTED_VERSION_MISMATCH,
                    // curatorException);
                    logger.info("Membership service observed an expected version mismatch");
                    cohort = describeCohort(namespace, cohortId, cohortType);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
        return cohort;
    }

    @Override
    public void streamCohortChanges(final String namespace, final String cohortId, final CohortType cohortType,
            final CohortUpdateCallback cohortUpdateCallback) throws MembershipServerException {
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        final String cohortIdPath = "/" + namespace + "/cohorts/" + cohortType + "/" + cohortId;
        switch (mode) {
        case ZK_DIRECT: {
            try {
                final Watcher cohortChangedWatcher = new Watcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        logger.debug("Cohort changed, {}", watchedEvent);
                        switch (watchedEvent.getType()) {
                        case NodeCreated: {
                            logger.info("Cohort created, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        case NodeDeleted: {
                            logger.info("Cohort left or died, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        case NodeDataChanged: {
                            logger.info("Cohort data changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        case NodeChildrenChanged: {
                            logger.info("Cohort changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        default: {
                            logger.info("Cohort change triggered, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent);
                            break;
                        }
                        }
                        try {
                            final Cohort updatedCohort = describeCohort(namespace, cohortId, cohortType);
                            if (updatedCohort != null) {
                                logger.info("Updated cohort:{}", updatedCohort);
                                final CohortUpdate update = CohortUpdate.newBuilder().setNamespace(namespace)
                                        .setUpdateType(CohortUpdateType.UPDATED_COHORT).setCohort(updatedCohort)
                                        .setCohortId(cohortId).setCohortType(cohortType).build();
                                cohortUpdateCallback.accept(update);
                            } else {
                                logger.info("Deleted cohort:{}", cohortId);
                                final CohortUpdate update = CohortUpdate.newBuilder().setNamespace(namespace)
                                        .setUpdateType(CohortUpdateType.UPDATED_COHORT).setCohortId(cohortId)
                                        .setCohortType(cohortType).build();
                                cohortUpdateCallback.accept(update);
                            }
                        } catch (final MembershipServerException problem) {
                            switch (problem.getCode()) {
                            case INVALID_MEMBERSHIP_LCM: {
                                break;
                            }
                            default: {
                                logger.error(String.format("Problem encountered while trying to describe cohortId:%s",
                                        cohortId), problem);
                                break;
                            }
                            }
                        }
                    }
                };
                serverProxyZk.getData(cohortIdPath, cohortChangedWatcher, null);
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
            break;
        }
        case CURATOR: {
            try {
                final CuratorWatcher cohortChangedWatcher = new CuratorWatcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        logger.info("Cohort changed, {}", watchedEvent);
                        switch (watchedEvent.getType()) {
                        case NodeCreated: {
                            logger.info("Cohort created, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        case NodeDeleted: {
                            logger.info("Cohort left or died, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        case NodeDataChanged: {
                            logger.info("Cohort data changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        case NodeChildrenChanged: {
                            logger.info("Cohort changed, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent.getPath());
                            break;
                        }
                        default: {
                            logger.info("Cohort change triggered, sessionId:{}, {}", getServerSessionId(),
                                    watchedEvent);
                            break;
                        }
                        }
                        List<Member> updatedMembers = null;
                        try {
                            final Cohort updatedCohort = describeCohort(namespace, cohortId, cohortType);
                            if (updatedCohort != null) {
                                logger.info("Updated cohort:{}", updatedCohort);
                                final CohortUpdate update = CohortUpdate.newBuilder().setNamespace(namespace)
                                        .setUpdateType(CohortUpdateType.UPDATED_COHORT).setCohort(updatedCohort)
                                        .setCohortId(cohortId).setCohortType(cohortType).build();
                                cohortUpdateCallback.accept(update);
                            } else {
                                logger.info("Deleted cohort:{}", cohortId);
                                final CohortUpdate update = CohortUpdate.newBuilder().setNamespace(namespace)
                                        .setUpdateType(CohortUpdateType.UPDATED_COHORT).setCohortId(cohortId)
                                        .setCohortType(cohortType).build();
                                cohortUpdateCallback.accept(update);
                            }
                        } catch (final MembershipServerException problem) {
                            switch (problem.getCode()) {
                            case INVALID_MEMBERSHIP_LCM: {
                                break;
                            }
                            default: {
                                logger.error(String.format("Problem encountered while trying to describe cohortId:%s",
                                        cohortId), problem);
                                break;
                            }
                            }
                        }
                    }
                };
                serverProxyCurator.getChildren().usingWatcher(cohortChangedWatcher).forPath(cohortIdPath);
            } catch (final Exception curatorException) {
                if (curatorException instanceof MembershipServerException) {
                    throw MembershipServerException.class.cast(curatorException);
                } else {
                    // fix later
                    throw new MembershipServerException(Code.UNKNOWN_FAILURE, curatorException);
                }
            }
            break;
        }
        }
    }

    @Override
    public Member updateMember(final String namespace, final String memberId, final String cohortId,
            final CohortType cohortType, final byte[] memberMetadata, final int expectedVersion)
            throws MembershipServerException {
        // TODO
        return null;
    }

    // Responsible for replenishing watches that have been triggered and cleared
    private static final class WatcherReplenisher implements Lifecycle {
        private final AtomicBoolean running;
        private String identity;

        private WatcherReplenisher() {
            running = new AtomicBoolean(false);
        }

        @Override
        public String getIdentity() {
            return identity;
        }

        @Override
        public void start() throws Exception {
            // TODO
            identity = UUID.randomUUID().toString();
        }

        @Override
        public void stop() throws Exception {
            // TODO
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }
    }

    @SuppressWarnings("unused")
    private static boolean checkIdempotency(final KeeperException keeperException) {
        boolean idempotent = false;
        switch (keeperException.code()) {
        case CONNECTIONLOSS:
        case SESSIONEXPIRED:
        case SESSIONMOVED:
        case OPERATIONTIMEOUT:
            idempotent = true;
        default:
            break;
        }
        return idempotent;
    }

    private List<String> flattenTree(final String subTreeRoot) {
        final List<String> flattened = new ArrayList<>();
        if (subTreeRoot != null && !subTreeRoot.trim().isEmpty()) {
            // run breadth first search
            final Queue<String> queue = new ArrayDeque<>();
            queue.add(subTreeRoot);
            flattened.add(subTreeRoot);
            while (!queue.isEmpty()) {
                final String node = queue.poll();
                List<String> childNodes = null;
                switch (mode) {
                case ZK_DIRECT: {
                    try {
                        childNodes = serverProxyZk.getChildren(node, false);
                    } catch (KeeperException | InterruptedException problem) {
                        // logger.error(String.format("flattenTree encountered a
                        // problem while
                        // flattening %s", subTreeRoot), problem);
                    }
                    break;
                }
                case CURATOR: {
                    try {
                        childNodes = serverProxyCurator.getChildren().forPath(node);
                    } catch (Exception problem) {
                        // logger.error(String.format("flattenTree encountered a
                        // problem while
                        // flattening %s", subTreeRoot), problem);
                    }
                    break;
                }
                }
                if (childNodes != null) {
                    for (final String childNode : childNodes) {
                        if (childNode != null) {
                            final String childNodePath = node + "/" + childNode;
                            queue.add(childNodePath);
                            flattened.add(childNodePath);
                        }
                    }
                }
            }
        }
        return flattened;
    }

    private static CohortType cohortTypeFromString(final String cohortTypeString) {
        CohortType found = null;
        for (CohortType cohortType : CohortType.values()) {
            if (cohortType.name().equals(cohortTypeString)) {
                found = cohortType;
            }
        }
        return found;
    }

    private static byte[] fromByteString(final ByteString byteString) {
        byte[] byteArray = null;
        if (byteString != null) {
            byteArray = byteString.toByteArray();
        }
        return byteArray;
    }

    private static ByteString toByteString(final byte[] byteArray) {
        ByteString byteString = null;
        if (byteArray != null) {
            byteString = ByteString.copyFrom(byteArray);
        }
        return byteString;
    }

}
