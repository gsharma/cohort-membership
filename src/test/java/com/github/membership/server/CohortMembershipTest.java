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
import org.apache.curator.test.TestingZooKeeperServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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

/**
 * Tests to maintain the sanity of MembershipService.
 */
public final class CohortMembershipTest {
    private static final Logger logger = LogManager.getLogger(CohortMembershipTest.class.getSimpleName());

    private TestingCluster testingCluster;
    private TestingServer testingServer;

    @Test
    public void testBasicJoin() throws Exception {
        MembershipDelegate membershipService = null;
        try {
            final MembershipDelegateConfiguration configuration = new MembershipDelegateConfiguration();
            configuration.setConnectString(testingCluster.getConnectString());
            membershipService = MembershipDelegate.getDelegate(configuration);
            membershipService.start();
            assertTrue(membershipService.isRunning());

            logger.info("[step-1] create namespace");
            final String namespace = "testBasicJoin";
            final NewNamespaceRequest newNamespaceRequestOne = NewNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            final NewNamespaceResponse newNamespaceResponseOne = membershipService.newNamespace(newNamespaceRequestOne);
            // assertEquals("/" + namespace, newNamespaceResponseOne.getPath());
            assertTrue(newNamespaceResponseOne.getSuccess());

            logger.info("[step-2] create nodeOne");
            final String serverHostOne = "localhost";
            final int serverPortOne = 8000;
            final NewNodeRequest newNodeRequestOne = NewNodeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setNodeId(UUID.randomUUID().toString())
                    .setPersona(NodePersona.COMPUTE)
                    .setAddress(serverHostOne + ":" + serverPortOne).build();
            final NewNodeResponse newNodeResponseOne = membershipService.newNode(newNodeRequestOne);
            final Node nodeOne = newNodeResponseOne.getNode();
            assertNotNull(nodeOne);
            assertEquals("/" + namespace + "/nodes/" + nodeOne.getId(), nodeOne.getPath());

            logger.info("[step-3] create nodeTwo");
            final String serverHostTwo = "localhost";
            final int serverPortTwo = 8001;
            final NewNodeRequest newNodeRequestTwo = NewNodeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setNodeId(UUID.randomUUID().toString())
                    .setPersona(NodePersona.DATA_COMPUTE)
                    .setAddress(serverHostTwo + ":" + serverPortTwo).build();
            final NewNodeResponse newNodeResponseTwo = membershipService.newNode(newNodeRequestTwo);
            final Node nodeTwo = newNodeResponseTwo.getNode();
            assertNotNull(nodeTwo);
            assertEquals("/" + namespace + "/nodes/" + nodeTwo.getId(), nodeTwo.getPath());

            logger.info("[step-4] create cohortTypeOne");
            final NewCohortTypeRequest newCohortTypeRequestOne = NewCohortTypeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortType(CohortType.ONE).build();
            final NewCohortTypeResponse newCohortTypeResponseOne = membershipService.newCohortType(newCohortTypeRequestOne);
            assertTrue(newCohortTypeResponseOne.getSuccess());
            // assertEquals(CohortType.ONE, newCohortTypeResponseOne.getCohortType());
            // assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestOne.getCohortType().name(), newCohortTypeResponseOne.getPath());

            logger.info("[step-5] create cohortTypeTwo");
            final NewCohortTypeRequest newCohortTypeRequestTwo = NewCohortTypeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortType(CohortType.TWO).build();
            final NewCohortTypeResponse newCohortTypeResponseTwo = membershipService.newCohortType(newCohortTypeRequestTwo);
            assertTrue(newCohortTypeResponseTwo.getSuccess());
            // assertEquals(CohortType.TWO, newCohortTypeResponseTwo.getCohortType());
            // assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestTwo.getCohortType().name(), newCohortTypeResponseTwo.getPath());

            logger.info("[step-6] create cohortOne");
            final NewCohortRequest newCohortRequestOne = NewCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(UUID.randomUUID().toString())
                    .setCohortType(CohortType.ONE).build();
            final NewCohortResponse newCohortResponseOne = membershipService.newCohort(newCohortRequestOne);
            final Cohort cohortOne = newCohortResponseOne.getCohort();
            assertNotNull(cohortOne);
            assertEquals("/" + namespace + "/cohorts/" + newCohortRequestOne.getCohortType().name() + "/" + cohortOne.getId(), cohortOne.getPath());

            logger.info("[step-7] create cohortTwo");
            final NewCohortRequest newCohortRequestTwo = NewCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(UUID.randomUUID().toString())
                    .setCohortType(CohortType.TWO).build();
            final NewCohortResponse newCohortResponseTwo = membershipService.newCohort(newCohortRequestTwo);
            final Cohort cohortTwo = newCohortResponseTwo.getCohort();
            assertNotNull(cohortTwo);
            assertEquals("/" + namespace + "/cohorts/" + newCohortRequestTwo.getCohortType().name() + "/" + cohortTwo.getId(), cohortTwo.getPath());

            logger.info("[step-8] list cohorts, check for cohortOne and cohortTwo");
            final ListCohortsRequest listCohortsRequestOne = ListCohortsRequest.newBuilder()
                    .setNamespace(namespace).build();
            final ListCohortsResponse listCohortsResponseOne = membershipService.listCohorts(listCohortsRequestOne);
            assertEquals(2, listCohortsResponseOne.getCohortsList().size());
            assertTrue(listCohortsResponseOne.getCohortsList().contains(cohortOne));
            assertTrue(listCohortsResponseOne.getCohortsList().contains(cohortTwo));

            logger.info("[step-9] memberOne joins cohortOne");
            final JoinCohortRequest joinCohortRequestOne = JoinCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType())
                    .setNodeId(nodeOne.getId())
                    .setMemberId(UUID.randomUUID().toString()).build();
            final String memberOneId = joinCohortRequestOne.getMemberId();
            final JoinCohortResponse joinCohortResponseOne = membershipService.joinCohort(joinCohortRequestOne);
            assertEquals(1, joinCohortResponseOne.getCohort().getMembersList().size());
            // final Member memberOne = joinCohortResponseOne.getMember();
            // assertNotNull(memberOne);
            // assertEquals(cohortOne.getPath() + "/members/" + memberOne.getMemberId(), memberOne.getPath());

            logger.info("[step-10] memberTwo joins cohortOne");
            final JoinCohortRequest joinCohortRequestTwo = JoinCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType())
                    .setNodeId(nodeTwo.getId())
                    .setMemberId(UUID.randomUUID().toString()).build();
            final String memberTwoId = joinCohortRequestTwo.getMemberId();
            final JoinCohortResponse joinCohortResponseTwo = membershipService.joinCohort(joinCohortRequestTwo);
            assertEquals(2, joinCohortResponseTwo.getCohort().getMembersList().size());
            // final Member memberTwo = joinCohortResponseTwo.getMember();
            // assertNotNull(memberTwo);
            // assertEquals(cohortOne.getPath() + "/members/" + memberTwo.getMemberId(), memberTwo.getPath());

