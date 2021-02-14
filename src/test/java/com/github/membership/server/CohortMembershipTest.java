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
import com.github.membership.domain.ListNodesRequest;
import com.github.membership.domain.ListNodesResponse;
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
import com.github.membership.domain.NodePersona;
import com.github.membership.domain.PurgeNamespaceRequest;
import com.github.membership.domain.PurgeNamespaceResponse;

public final class CohortMembershipTest {
    private static final Logger logger = LogManager.getLogger(CohortMembershipTest.class.getSimpleName());

    private TestingCluster testingCluster;
    private TestingServer testingServer;

    @Test
    public void testBasicJoin() throws Exception {
        MembershipService membershipService = null;
        try {
            membershipService = MembershipService.getService(testingCluster.getConnectString());
            membershipService.start();
            assertTrue(membershipService.isRunning());

            logger.info("[step-1] create namespace");
            final String namespace = "universe";
            final NewNamespaceRequest newNamespaceRequestOne = new NewNamespaceRequest();
            newNamespaceRequestOne.setNamespace(namespace);
            final NewNamespaceResponse newNamespaceResponseOne = membershipService.newNamespace(newNamespaceRequestOne);
            assertEquals("/" + namespace, newNamespaceResponseOne.getPath());
            assertTrue(newNamespaceResponseOne.isSuccess());

            logger.info("[step-2] create nodeOne");
            final NewNodeRequest newNodeRequestOne = new NewNodeRequest();
            newNodeRequestOne.setNamespace(namespace);
            newNodeRequestOne.setNodeId(UUID.randomUUID().toString());
            newNodeRequestOne.setPersona(NodePersona.COMPUTE);
            final String serverHostOne = "localhost";
            final int serverPortOne = 8000;
            newNodeRequestOne.setAddress(new InetSocketAddress(serverHostOne, serverPortOne));
            final NewNodeResponse newNodeResponseOne = membershipService.newNode(newNodeRequestOne);
            final Node nodeOne = newNodeResponseOne.getNode();
            assertNotNull(nodeOne);
            assertEquals("/" + namespace + "/nodes/" + nodeOne.getId(), nodeOne.getPath());

            logger.info("[step-3] create nodeTwo");
            final NewNodeRequest newNodeRequestTwo = new NewNodeRequest();
            newNodeRequestTwo.setNamespace(namespace);
            newNodeRequestTwo.setNodeId(UUID.randomUUID().toString());
            newNodeRequestTwo.setPersona(NodePersona.DATA_COMPUTE);
            final String serverHostTwo = "localhost";
            final int serverPortTwo = 8001;
            newNodeRequestOne.setAddress(new InetSocketAddress(serverHostTwo, serverPortTwo));
            final NewNodeResponse newNodeResponseTwo = membershipService.newNode(newNodeRequestTwo);
            final Node nodeTwo = newNodeResponseTwo.getNode();
            assertNotNull(nodeTwo);
            assertEquals("/" + namespace + "/nodes/" + nodeTwo.getId(), nodeTwo.getPath());

            logger.info("[step-4] create cohortTypeOne");
            final NewCohortTypeRequest newCohortTypeRequestOne = new NewCohortTypeRequest();
            newCohortTypeRequestOne.setNamespace(namespace);
            newCohortTypeRequestOne.setCohortType(CohortType.ONE);
            final NewCohortTypeResponse newCohortTypeResponseOne = membershipService.newCohortType(newCohortTypeRequestOne);
            assertTrue(newCohortTypeResponseOne.isSuccess());
            assertEquals(CohortType.ONE, newCohortTypeResponseOne.getCohortType());
            assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestOne.getCohortType().name(), newCohortTypeResponseOne.getPath());

            logger.info("[step-5] create cohortTypeTwo");
            final NewCohortTypeRequest newCohortTypeRequestTwo = new NewCohortTypeRequest();
            newCohortTypeRequestTwo.setNamespace(namespace);
            newCohortTypeRequestTwo.setCohortType(CohortType.TWO);
            final NewCohortTypeResponse newCohortTypeResponseTwo = membershipService.newCohortType(newCohortTypeRequestTwo);
            assertTrue(newCohortTypeResponseTwo.isSuccess());
            assertEquals(CohortType.TWO, newCohortTypeResponseTwo.getCohortType());
            assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestTwo.getCohortType().name(), newCohortTypeResponseTwo.getPath());

