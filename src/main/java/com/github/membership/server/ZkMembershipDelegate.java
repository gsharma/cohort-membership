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
import org.apache.zookeeper.data.Stat;

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
import com.github.membership.server.MembershipServerException.Code;

/**
 * A Zookeeper-backed Cohort Membership Service.
 * 
 * TODO: switch to using multi() where possible
 */
final class ZkMembershipDelegate implements MembershipDelegate {
    private static final Logger logger = LogManager.getLogger(ZkMembershipDelegate.class.getSimpleName());

    private final MembershipDelegateConfiguration configuration;
    private final AtomicBoolean running;
    private final AtomicBoolean ready;

    private String identity;

    private ZooKeeper serverProxy;
    private long serverSessionId;

    private Set<String> trackedNamespaces;

    ZkMembershipDelegate(final MembershipDelegateConfiguration configuration) {
        this.running = new AtomicBoolean(false);
        this.ready = new AtomicBoolean(false);

        // this.serverAddresses = new ArrayList<>();
        // this.serverAddresses.addAll(serverAddresses);
        this.configuration = configuration;

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
            // for (final InetSocketAddress serverAddress : serverAddresses) {
            // connectString.append(serverAddress.getHostName()).append(':').append(serverAddress.getPort()).append(',');
            // }
            final int sessionTimeoutMillis = 2000;
            final Watcher watcher = new Watcher() {
                @Override
                public void process(final WatchedEvent watchedEvent) {
                    switch (watchedEvent.getState()) {
                        case SyncConnected:
                            transitionedToConnected.countDown();
                            break;
                        case Expired:
                            // TODO: handle session expiration
                            logger.info("Proxy session expired, {}", watchedEvent);
                            break;
                        default:
                            logger.info("Proxy encountered {}", watchedEvent);
                            break;
                    }
                }
            };
            try {
                final String connectString = configuration.getConnectString();
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
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        boolean success = false;
        try {
            final String namespace = request.getNamespace();
            String namespacePath = "/" + namespace;

            // create namespace node
            if (serverProxy.exists(namespacePath, false) == null) {
                logger.debug("Creating namespace {}", namespacePath);
                final Stat namespaceStat = new Stat();
                namespacePath = serverProxy.create(namespacePath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, namespaceStat);
                logger.debug("namespace:{}, stat:{}", namespacePath, namespaceStat);
                logger.info("Created namespace:{}, zxid:{}", namespacePath, namespaceStat.getCzxid());

                // create cohorts root node
                final Stat cohortRootStat = new Stat();
                final String cohortRootPath = serverProxy.create(namespacePath + "/cohorts", null, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT, cohortRootStat);
                logger.debug("cohorts root:{}, stat:{}", cohortRootPath, cohortRootStat);
                logger.info("Created cohorts root:{}, zxid:{}", cohortRootPath, cohortRootStat.getCzxid());

                // create nodes root node
                final Stat nodeRootStat = new Stat();
                final String nodeRootPath = serverProxy.create(namespacePath + "/nodes", null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT,
                        nodeRootStat);
                logger.debug("nodes root:{}, stat:{}", nodeRootPath, nodeRootStat);
                logger.info("Created nodes root:{}, zxid:{}", nodeRootPath, nodeRootStat.getCzxid());

                success = true;
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

        final NewNamespaceResponse response = NewNamespaceResponse.newBuilder()
                .setSuccess(success).build();
        logger.debug(response);
        return response;
    }

    @Override
    public PurgeNamespaceResponse purgeNamespace(final PurgeNamespaceRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        boolean success = false;
        try {
            final String namespace = request.getNamespace();
            final String namespacePath = "/" + namespace;
            logger.debug("Purging namespace {}", namespacePath);
            final List<String> childNodes = flattenTree(namespacePath);
            // start with leaves, work up from there
            for (int iter = childNodes.size() - 1; iter >= 0; iter--) {
                final String path = childNodes.get(iter);
                serverProxy.delete(path, -1);
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

        final PurgeNamespaceResponse response = PurgeNamespaceResponse.newBuilder()
                .setSuccess(success).build();
        logger.debug(response);
        return response;
    }

    @Override
    public NewCohortTypeResponse newCohortType(final NewCohortTypeRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        boolean success = false;
        try {
            final String namespace = request.getNamespace();
            final CohortType cohortType = request.getCohortType();
            final String cohortRootPath = "/" + namespace + "/cohorts";
            String cohortTypePath = cohortRootPath + "/" + cohortType;

            if (serverProxy.exists(cohortTypePath, false) == null) {
                logger.debug("Creating cohort type {}", cohortTypePath);
                final Stat cohortTypeStat = new Stat();
                cohortTypePath = serverProxy.create(cohortTypePath, null,
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

        final NewCohortTypeResponse response = NewCohortTypeResponse.newBuilder()
                .setSuccess(success).build();
        logger.debug(response);
        return response;
    }

    @Override
    public NewCohortResponse newCohort(final NewCohortRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        Cohort cohort = null;
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortTypePath = cohortRootPath + "/" + cohortType;
            if (serverProxy.exists(cohortTypePath, false) == null) {
                logger.warn("Failed to locate cohortType tree {}", cohortTypePath);
                throw new MembershipServerException(Code.UNKNOWN_FAILURE);
            }
            String cohortChildPath = cohortTypePath + "/" + cohortId;
            if (serverProxy.exists(cohortChildPath, false) == null) {
                logger.debug("Creating cohort {}", cohortChildPath);
                final Stat cohortStat = new Stat();
                cohortChildPath = serverProxy.create(cohortChildPath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, cohortStat);
                logger.debug("cohort:{}, stat:{}", cohortChildPath, cohortStat);
                logger.info("Created cohort:{}, zxid:{}", cohortChildPath, cohortStat.getCzxid());

                // for debugging
                // if (serverProxy.exists(cohortChildPath, false) == null) {
                // logger.warn("Failed to create cohort {}", cohortChildPath);
                // }

                String membersChildPath = cohortChildPath + "/members";
                logger.debug("Creating members root {}", membersChildPath);
                final Stat membersChildStat = new Stat();
                membersChildPath = serverProxy.create(membersChildPath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, membersChildStat);
                logger.debug("members root:{}, stat:{}", membersChildPath, membersChildStat);
                logger.info("Created members root:{}, zxid:{}", membersChildPath, membersChildStat.getCzxid());

                cohort = Cohort.newBuilder()
                        .setType(cohortType)
                        .setId(cohortId)
                        .setPath(cohortChildPath).build();

                // for debugging
                // if (serverProxy.exists(membersChildPath, false) == null) {
                // logger.warn("Failed to create members root {}", membersChildPath);
                // }

                final Watcher membershipChangedWatcher = new Watcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        logger.debug("Membership changed, {}", watchedEvent);
                        switch (watchedEvent.getType()) {
                            case NodeCreated:
                                logger.info("Member added, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                                break;
                            case NodeDeleted:
                                logger.info("Member left or died, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                                break;
                            case NodeDataChanged:
                                logger.info("Member data changed, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                                break;
                            case NodeChildrenChanged:
                                logger.info("Membership changed, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                                break;
                            default:
                                logger.info("Membership change triggered, sessionId:{}, {}", serverSessionId, watchedEvent);
                                break;
                        }
                    }
                };
                serverProxy.getChildren(membersChildPath, membershipChangedWatcher);
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

        final NewCohortResponse response = NewCohortResponse.newBuilder()
                .setCohort(cohort).build();
        logger.debug(response);
        return response;
    }

    @Override
    public ListNodesResponse listNodes(final ListNodesRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        final List<Node> nodes = new ArrayList<>();
        try {
            final String namespace = request.getNamespace();
            final String nodeRootPath = "/" + namespace + "/nodes";
            logger.debug("List nodes under: {}", nodeRootPath);
            final Stat nodeRootStat = new Stat();
            final List<String> nodeIds = serverProxy.getChildren(nodeRootPath, false, nodeRootStat);
            logger.debug("node root:{}, stat:{}", nodeRootPath, nodeRootStat);
            for (final String nodeId : nodeIds) {
                final Node node = Node.newBuilder().setId(nodeId).setPath(nodeRootPath + "/" + nodeId).build();
                // TODO
                // node.setAddress(null);
                // node.setPersona(null);
                nodes.add(node);
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
        final ListNodesResponse response = ListNodesResponse.newBuilder()
                .addAllNodes(nodes).build();
        logger.debug(response);
        return response;
    }

    @Override
    public NewNodeResponse newNode(final NewNodeRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        Node node = null;
        try {
            final String namespace = request.getNamespace();
            final String nodeId = request.getNodeId();
            final String address = request.getAddress();
            final String nodeRootPath = "/" + namespace + "/nodes";
            String nodeChildPath = nodeRootPath + "/" + nodeId;
            if (serverProxy.exists(nodeChildPath, false) == null) {
                logger.debug("Creating node {}", nodeChildPath);
                final Stat nodeStat = new Stat();
                // TODO: save data in znode
                nodeChildPath = serverProxy.create(nodeChildPath, null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL, nodeStat);
                logger.debug("node:{}, stat:{}", nodeChildPath, nodeStat);

                node = Node.newBuilder()
                        .setAddress(address)
                        .setId(nodeId)
                        .setPath(nodeChildPath).build();
                logger.info("Created node {}, zxid:{}", node, nodeStat.getCzxid());

                final Watcher nodeChangedWatcher = new Watcher() {
                    @Override
                    public void process(final WatchedEvent watchedEvent) {
                        logger.debug("Node changed, {}", watchedEvent);
                        switch (watchedEvent.getType()) {
                            case NodeCreated:
                                logger.info("Node added, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                                break;
                            case NodeDeleted:
                                logger.info("Node left or died, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                                break;
                            case NodeDataChanged:
                                logger.info("Node data changed, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                                break;
                            case NodeChildrenChanged:
                                logger.info("Node changed, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                                break;
                            default:
                                logger.info("Node change triggered, sessionId:{}, {}", serverSessionId, watchedEvent);
                                break;
                        }
                    }
                };
                serverProxy.getChildren(nodeRootPath, nodeChangedWatcher);
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

        final NewNodeResponse response = NewNodeResponse.newBuilder()
                .setNode(node).build();
        logger.debug(response);
        return response;
    }

    @Override
    public ListCohortsResponse listCohorts(final ListCohortsRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        final List<Cohort> cohorts = new ArrayList<>();
        try {
            final String namespace = request.getNamespace();
            final String cohortRootPath = "/" + namespace + "/cohorts";
            logger.debug("List cohorts under {}", cohortRootPath);
            final Stat cohortRootStat = new Stat();
            final List<String> cohortTypes = serverProxy.getChildren(cohortRootPath, false, cohortRootStat);
            logger.debug("cohort root:{}, stat:{}", cohortRootPath, cohortRootStat);
            for (final String cohortType : cohortTypes) {
                final String cohortTypePath = cohortRootPath + "/" + cohortType;
                final Stat cohortTypeStat = new Stat();
                final List<String> cohortIds = serverProxy.getChildren(cohortTypePath, false, cohortTypeStat);
                logger.debug("cohort type:{}, stat:{}", cohortTypePath, cohortTypeStat);
                for (final String cohortId : cohortIds) {
                    final DescribeCohortRequest describeCohortRequest = DescribeCohortRequest.newBuilder().setNamespace(namespace)
                            .setCohortId(cohortId).setCohortType(cohortTypeFromString(cohortType)).build();
                    final DescribeCohortResponse describeCohortResponse = describeCohort(describeCohortRequest);
                    final Cohort cohort = describeCohortResponse.getCohort();
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

        final ListCohortsResponse response = ListCohortsResponse.newBuilder().addAllCohorts(cohorts).build();
        logger.debug(response);
        return response;
    }

    @Override
    public JoinCohortResponse joinCohort(final JoinCohortRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }

        Cohort cohort = null;
        try {
            final String namespace = request.getNamespace();
            final String memberId = request.getMemberId();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final String nodeId = request.getNodeId();
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
            if (serverProxy.exists(cohortMembersPath, false) == null) {
                final String warning = "Failed to locate member tree " + cohortMembersPath;
                logger.warn(warning);
                throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
            }

            String memberChildPath = cohortMembersPath + "/" + memberId;
            logger.debug("Creating member {}", memberChildPath);
            // TODO: save data in znode
            final Stat memberStat = new Stat();
            memberChildPath = serverProxy.create(memberChildPath, null,
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL, memberStat);
            logger.debug("member:{}, stat:{}", memberChildPath, memberStat);

            final Member member = Member.newBuilder()
                    .setMemberId(memberId)
                    .setCohortType(cohortType)
                    .setCohortId(cohortId)
                    .setNodeId(nodeId)
                    .setPath(memberChildPath).build();
            logger.info("Created member {}, zxid:{}", member, memberStat.getCzxid());

            final Watcher membershipChangedWatcher = new Watcher() {
                @Override
                public void process(final WatchedEvent watchedEvent) {
                    // logger.info("Membership changed, {}", watchedEvent);
                    switch (watchedEvent.getType()) {
                        case NodeCreated:
                            logger.info("Member added, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                            break;
                        case NodeDeleted:
                            logger.info("Member left or died, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                            break;
                        case NodeDataChanged:
                            logger.info("Member data changed, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                            break;
                        case NodeChildrenChanged:
                            logger.info("Membership changed, sessionId:{}, {}", serverSessionId, watchedEvent.getPath());
                            break;
                        default:
                            logger.info("Membership change triggered, sessionId:{}, {}", serverSessionId, watchedEvent);
                            break;
                    }
                }
            };
            // serverProxy.exists(memberChildPath, membershipChangedWatcher);
            // serverProxy.getChildren(cohortMembersPath, membershipChangedWatcher);

            final DescribeCohortRequest describeCohortRequest = DescribeCohortRequest.newBuilder().setNamespace(namespace).setCohortId(cohortId)
                    .setCohortType(cohortType).build();
            final DescribeCohortResponse describeCohortResponse = describeCohort(describeCohortRequest);
            cohort = describeCohortResponse.getCohort();
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

        final JoinCohortResponse response = JoinCohortResponse.newBuilder()
                .setCohort(cohort).build();
        logger.debug(response);
        return response;
    }

    @Override
    public DescribeCohortResponse describeCohort(final DescribeCohortRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }

        Cohort cohort = null;
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
            if (serverProxy.exists(cohortMembersPath, false) == null) {
                final String warning = "Failed to locate member tree " + cohortMembersPath;
                logger.warn(warning);
                throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
            }
            final List<String> memberIds = serverProxy.getChildren(cohortMembersPath, false);
            final List<Member> members = new ArrayList<>();
            for (final String memberId : memberIds) {
                final Member member = Member.newBuilder()
                        .setCohortId(cohortId)
                        .setCohortType(cohortType)
                        .setMemberId(memberId)
                        .setPath(cohortMembersPath + "/" + memberId).build();
                // member.setNodeId(null); // TODO
                members.add(member);
            }

            cohort = Cohort.newBuilder()
                    .setType(cohortType)
                    .addAllMembers(members)
                    .setId(cohortId)
                    .setPath(cohortRootPath + "/" + cohortType + "/" + cohortId).build();
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

        final DescribeCohortResponse response = DescribeCohortResponse.newBuilder().setCohort(cohort).build();
        logger.debug(response);
        return response;
    }

    @Override
    public LeaveCohortResponse leaveCohort(final LeaveCohortRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        boolean success = false;
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final String memberId = request.getMemberId();
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortMembersPath = cohortRootPath + "/" + cohortType + "/" + cohortId + "/members";
            if (serverProxy.exists(cohortMembersPath, false) == null) {
                final String warning = "Failed to locate member tree " + cohortMembersPath;
                logger.warn(warning);
                throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
            }
            serverProxy.delete(cohortMembersPath + "/" + memberId, -1);
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

        final LeaveCohortResponse response = LeaveCohortResponse.newBuilder()
                .setSuccess(success).build();
        logger.debug(response);
        return response;
    }

    @Override
    public DeleteCohortResponse deleteCohort(final DeleteCohortRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        boolean success = false;
        try {
            final String namespace = request.getNamespace();
            final String cohortId = request.getCohortId();
            final CohortType cohortType = request.getCohortType();
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortPath = cohortRootPath + "/" + cohortType + "/" + cohortId;
            if (serverProxy.exists(cohortPath, false) == null) {
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
                serverProxy.delete(path, -1);
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

        final DeleteCohortResponse response = DeleteCohortResponse.newBuilder()
                .setSuccess(success).build();
        logger.debug(response);
        return response;
    }

    @Override
    public DeleteCohortTypeResponse deleteCohortType(final DeleteCohortTypeRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        boolean success = false;
        try {
            final String namespace = request.getNamespace();
            final CohortType cohortType = request.getCohortType();
            final String cohortRootPath = "/" + namespace + "/cohorts";
            final String cohortTypePath = cohortRootPath + "/" + cohortType;
            if (serverProxy.exists(cohortTypePath, false) == null) {
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
                serverProxy.delete(path, -1);
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

        final DeleteCohortTypeResponse response = DeleteCohortTypeResponse.newBuilder()
                .setSuccess(success).build();
        logger.debug(response);
        return response;
    }

    @Override
    public DeleteNodeResponse deleteNode(final DeleteNodeRequest request) throws MembershipServerException {
        logger.debug(request);
        if (!isRunning()) {
            throw new MembershipServerException(Code.INVALID_MEMBERSHIP_LCM,
                    "Invalid attempt to operate an already stopped membership service");
        }
        // TODO
        // if (!request.validate()) {
        // throw new MembershipServerException(Code.REQUEST_VALIDATION_FAILURE, request.toString());
        // }
        boolean success = false;
        try {
            final String namespace = request.getNamespace();
            final String nodeId = request.getNodeId();
            final String nodeRootPath = "/" + namespace + "/nodes";
            final String nodePath = nodeRootPath + "/" + nodeId;
            if (serverProxy.exists(nodePath, false) == null) {
                final String warning = "Failed to locate node " + nodePath;
                logger.warn(warning);
                throw new MembershipServerException(Code.PARENT_LOCATOR_FAILURE, warning);
            }

            serverProxy.delete(nodePath, -1);
            success = true;
            logger.info("Deleted node {}", nodePath);
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

        final DeleteNodeResponse response = DeleteNodeResponse.newBuilder()
                .setSuccess(success).build();
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

    private static CohortType cohortTypeFromString(final String cohortTypeString) {
        CohortType found = null;
        for (CohortType cohortType : CohortType.values()) {
            if (cohortType.name().equals(cohortTypeString)) {
                found = cohortType;
            }
        }
        return found;
    }

}
