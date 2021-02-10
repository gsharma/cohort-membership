package com.github.membership.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.TestingServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class CohortMembershipTest {
    private static final Logger logger = LogManager.getLogger(CohortMembershipTest.class.getSimpleName());

    private final String namespace = "test";

    private TestingCluster testingCluster;
    private TestingServer testingServer;
    private MembershipService membershipService;

    @Test
    public void testBasicJoin() throws Exception {
        // create a node
        final NewNodeRequest newNodeRequest = new NewNodeRequest();
        newNodeRequest.setNodeId(UUID.randomUUID().toString());
        final String serverHost = "localhost";
        final int serverPort = 8000;
        newNodeRequest.setAddress(new InetSocketAddress(serverHost, serverPort));
        final NewNodeResponse newNodeResponse = membershipService.newNode(newNodeRequest);
        final Node node = newNodeResponse.getNode();
        assertNotNull(node);
        assertEquals("/" + namespace + "/nodes/" + node.getId(), node.getPath());

        // create a cohort
        final NewCohortRequest newCohortRequest = new NewCohortRequest();
        newCohortRequest.setCohortId(UUID.randomUUID().toString());
        newCohortRequest.setCohortType(CohortType.ONE);
        final NewCohortResponse newCohortResponse = membershipService.newCohort(newCohortRequest);
        final Cohort cohort = newCohortResponse.getCohort();
        assertNotNull(cohort);
        assertEquals("/" + namespace + "/cohorts/" + newCohortRequest.getCohortType().name() + "/" + cohort.getId(), cohort.getPath());

        final JoinCohortRequest joinCohortRequest = new JoinCohortRequest();
        joinCohortRequest.setCohortId(cohort.getId());
        joinCohortRequest.setCohortType(cohort.getType());
        joinCohortRequest.setNodeId(node.getId());
        joinCohortRequest.setMemberId(UUID.randomUUID().toString());
        final JoinCohortResponse joinCohortResponse = membershipService.joinCohort(joinCohortRequest);
        final Member member = joinCohortResponse.getMember();
        assertNotNull(member);
        assertEquals(cohort.getPath() + "/members/" + member.getMemberId(), member.getPath());
    }

    @Before
    public void initTestCluster() throws Exception {
        final List<InstanceSpec> instanceSpecs = new ArrayList<>();

        for (int iter = 0; iter < 3; iter++) {
            final String serverHost = "localhost";
            final int serverPort = 4000 + iter;
            final File dataDir = new File("target/zkDataDir/" + iter);
            final InstanceSpec instanceSpec = new InstanceSpec(dataDir, serverPort, -1, -1, true, -1, -1, 2);
            instanceSpecs.add(instanceSpec);

            // serverAddresses.add(new InetSocketAddress(serverHost, serverPort));
        }

        // System.setProperty("zk.servers", "localhost:" + instanceSpec.getPort());
        System.setProperty("zookeeper.serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");

        // testingCluster = new TestingCluster(instanceSpecs);
        testingCluster = new TestingCluster(3);
        testingCluster.start();
        logger.info("Started testCluster {}", testingCluster.getConnectString());

        final List<InetSocketAddress> serverAddresses = new ArrayList<>();
        for (final InstanceSpec instanceSpec : testingCluster.getInstances()) {
            serverAddresses.add(new InetSocketAddress(instanceSpec.getHostname(), instanceSpec.getPort()));
        }

        membershipService = MembershipService.getMembership(serverAddresses, namespace);
        membershipService.start();
        assertTrue(membershipService.isRunning());
    }

    @After
    public void tiniTestCluster() throws Exception {
        if (membershipService != null) {
            membershipService.stop();
            assertFalse(membershipService.isRunning());
        }
        if (testingCluster != null) {
            logger.info("Stopping testCluster {}", testingCluster.getConnectString());
            testingCluster.close();
        }
    }

    // @Before
    public void initTestServer() throws Exception {
        final String serverHost = "localhost";
        final int serverPort = 4000;
        final File dataDir = new File("target/zkDataDir");
        final InstanceSpec instanceSpec = new InstanceSpec(dataDir, serverPort, -1, -1, true, -1, -1, 2);
        // System.setProperty("zk.servers", "localhost:" + instanceSpec.getPort());
        System.setProperty("zookeeper.serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");
        testingServer = new TestingServer(instanceSpec, false);
        testingServer.start();

        final List<InetSocketAddress> serverAddresses = Collections.singletonList(new InetSocketAddress(serverHost, serverPort));
        membershipService = MembershipService.getMembership(serverAddresses, namespace);
        membershipService.start();
        assertTrue(membershipService.isRunning());
    }

    // @After
    public void tiniTestServer() throws Exception {
        if (membershipService != null) {
            membershipService.stop();
            assertFalse(membershipService.isRunning());
        }
        if (testingServer != null) {
            testingServer.close();
        }
    }

}