            logger.info("[step-6] create cohortOne");
            final NewCohortRequest newCohortRequestOne = new NewCohortRequest();
            newCohortRequestOne.setNamespace(namespace);
            newCohortRequestOne.setCohortId(UUID.randomUUID().toString());
            newCohortRequestOne.setCohortType(CohortType.ONE);
            final NewCohortResponse newCohortResponseOne = membershipService.newCohort(newCohortRequestOne);
            final Cohort cohortOne = newCohortResponseOne.getCohort();
            assertNotNull(cohortOne);
            assertEquals("/" + namespace + "/cohorts/" + newCohortRequestOne.getCohortType().name() + "/" + cohortOne.getId(), cohortOne.getPath());

            logger.info("[step-7] create cohortTwo");
            final NewCohortRequest newCohortRequestTwo = new NewCohortRequest();
            newCohortRequestTwo.setNamespace(namespace);
            newCohortRequestTwo.setCohortId(UUID.randomUUID().toString());
            newCohortRequestTwo.setCohortType(CohortType.TWO);
            final NewCohortResponse newCohortResponseTwo = membershipService.newCohort(newCohortRequestTwo);
            final Cohort cohortTwo = newCohortResponseTwo.getCohort();
            assertNotNull(cohortTwo);
            assertEquals("/" + namespace + "/cohorts/" + newCohortRequestTwo.getCohortType().name() + "/" + cohortTwo.getId(), cohortTwo.getPath());

            logger.info("[step-8] list cohorts, check for cohortOne and cohortTwo");
            final ListCohortsRequest listCohortsRequestOne = new ListCohortsRequest();
            listCohortsRequestOne.setNamespace(namespace);
            final ListCohortsResponse listCohortsResponseOne = membershipService.listCohorts(listCohortsRequestOne);
            assertEquals(2, listCohortsResponseOne.getCohorts().size());
            assertTrue(listCohortsResponseOne.getCohorts().contains(cohortOne));
            assertTrue(listCohortsResponseOne.getCohorts().contains(cohortTwo));

            logger.info("[step-9] memberOne joins cohortOne");
            final JoinCohortRequest joinCohortRequestOne = new JoinCohortRequest();
            joinCohortRequestOne.setNamespace(namespace);
            joinCohortRequestOne.setCohortId(cohortOne.getId());
            joinCohortRequestOne.setCohortType(cohortOne.getType());
            joinCohortRequestOne.setNodeId(nodeOne.getId());
            joinCohortRequestOne.setMemberId(UUID.randomUUID().toString());
            final JoinCohortResponse joinCohortResponseOne = membershipService.joinCohort(joinCohortRequestOne);
            final Member memberOne = joinCohortResponseOne.getMember();
            assertNotNull(memberOne);
            assertEquals(cohortOne.getPath() + "/members/" + memberOne.getMemberId(), memberOne.getPath());

            logger.info("[step-10] memberTwo joins cohortOne");
            final JoinCohortRequest joinCohortRequestTwo = new JoinCohortRequest();
            joinCohortRequestTwo.setNamespace(namespace);
            joinCohortRequestTwo.setCohortId(cohortOne.getId());
            joinCohortRequestTwo.setCohortType(cohortOne.getType());
            joinCohortRequestTwo.setNodeId(nodeTwo.getId());
            joinCohortRequestTwo.setMemberId(UUID.randomUUID().toString());
            final JoinCohortResponse joinCohortResponseTwo = membershipService.joinCohort(joinCohortRequestTwo);
            final Member memberTwo = joinCohortResponseTwo.getMember();
            assertNotNull(memberTwo);
            assertEquals(cohortOne.getPath() + "/members/" + memberTwo.getMemberId(), memberTwo.getPath());