            logger.info("[step-11] describe cohortOne, check for memberOne and memberTwo");
            final DescribeCohortRequest describeCohortRequestOne = DescribeCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType()).build();
            final DescribeCohortResponse describeCohortResponseOne = membershipService.describeCohort(describeCohortRequestOne);
            final Cohort describedCohortOne = describeCohortResponseOne.getCohort();
            assertEquals(cohortOne.getId(), describedCohortOne.getId());
            assertEquals(cohortOne.getType(), describedCohortOne.getType());
            List<Member> members = describedCohortOne.getMembersList();
            assertEquals(2, members.size());
            final List<String> memberIds = new ArrayList<>();
            for (final Member member : members) {
                memberIds.add(member.getMemberId());
            }
            assertTrue(memberIds.contains(memberOneId));
            assertTrue(memberIds.contains(memberTwoId));

            logger.info("[step-12] describe cohortTwo, should have no members");
            final DescribeCohortRequest describeCohortRequestTwo = DescribeCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortTwo.getId())
                    .setCohortType(cohortTwo.getType()).build();
            final DescribeCohortResponse describeCohortResponseTwo = membershipService.describeCohort(describeCohortRequestTwo);
            final Cohort describedCohortTwo = describeCohortResponseTwo.getCohort();
            assertEquals(cohortTwo.getId(), describedCohortTwo.getId());
            assertEquals(cohortTwo.getType(), describedCohortTwo.getType());
            members = describedCohortTwo.getMembersList();
            assertEquals(0, members.size());

            logger.info("[step-13] memberTwo leaves cohortOne");
            final LeaveCohortRequest leaveCohortRequestOne = LeaveCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType())
                    .setMemberId(memberTwoId).build();
            final LeaveCohortResponse leaveCohortResponseOne = membershipService.leaveCohort(leaveCohortRequestOne);
            assertTrue(leaveCohortResponseOne.getSuccess());

            logger.info("[step-14] describe cohortOne, check for presence of memberOne only");
            final DescribeCohortRequest describeCohortRequestThree = DescribeCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType()).build();
            final DescribeCohortResponse describeCohortResponseThree = membershipService.describeCohort(describeCohortRequestThree);
            final Cohort describedCohortThree = describeCohortResponseThree.getCohort();
            assertEquals(cohortOne.getId(), describedCohortThree.getId());
            assertEquals(cohortOne.getType(), describedCohortThree.getType());
            members = describedCohortThree.getMembersList();
            assertEquals(1, members.size());
            assertEquals(memberOneId, members.get(0).getMemberId());

            logger.info("[step-15] delete cohortTwo");
            final DeleteCohortRequest deleteCohortRequestOne = DeleteCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortTwo.getId())
                    .setCohortType(cohortTwo.getType()).build();
            final DeleteCohortResponse deleteCohortResponseOne = membershipService.deleteCohort(deleteCohortRequestOne);
            // assertEquals(cohortTwo.getId(), deleteCohortResponseOne.getCohortId());
            assertTrue(deleteCohortResponseOne.getSuccess());

            logger.info("[step-16] delete cohortOne");
            final DeleteCohortRequest deleteCohortRequestTwo = DeleteCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType()).build();
            final DeleteCohortResponse deleteCohortResponseTwo = membershipService.deleteCohort(deleteCohortRequestTwo);
            // assertEquals(cohortOne.getId(), deleteCohortResponseTwo.getCohortId());
            assertTrue(deleteCohortResponseTwo.getSuccess());

            logger.info("[step-17] delete cohortTypeOne");
            final DeleteCohortTypeRequest deleteCohortTypeRequestOne = DeleteCohortTypeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortType(cohortOne.getType()).build();
            final DeleteCohortTypeResponse deleteCohortTypeResponseOne = membershipService.deleteCohortType(deleteCohortTypeRequestOne);
            // assertEquals(cohortOne.getType(), deleteCohortTypeResponseOne.getCohortType());
            assertTrue(deleteCohortTypeResponseOne.getSuccess());

            logger.info("[step-18] delete nodeOne");
            final DeleteNodeRequest deleteNodeRequestOne = DeleteNodeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setNodeId(nodeOne.getId()).build();
            final DeleteNodeResponse deleteNodeResponseOne = membershipService.deleteNode(deleteNodeRequestOne);
            assertTrue(deleteNodeResponseOne.getSuccess());

            logger.info("[step-19] delete nodeTwo");
            final DeleteNodeRequest deleteNodeRequestTwo = DeleteNodeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setNodeId(nodeTwo.getId()).build();
            final DeleteNodeResponse deleteNodeResponseTwo = membershipService.deleteNode(deleteNodeRequestTwo);
            assertTrue(deleteNodeResponseTwo.getSuccess());

            logger.info("[step-20] purge namespace");
            final PurgeNamespaceRequest purgeNamespaceRequestOne = PurgeNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            final PurgeNamespaceResponse purgeNamespaceResponseOne = membershipService.purgeNamespace(purgeNamespaceRequestOne);
            // assertEquals(namespace, purgeNamespaceResponseOne.getNamespace());
            assertTrue(purgeNamespaceResponseOne.getSuccess());
        } finally {
            if (membershipService != null && membershipService.isRunning()) {
                membershipService.stop();
                assertFalse(membershipService.isRunning());
            }
        }
    }

    @Test
    public void testNodeDeath() throws Exception {
        MembershipDelegate membershipServiceOne = null;
        MembershipDelegate membershipServiceTwo = null;
        try {
            final MembershipDelegateConfiguration configuration = new MembershipDelegateConfiguration();
            configuration.setConnectString(testingCluster.getConnectString());

            membershipServiceOne = MembershipDelegate.getDelegate(configuration);
            membershipServiceOne.start();
            assertTrue(membershipServiceOne.isRunning());

            membershipServiceTwo = MembershipDelegate.getDelegate(configuration);
            membershipServiceTwo.start();
            assertTrue(membershipServiceTwo.isRunning());

            logger.info("[step-1] create namespace");
            final String namespace = "testNodeDeath";
            final NewNamespaceRequest newNamespaceRequestOne = NewNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            final NewNamespaceResponse newNamespaceResponseOne = membershipServiceOne.newNamespace(newNamespaceRequestOne);
            // assertEquals("/" + namespace, newNamespaceResponseOne.getPath());
            assertTrue(newNamespaceResponseOne.getSuccess());

            logger.info("[step-2] create nodeOne");
            final String serverHostOne = "localhost";
            final int serverPortOne = 8000;
            final NewNodeRequest newNodeRequestOne = NewNodeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setNodeId(UUID.randomUUID().toString())
                    .setPersona(NodePersona.COMPUTE)
                    .setAddress(serverHostOne + ":" + serverPortOne).build();
            final NewNodeResponse newNodeResponseOne = membershipServiceOne.newNode(newNodeRequestOne);
            final Node nodeOne = newNodeResponseOne.getNode();
            assertNotNull(nodeOne);
            assertEquals("/" + namespace + "/nodes/" + nodeOne.getId(), nodeOne.getPath());

            logger.info("[step-3] create nodeTwo");
            final String serverHostTwo = "localhost";
            final int serverPortTwo = 8001;
            final NewNodeRequest newNodeRequestTwo = NewNodeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setNodeId(UUID.randomUUID().toString())
                    .setPersona(NodePersona.DATA_COMPUTE)
                    .setAddress(serverHostTwo + ":" + serverPortTwo).build();
            final NewNodeResponse newNodeResponseTwo = membershipServiceTwo.newNode(newNodeRequestTwo);
            final Node nodeTwo = newNodeResponseTwo.getNode();
            assertNotNull(nodeTwo);
            assertEquals("/" + namespace + "/nodes/" + nodeTwo.getId(), nodeTwo.getPath());

            logger.info("[step-4] create cohortTypeOne");
            final NewCohortTypeRequest newCohortTypeRequestOne = NewCohortTypeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortType(CohortType.ONE).build();
            final NewCohortTypeResponse newCohortTypeResponseOne = membershipServiceOne.newCohortType(newCohortTypeRequestOne);
            assertTrue(newCohortTypeResponseOne.getSuccess());
            // assertEquals(CohortType.ONE, newCohortTypeResponseOne.getCohortType());
            // assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestOne.getCohortType().name(), newCohortTypeResponseOne.getPath());

            logger.info("[step-5] create cohortOne");
            final NewCohortRequest newCohortRequestOne = NewCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(UUID.randomUUID().toString())
                    .setCohortType(CohortType.ONE).build();
            final NewCohortResponse newCohortResponseOne = membershipServiceOne.newCohort(newCohortRequestOne);
            final Cohort cohortOne = newCohortResponseOne.getCohort();
            assertNotNull(cohortOne);
            assertEquals("/" + namespace + "/cohorts/" + newCohortRequestOne.getCohortType().name() + "/" + cohortOne.getId(), cohortOne.getPath());

            logger.info("[step-6] list cohorts, check for cohortOne");
            final ListCohortsRequest listCohortsRequestOne = ListCohortsRequest.newBuilder()
                    .setNamespace(namespace).build();
            final ListCohortsResponse listCohortsResponseOne = membershipServiceOne.listCohorts(listCohortsRequestOne);
            assertEquals(1, listCohortsResponseOne.getCohortsList().size());
            assertTrue(listCohortsResponseOne.getCohortsList().contains(cohortOne));

            logger.info("[step-7] memberOne joins cohortOne");
            final JoinCohortRequest joinCohortRequestOne = JoinCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType())
                    .setNodeId(nodeOne.getId())
                    .setMemberId(UUID.randomUUID().toString()).build();
            final String memberOneId = joinCohortRequestOne.getMemberId();
            final JoinCohortResponse joinCohortResponseOne = membershipServiceOne.joinCohort(joinCohortRequestOne);
            assertEquals(1, joinCohortResponseOne.getCohort().getMembersList().size());
            // final Member memberOne = joinCohortResponseOne.getMember();
            // assertNotNull(memberOne);
            // assertEquals(cohortOne.getPath() + "/members/" + memberOne.getMemberId(), memberOne.getPath());

            logger.info("[step-8] memberTwo joins cohortOne");
            final JoinCohortRequest joinCohortRequestTwo = JoinCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType())
                    .setNodeId(nodeTwo.getId())
                    .setMemberId(UUID.randomUUID().toString()).build();
            final String memberTwoId = joinCohortRequestTwo.getMemberId();
            final JoinCohortResponse joinCohortResponseTwo = membershipServiceTwo.joinCohort(joinCohortRequestTwo);
            assertEquals(2, joinCohortResponseTwo.getCohort().getMembersList().size());
            // final Member memberTwo = joinCohortResponseTwo.getMember();
            // assertNotNull(memberTwo);
            // assertEquals(cohortOne.getPath() + "/members/" + memberTwo.getMemberId(), memberTwo.getPath());

            logger.info("[step-9] describe cohortOne, check for memberOne and memberTwo");
            final DescribeCohortRequest describeCohortRequestOne = DescribeCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType()).build();
            final DescribeCohortResponse describeCohortResponseOne = membershipServiceOne.describeCohort(describeCohortRequestOne);
            final Cohort describedCohortOne = describeCohortResponseOne.getCohort();
            assertEquals(cohortOne.getId(), describedCohortOne.getId());
            assertEquals(cohortOne.getType(), describedCohortOne.getType());
            List<Member> members = describedCohortOne.getMembersList();
            assertEquals(2, members.size());
            final List<String> memberIds = new ArrayList<>();
            for (final Member member : members) {
                memberIds.add(member.getMemberId());
            }
            assertTrue(memberIds.contains(memberOneId));
            assertTrue(memberIds.contains(memberTwoId));

            logger.info("[step-10] list nodes, check for nodeOne and nodeTwo");
            final ListNodesRequest listNodesRequestOne = ListNodesRequest.newBuilder()
                    .setNamespace(namespace).build();
            final ListNodesResponse listNodesResponseOne = membershipServiceOne.listNodes(listNodesRequestOne);
            assertEquals(2, listNodesResponseOne.getNodesList().size());

            logger.info("[step-12] stop membershipServiceTwo, memberTwo should've left cohortOne");
            membershipServiceTwo.stop();

            logger.info("[step-13] describe cohortOne, check for presence of memberOne only");
            final DescribeCohortRequest describeCohortRequestThree = DescribeCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType()).build();
            final DescribeCohortResponse describeCohortResponseThree = membershipServiceOne.describeCohort(describeCohortRequestThree);
            final Cohort describedCohortTwo = describeCohortResponseThree.getCohort();
            assertEquals(cohortOne.getId(), describedCohortTwo.getId());
            assertEquals(cohortOne.getType(), describedCohortTwo.getType());
            members = describedCohortTwo.getMembersList();
            assertEquals(1, members.size());
            assertEquals(memberOneId, members.get(0).getMemberId());

            logger.info("[step-14] list nodes, check for nodeOne only");
            final ListNodesRequest listNodesRequestTwo = ListNodesRequest.newBuilder()
                    .setNamespace(namespace).build();
            final ListNodesResponse listNodesResponseTwo = membershipServiceOne.listNodes(listNodesRequestTwo);
            assertEquals(1, listNodesResponseTwo.getNodesList().size());
            assertTrue(listNodesResponseTwo.getNodesList().get(0).getId().equals(nodeOne.getId()));

            logger.info("[step-15] purge namespace");
            final PurgeNamespaceRequest purgeNamespaceRequestOne = PurgeNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            final PurgeNamespaceResponse purgeNamespaceResponseOne = membershipServiceOne.purgeNamespace(purgeNamespaceRequestOne);
            // assertEquals(namespace, purgeNamespaceResponseOne.getNamespace());
            assertTrue(purgeNamespaceResponseOne.getSuccess());
        } finally {
            if (membershipServiceOne != null && membershipServiceOne.isRunning()) {
                membershipServiceOne.stop();
                assertFalse(membershipServiceOne.isRunning());
            }
            if (membershipServiceTwo != null && membershipServiceTwo.isRunning()) {
                membershipServiceTwo.stop();
                assertFalse(membershipServiceTwo.isRunning());
            }
        }
    }

    @Test
    public void testMembershipServiceLCM() throws Exception {
        for (int iter = 0; iter < 3; iter++) {
            final MembershipDelegateConfiguration configuration = new MembershipDelegateConfiguration();
            configuration.setConnectString(testingCluster.getConnectString());
            final MembershipDelegate membershipService = MembershipDelegate.getDelegate(configuration);
            membershipService.start();
            assertTrue(membershipService.isRunning());

            membershipService.stop();
            assertFalse(membershipService.isRunning());
        }
    }

    @Before
    public void initTestCluster() throws Exception {
        logger.info("[step-0] init testCluster");
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

        final List<TestingZooKeeperServer> testServers = testingCluster.getServers();
        assertEquals(3, testServers.size());
        for (final TestingZooKeeperServer testServer : testServers) {
            assertTrue(testServer.getQuorumPeer().isRunning());
            // logger.info(testServer.getQuorumPeer());
        }
        logger.info("Started testCluster {}", testingCluster.getConnectString());
    }

    @After
    public void tiniTestCluster() throws Exception {
        if (testingCluster != null) {
            logger.info("Stopping testCluster {}", testingCluster.getConnectString());
            testingCluster.close();
        }
        logger.info("[step-n] tini testCluster");
    }

    // @Before
    public void initTestServer() throws Exception {
        logger.info("[step-0] init testServer");
        final String serverHost = "localhost";
        final int serverPort = 4000;
        final File dataDir = new File("target/zkDataDir");
        final InstanceSpec instanceSpec = new InstanceSpec(dataDir, serverPort, -1, -1, true, -1, -1, 2);
        // System.setProperty("zk.servers", "localhost:" + instanceSpec.getPort());
        System.setProperty("zookeeper.serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");
        testingServer = new TestingServer(instanceSpec, false);
        testingServer.start();
    }

    // @After
    public void tiniTestServer() throws Exception {
        if (testingServer != null) {
            testingServer.close();
        }
        logger.info("[step-n] tini testServer");
    }

}