            logger.info("[step-11] describe cohortOne, check for memberOne and memberTwo");
            final DescribeCohortRequest describeCohortRequestOne = new DescribeCohortRequest();
            describeCohortRequestOne.setNamespace(namespace);
            describeCohortRequestOne.setCohortId(cohortOne.getId());
            describeCohortRequestOne.setCohortType(cohortOne.getType());
            final DescribeCohortResponse describeCohortResponseOne = membershipService.describeCohort(describeCohortRequestOne);
            assertEquals(cohortOne.getId(), describeCohortResponseOne.getCohortId());
            assertEquals(cohortOne.getType(), describeCohortResponseOne.getCohortType());
            List<Member> members = describeCohortResponseOne.getMembers();
            assertEquals(2, members.size());
            final List<String> memberIds = new ArrayList<>();
            for (final Member member : members) {
                memberIds.add(member.getMemberId());
            }
            assertTrue(memberIds.contains(memberOne.getMemberId()));
            assertTrue(memberIds.contains(memberTwo.getMemberId()));

            logger.info("[step-12] describe cohortTwo, should have no members");
            final DescribeCohortRequest describeCohortRequestTwo = new DescribeCohortRequest();
            describeCohortRequestTwo.setNamespace(namespace);
            describeCohortRequestTwo.setCohortId(cohortTwo.getId());
            describeCohortRequestTwo.setCohortType(cohortTwo.getType());
            final DescribeCohortResponse describeCohortResponseTwo = membershipService.describeCohort(describeCohortRequestTwo);
            assertEquals(cohortTwo.getId(), describeCohortResponseTwo.getCohortId());
            assertEquals(cohortTwo.getType(), describeCohortResponseTwo.getCohortType());
            members = describeCohortResponseTwo.getMembers();
            assertEquals(0, members.size());

            logger.info("[step-13] memberTwo leaves cohortOne");
            final LeaveCohortRequest leaveCohortRequestOne = new LeaveCohortRequest();
            leaveCohortRequestOne.setNamespace(namespace);
            leaveCohortRequestOne.setCohortId(cohortOne.getId());
            leaveCohortRequestOne.setCohortType(cohortOne.getType());
            leaveCohortRequestOne.setMemberId(memberTwo.getMemberId());
            final LeaveCohortResponse leaveCohortResponseOne = membershipService.leaveCohort(leaveCohortRequestOne);
            assertTrue(leaveCohortResponseOne.isSuccess());

            logger.info("[step-14] describe cohortOne, check for presence of memberOne only");
            final DescribeCohortRequest describeCohortRequestThree = new DescribeCohortRequest();
            describeCohortRequestThree.setNamespace(namespace);
            describeCohortRequestThree.setCohortId(cohortOne.getId());
            describeCohortRequestThree.setCohortType(cohortOne.getType());
            final DescribeCohortResponse describeCohortResponseThree = membershipService.describeCohort(describeCohortRequestThree);
            assertEquals(cohortOne.getId(), describeCohortResponseThree.getCohortId());
            assertEquals(cohortOne.getType(), describeCohortResponseThree.getCohortType());
            members = describeCohortResponseThree.getMembers();
            assertEquals(1, members.size());
            assertEquals(memberOne.getMemberId(), members.get(0).getMemberId());

            logger.info("[step-15] delete cohortTwo");
            final DeleteCohortRequest deleteCohortRequestOne = new DeleteCohortRequest();
            deleteCohortRequestOne.setNamespace(namespace);
            deleteCohortRequestOne.setCohortId(cohortTwo.getId());
            deleteCohortRequestOne.setCohortType(cohortTwo.getType());
            final DeleteCohortResponse deleteCohortResponseOne = membershipService.deleteCohort(deleteCohortRequestOne);
            assertEquals(cohortTwo.getId(), deleteCohortResponseOne.getCohortId());
            assertTrue(deleteCohortResponseOne.isSuccess());

            logger.info("[step-16] delete cohortOne");
            final DeleteCohortRequest deleteCohortRequestTwo = new DeleteCohortRequest();
            deleteCohortRequestTwo.setNamespace(namespace);
            deleteCohortRequestTwo.setCohortId(cohortOne.getId());
            deleteCohortRequestTwo.setCohortType(cohortOne.getType());
            final DeleteCohortResponse deleteCohortResponseTwo = membershipService.deleteCohort(deleteCohortRequestTwo);
            assertEquals(cohortOne.getId(), deleteCohortResponseTwo.getCohortId());
            assertTrue(deleteCohortResponseTwo.isSuccess());

            logger.info("[step-17] delete cohortTypeOne");
            final DeleteCohortTypeRequest deleteCohortTypeRequestOne = new DeleteCohortTypeRequest();
            deleteCohortTypeRequestOne.setNamespace(namespace);
            deleteCohortTypeRequestOne.setCohortType(cohortOne.getType());
            final DeleteCohortTypeResponse deleteCohortTypeResponseOne = membershipService.deleteCohortType(deleteCohortTypeRequestOne);
            assertEquals(cohortOne.getType(), deleteCohortTypeResponseOne.getCohortType());
            assertTrue(deleteCohortTypeResponseOne.isSuccess());

            logger.info("[step-18] delete nodeOne");
            final DeleteNodeRequest deleteNodeRequestOne = new DeleteNodeRequest();
            deleteNodeRequestOne.setNamespace(namespace);
            deleteNodeRequestOne.setNodeId(nodeOne.getId());
            final DeleteNodeResponse deleteNodeResponseOne = membershipService.deleteNode(deleteNodeRequestOne);
            assertTrue(deleteNodeResponseOne.isSuccess());

            logger.info("[step-19] delete nodeTwo");
            final DeleteNodeRequest deleteNodeRequestTwo = new DeleteNodeRequest();
            deleteNodeRequestTwo.setNamespace(namespace);
            deleteNodeRequestTwo.setNodeId(nodeTwo.getId());
            final DeleteNodeResponse deleteNodeResponseTwo = membershipService.deleteNode(deleteNodeRequestTwo);
            assertTrue(deleteNodeResponseTwo.isSuccess());

            logger.info("[step-20] purge namespace");
            final PurgeNamespaceRequest purgeNamespaceRequestOne = new PurgeNamespaceRequest();
            purgeNamespaceRequestOne.setNamespace(namespace);
            final PurgeNamespaceResponse purgeNamespaceResponseOne = membershipService.purgeNamespace(purgeNamespaceRequestOne);
            assertEquals(namespace, purgeNamespaceResponseOne.getNamespace());
            assertTrue(purgeNamespaceResponseOne.isSuccess());
        } finally {
            if (membershipService != null && membershipService.isRunning()) {
                membershipService.stop();
                assertFalse(membershipService.isRunning());
            }
        }
    }

    @Test
    public void testNodeDeath() throws Exception {
        MembershipService membershipServiceOne = null;
        MembershipService membershipServiceTwo = null;
        try {
            membershipServiceOne = MembershipService.getService(testingCluster.getConnectString());
            membershipServiceOne.start();
            assertTrue(membershipServiceOne.isRunning());

            membershipServiceTwo = MembershipService.getService(testingCluster.getConnectString());
            membershipServiceTwo.start();
            assertTrue(membershipServiceTwo.isRunning());

            // TODO
            logger.info("[step-1] create namespace");
            final String namespace = "universe";
            final NewNamespaceRequest newNamespaceRequestOne = new NewNamespaceRequest();
            newNamespaceRequestOne.setNamespace(namespace);
            final NewNamespaceResponse newNamespaceResponseOne = membershipServiceOne.newNamespace(newNamespaceRequestOne);
            assertEquals("/" + namespace, newNamespaceResponseOne.getPath());
            assertTrue(newNamespaceResponseOne.isSuccess());

            logger.info("[step-2] create nodeOne");
            final NewNodeRequest newNodeRequestOne = new NewNodeRequest();
            newNodeRequestOne.setNamespace(namespace);
            newNodeRequestOne.setNodeId(UUID.randomUUID().toString());
            newNodeRequestOne.setPersona(NodePersona.COMPUTE);
            final String serverHostOne = "localhost";
            final int serverPortOne = 8000;
            newNodeRequestOne.setAddress(new InetSocketAddress(serverHostOne, serverPortOne));
            final NewNodeResponse newNodeResponseOne = membershipServiceOne.newNode(newNodeRequestOne);
            final Node nodeOne = newNodeResponseOne.getNode();
            assertNotNull(nodeOne);
            assertEquals("/" + namespace + "/nodes/" + nodeOne.getId(), nodeOne.getPath());

            logger.info("[step-3] create nodeTwo");
            final NewNodeRequest newNodeRequestTwo = new NewNodeRequest();
            newNodeRequestTwo.setNamespace(namespace);
            newNodeRequestTwo.setNodeId(UUID.randomUUID().toString());
            newNodeRequestTwo.setPersona(NodePersona.DATA_COMPUTE);
            final String serverHostTwo = "localhost";
            final int serverPortTwo = 8001;
            newNodeRequestOne.setAddress(new InetSocketAddress(serverHostTwo, serverPortTwo));
            final NewNodeResponse newNodeResponseTwo = membershipServiceTwo.newNode(newNodeRequestTwo);
            final Node nodeTwo = newNodeResponseTwo.getNode();
            assertNotNull(nodeTwo);
            assertEquals("/" + namespace + "/nodes/" + nodeTwo.getId(), nodeTwo.getPath());

            logger.info("[step-4] create cohortTypeOne");
            final NewCohortTypeRequest newCohortTypeRequestOne = new NewCohortTypeRequest();
            newCohortTypeRequestOne.setNamespace(namespace);
            newCohortTypeRequestOne.setCohortType(CohortType.ONE);
            final NewCohortTypeResponse newCohortTypeResponseOne = membershipServiceOne.newCohortType(newCohortTypeRequestOne);
            assertTrue(newCohortTypeResponseOne.isSuccess());
            assertEquals(CohortType.ONE, newCohortTypeResponseOne.getCohortType());
            assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestOne.getCohortType().name(), newCohortTypeResponseOne.getPath());

            logger.info("[step-5] create cohortOne");
            final NewCohortRequest newCohortRequestOne = new NewCohortRequest();
            newCohortRequestOne.setNamespace(namespace);
            newCohortRequestOne.setCohortId(UUID.randomUUID().toString());
            newCohortRequestOne.setCohortType(CohortType.ONE);
            final NewCohortResponse newCohortResponseOne = membershipServiceOne.newCohort(newCohortRequestOne);
            final Cohort cohortOne = newCohortResponseOne.getCohort();
            assertNotNull(cohortOne);
            assertEquals("/" + namespace + "/cohorts/" + newCohortRequestOne.getCohortType().name() + "/" + cohortOne.getId(), cohortOne.getPath());

            logger.info("[step-6] list cohorts, check for cohortOne");
            final ListCohortsRequest listCohortsRequestOne = new ListCohortsRequest();
            listCohortsRequestOne.setNamespace(namespace);
            final ListCohortsResponse listCohortsResponseOne = membershipServiceOne.listCohorts(listCohortsRequestOne);
            assertEquals(1, listCohortsResponseOne.getCohorts().size());
            assertTrue(listCohortsResponseOne.getCohorts().contains(cohortOne));

            logger.info("[step-7] memberOne joins cohortOne");
            final JoinCohortRequest joinCohortRequestOne = new JoinCohortRequest();
            joinCohortRequestOne.setNamespace(namespace);
            joinCohortRequestOne.setCohortId(cohortOne.getId());
            joinCohortRequestOne.setCohortType(cohortOne.getType());
            joinCohortRequestOne.setNodeId(nodeOne.getId());
            joinCohortRequestOne.setMemberId(UUID.randomUUID().toString());
            final JoinCohortResponse joinCohortResponseOne = membershipServiceOne.joinCohort(joinCohortRequestOne);
            final Member memberOne = joinCohortResponseOne.getMember();
            assertNotNull(memberOne);
            assertEquals(cohortOne.getPath() + "/members/" + memberOne.getMemberId(), memberOne.getPath());

            logger.info("[step-8] memberTwo joins cohortOne");
            final JoinCohortRequest joinCohortRequestTwo = new JoinCohortRequest();
            joinCohortRequestTwo.setNamespace(namespace);
            joinCohortRequestTwo.setCohortId(cohortOne.getId());
            joinCohortRequestTwo.setCohortType(cohortOne.getType());
            joinCohortRequestTwo.setNodeId(nodeTwo.getId());
            joinCohortRequestTwo.setMemberId(UUID.randomUUID().toString());
            final JoinCohortResponse joinCohortResponseTwo = membershipServiceTwo.joinCohort(joinCohortRequestTwo);
            final Member memberTwo = joinCohortResponseTwo.getMember();
            assertNotNull(memberTwo);
            assertEquals(cohortOne.getPath() + "/members/" + memberTwo.getMemberId(), memberTwo.getPath());

            logger.info("[step-9] describe cohortOne, check for memberOne and memberTwo");
            final DescribeCohortRequest describeCohortRequestOne = new DescribeCohortRequest();
            describeCohortRequestOne.setNamespace(namespace);
            describeCohortRequestOne.setCohortId(cohortOne.getId());
            describeCohortRequestOne.setCohortType(cohortOne.getType());
            final DescribeCohortResponse describeCohortResponseOne = membershipServiceOne.describeCohort(describeCohortRequestOne);
            assertEquals(cohortOne.getId(), describeCohortResponseOne.getCohortId());
            assertEquals(cohortOne.getType(), describeCohortResponseOne.getCohortType());
            List<Member> members = describeCohortResponseOne.getMembers();
            assertEquals(2, members.size());
            final List<String> memberIds = new ArrayList<>();
            for (final Member member : members) {
                memberIds.add(member.getMemberId());
            }
            assertTrue(memberIds.contains(memberOne.getMemberId()));
            assertTrue(memberIds.contains(memberTwo.getMemberId()));

            logger.info("[step-10] list nodes, check for nodeOne and nodeTwo");
            final ListNodesRequest listNodesRequestOne = new ListNodesRequest();
            listNodesRequestOne.setNamespace(namespace);
            final ListNodesResponse listNodesResponseOne = membershipServiceOne.listNodes(listNodesRequestOne);
            assertEquals(2, listNodesResponseOne.getNodes().size());

            logger.info("[step-12] stop membershipServiceTwo, memberTwo should've left cohortOne");
            membershipServiceTwo.stop();

            logger.info("[step-13] describe cohortOne, check for presence of memberOne only");
            final DescribeCohortRequest describeCohortRequestThree = new DescribeCohortRequest();
            describeCohortRequestThree.setNamespace(namespace);
            describeCohortRequestThree.setCohortId(cohortOne.getId());
            describeCohortRequestThree.setCohortType(cohortOne.getType());
            final DescribeCohortResponse describeCohortResponseThree = membershipServiceOne.describeCohort(describeCohortRequestThree);
            assertEquals(cohortOne.getId(), describeCohortResponseThree.getCohortId());
            assertEquals(cohortOne.getType(), describeCohortResponseThree.getCohortType());
            members = describeCohortResponseThree.getMembers();
            assertEquals(1, members.size());
            assertEquals(memberOne.getMemberId(), members.get(0).getMemberId());

            logger.info("[step-14] list nodes, check for nodeOne only");
            final ListNodesRequest listNodesRequestTwo = new ListNodesRequest();
            listNodesRequestTwo.setNamespace(namespace);
            final ListNodesResponse listNodesResponseTwo = membershipServiceOne.listNodes(listNodesRequestTwo);
            assertEquals(1, listNodesResponseTwo.getNodes().size());
            assertTrue(listNodesResponseTwo.getNodes().get(0).getId().equals(nodeOne.getId()));

            logger.info("[step-15] purge namespace");
            final PurgeNamespaceRequest purgeNamespaceRequestOne = new PurgeNamespaceRequest();
            purgeNamespaceRequestOne.setNamespace(namespace);
            final PurgeNamespaceResponse purgeNamespaceResponseOne = membershipServiceOne.purgeNamespace(purgeNamespaceRequestOne);
            assertEquals(namespace, purgeNamespaceResponseOne.getNamespace());
            assertTrue(purgeNamespaceResponseOne.isSuccess());
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
            MembershipService membershipService = MembershipService.getService(testingCluster.getConnectString());
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
