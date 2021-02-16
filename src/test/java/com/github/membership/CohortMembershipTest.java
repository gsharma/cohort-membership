package com.github.membership;

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
import java.util.concurrent.TimeUnit;

import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.TestingZooKeeperServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.github.membership.client.MembershipClient;
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
import com.github.membership.server.MembershipDelegate;
import com.github.membership.server.MembershipServer;
import com.github.membership.server.MembershipServerConfiguration;

/**
 * Tests to maintain the sanity of MembershipService.
 */
public final class CohortMembershipTest {
    private static final Logger logger = LogManager.getLogger(CohortMembershipTest.class.getSimpleName());

    private TestingCluster zkCluster;
    private TestingServer zkServer;

    @Test
    public void testBasicJoin() throws Exception {
        MembershipServer membershipService = null;
        MembershipClient client = null;
        try {
            final MembershipServerConfiguration serviceConfig = new MembershipServerConfiguration();
            serviceConfig.setConnectString(zkCluster.getConnectString());
            serviceConfig.setServerHost("localhost");
            serviceConfig.setServerPort(5001);
            serviceConfig.setWorkerCount(2);
            serviceConfig.setClientSessionTimeoutMillis(60 * 1000);
            serviceConfig.setClientSessionEstablishmentTimeoutSeconds(3L);
            membershipService = new MembershipServer(serviceConfig);
            membershipService.start();
            assertTrue(membershipService.isRunning());

            client = MembershipClient.getClient("localhost", 5001, 1L, 1);
            client.start();
            assertTrue(client.isRunning());

            logger.info("[step-1] create namespace");
            final String namespace = "testBasicJoin";
            final NewNamespaceRequest newNamespaceRequestOne = NewNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            final NewNamespaceResponse newNamespaceResponseOne = client.newNamespace(newNamespaceRequestOne);
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
            final NewNodeResponse newNodeResponseOne = client.newNode(newNodeRequestOne);
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
            final NewNodeResponse newNodeResponseTwo = client.newNode(newNodeRequestTwo);
            final Node nodeTwo = newNodeResponseTwo.getNode();
            assertNotNull(nodeTwo);
            assertEquals("/" + namespace + "/nodes/" + nodeTwo.getId(), nodeTwo.getPath());

            logger.info("[step-4] create cohortTypeOne");
            final NewCohortTypeRequest newCohortTypeRequestOne = NewCohortTypeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortType(CohortType.ONE).build();
            final NewCohortTypeResponse newCohortTypeResponseOne = client.newCohortType(newCohortTypeRequestOne);
            assertTrue(newCohortTypeResponseOne.getSuccess());
            // assertEquals(CohortType.ONE, newCohortTypeResponseOne.getCohortType());
            // assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestOne.getCohortType().name(), newCohortTypeResponseOne.getPath());

            logger.info("[step-5] create cohortTypeTwo");
            final NewCohortTypeRequest newCohortTypeRequestTwo = NewCohortTypeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortType(CohortType.TWO).build();
            final NewCohortTypeResponse newCohortTypeResponseTwo = client.newCohortType(newCohortTypeRequestTwo);
            assertTrue(newCohortTypeResponseTwo.getSuccess());
            // assertEquals(CohortType.TWO, newCohortTypeResponseTwo.getCohortType());
            // assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestTwo.getCohortType().name(), newCohortTypeResponseTwo.getPath());

            logger.info("[step-6] create cohortOne");
            final NewCohortRequest newCohortRequestOne = NewCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(UUID.randomUUID().toString())
                    .setCohortType(CohortType.ONE).build();
            final NewCohortResponse newCohortResponseOne = client.newCohort(newCohortRequestOne);
            final Cohort cohortOne = newCohortResponseOne.getCohort();
            assertNotNull(cohortOne);
            assertEquals("/" + namespace + "/cohorts/" + newCohortRequestOne.getCohortType().name() + "/" + cohortOne.getId(), cohortOne.getPath());

            logger.info("[step-7] create cohortTwo");
            final NewCohortRequest newCohortRequestTwo = NewCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(UUID.randomUUID().toString())
                    .setCohortType(CohortType.TWO).build();
            final NewCohortResponse newCohortResponseTwo = client.newCohort(newCohortRequestTwo);
            final Cohort cohortTwo = newCohortResponseTwo.getCohort();
            assertNotNull(cohortTwo);
            assertEquals("/" + namespace + "/cohorts/" + newCohortRequestTwo.getCohortType().name() + "/" + cohortTwo.getId(), cohortTwo.getPath());

            logger.info("[step-8] list cohorts, check for cohortOne and cohortTwo");
            final ListCohortsRequest listCohortsRequestOne = ListCohortsRequest.newBuilder()
                    .setNamespace(namespace).build();
            final ListCohortsResponse listCohortsResponseOne = client.listCohorts(listCohortsRequestOne);
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
            final JoinCohortResponse joinCohortResponseOne = client.joinCohort(joinCohortRequestOne);
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
            final JoinCohortResponse joinCohortResponseTwo = client.joinCohort(joinCohortRequestTwo);
            assertEquals(2, joinCohortResponseTwo.getCohort().getMembersList().size());
            // final Member memberTwo = joinCohortResponseTwo.getMember();
            // assertNotNull(memberTwo);
            // assertEquals(cohortOne.getPath() + "/members/" + memberTwo.getMemberId(), memberTwo.getPath());

            logger.info("[step-11] describe cohortOne, check for memberOne and memberTwo");
            final DescribeCohortRequest describeCohortRequestOne = DescribeCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType()).build();
            final DescribeCohortResponse describeCohortResponseOne = client.describeCohort(describeCohortRequestOne);
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
            final DescribeCohortResponse describeCohortResponseTwo = client.describeCohort(describeCohortRequestTwo);
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
            final LeaveCohortResponse leaveCohortResponseOne = client.leaveCohort(leaveCohortRequestOne);
            assertTrue(leaveCohortResponseOne.getSuccess());

            logger.info("[step-14] describe cohortOne, check for presence of memberOne only");
            final DescribeCohortRequest describeCohortRequestThree = DescribeCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType()).build();
            final DescribeCohortResponse describeCohortResponseThree = client.describeCohort(describeCohortRequestThree);
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
            final DeleteCohortResponse deleteCohortResponseOne = client.deleteCohort(deleteCohortRequestOne);
            // assertEquals(cohortTwo.getId(), deleteCohortResponseOne.getCohortId());
            assertTrue(deleteCohortResponseOne.getSuccess());

            logger.info("[step-16] delete cohortOne");
            final DeleteCohortRequest deleteCohortRequestTwo = DeleteCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType()).build();
            final DeleteCohortResponse deleteCohortResponseTwo = client.deleteCohort(deleteCohortRequestTwo);
            // assertEquals(cohortOne.getId(), deleteCohortResponseTwo.getCohortId());
            assertTrue(deleteCohortResponseTwo.getSuccess());

            logger.info("[step-17] delete cohortTypeOne");
            final DeleteCohortTypeRequest deleteCohortTypeRequestOne = DeleteCohortTypeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortType(cohortOne.getType()).build();
            final DeleteCohortTypeResponse deleteCohortTypeResponseOne = client.deleteCohortType(deleteCohortTypeRequestOne);
            // assertEquals(cohortOne.getType(), deleteCohortTypeResponseOne.getCohortType());
            assertTrue(deleteCohortTypeResponseOne.getSuccess());

            logger.info("[step-18] delete nodeOne");
            final DeleteNodeRequest deleteNodeRequestOne = DeleteNodeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setNodeId(nodeOne.getId()).build();
            final DeleteNodeResponse deleteNodeResponseOne = client.deleteNode(deleteNodeRequestOne);
            assertTrue(deleteNodeResponseOne.getSuccess());

            logger.info("[step-19] delete nodeTwo");
            final DeleteNodeRequest deleteNodeRequestTwo = DeleteNodeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setNodeId(nodeTwo.getId()).build();
            final DeleteNodeResponse deleteNodeResponseTwo = client.deleteNode(deleteNodeRequestTwo);
            assertTrue(deleteNodeResponseTwo.getSuccess());

            logger.info("[step-20] purge namespace");
            final PurgeNamespaceRequest purgeNamespaceRequestOne = PurgeNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            final PurgeNamespaceResponse purgeNamespaceResponseOne = client.purgeNamespace(purgeNamespaceRequestOne);
            // assertEquals(namespace, purgeNamespaceResponseOne.getNamespace());
            assertTrue(purgeNamespaceResponseOne.getSuccess());
        } finally {
            if (client != null && client.isRunning()) {
                client.stop();
                assertFalse(client.isRunning());
            }
            if (membershipService != null && membershipService.isRunning()) {
                membershipService.stop();
                assertFalse(membershipService.isRunning());
            }
        }
    }

    @Test
    public void testNodeDeath() throws Exception {
        MembershipServer membershipServiceOne = null;
        MembershipServer membershipServiceTwo = null;
        MembershipClient clientOne = null;
        MembershipClient clientTwo = null;
        try {
            final MembershipServerConfiguration serviceConfigOne = new MembershipServerConfiguration();
            serviceConfigOne.setConnectString(zkCluster.getConnectString());
            serviceConfigOne.setServerHost("localhost");
            serviceConfigOne.setServerPort(4001);
            serviceConfigOne.setWorkerCount(2);
            serviceConfigOne.setClientSessionTimeoutMillis(60 * 1000);
            serviceConfigOne.setClientSessionEstablishmentTimeoutSeconds(3L);
            membershipServiceOne = new MembershipServer(serviceConfigOne);
            membershipServiceOne.start();
            assertTrue(membershipServiceOne.isRunning());

            clientOne = MembershipClient.getClient("localhost", 4001, 1L, 1);
            clientOne.start();
            assertTrue(clientOne.isRunning());

            final MembershipServerConfiguration serviceConfigTwo = new MembershipServerConfiguration();
            serviceConfigTwo.setConnectString(zkCluster.getConnectString());
            serviceConfigTwo.setServerHost("localhost");
            serviceConfigTwo.setServerPort(4002);
            serviceConfigTwo.setWorkerCount(2);
            serviceConfigTwo.setClientSessionTimeoutMillis(60 * 1000);
            serviceConfigTwo.setClientSessionEstablishmentTimeoutSeconds(3L);
            membershipServiceTwo = new MembershipServer(serviceConfigTwo);
            membershipServiceTwo.start();
            assertTrue(membershipServiceTwo.isRunning());

            clientTwo = MembershipClient.getClient("localhost", 4002, 1L, 1);
            clientTwo.start();
            assertTrue(clientTwo.isRunning());

            logger.info("[step-1] create namespace");
            final String namespace = "testNodeDeath";
            final NewNamespaceRequest newNamespaceRequestOne = NewNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            final NewNamespaceResponse newNamespaceResponseOne = clientOne.newNamespace(newNamespaceRequestOne);
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
            final NewNodeResponse newNodeResponseOne = clientOne.newNode(newNodeRequestOne);
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
            final NewNodeResponse newNodeResponseTwo = clientTwo.newNode(newNodeRequestTwo);
            final Node nodeTwo = newNodeResponseTwo.getNode();
            assertNotNull(nodeTwo);
            assertEquals("/" + namespace + "/nodes/" + nodeTwo.getId(), nodeTwo.getPath());

            logger.info("[step-4] create cohortTypeOne");
            final NewCohortTypeRequest newCohortTypeRequestOne = NewCohortTypeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortType(CohortType.ONE).build();
            final NewCohortTypeResponse newCohortTypeResponseOne = clientOne.newCohortType(newCohortTypeRequestOne);
            assertTrue(newCohortTypeResponseOne.getSuccess());
            // assertEquals(CohortType.ONE, newCohortTypeResponseOne.getCohortType());
            // assertEquals("/" + namespace + "/cohorts/" + newCohortTypeRequestOne.getCohortType().name(), newCohortTypeResponseOne.getPath());

            logger.info("[step-5] create cohortOne");
            final NewCohortRequest newCohortRequestOne = NewCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(UUID.randomUUID().toString())
                    .setCohortType(CohortType.ONE).build();
            final NewCohortResponse newCohortResponseOne = clientOne.newCohort(newCohortRequestOne);
            final Cohort cohortOne = newCohortResponseOne.getCohort();
            assertNotNull(cohortOne);
            assertEquals("/" + namespace + "/cohorts/" + newCohortRequestOne.getCohortType().name() + "/" + cohortOne.getId(), cohortOne.getPath());

            logger.info("[step-6] list cohorts, check for cohortOne");
            final ListCohortsRequest listCohortsRequestOne = ListCohortsRequest.newBuilder()
                    .setNamespace(namespace).build();
            final ListCohortsResponse listCohortsResponseOne = clientOne.listCohorts(listCohortsRequestOne);
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
            final JoinCohortResponse joinCohortResponseOne = clientOne.joinCohort(joinCohortRequestOne);
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
            final JoinCohortResponse joinCohortResponseTwo = clientTwo.joinCohort(joinCohortRequestTwo);
            assertEquals(2, joinCohortResponseTwo.getCohort().getMembersList().size());
            // final Member memberTwo = joinCohortResponseTwo.getMember();
            // assertNotNull(memberTwo);
            // assertEquals(cohortOne.getPath() + "/members/" + memberTwo.getMemberId(), memberTwo.getPath());

            logger.info("[step-9] describe cohortOne, check for memberOne and memberTwo");
            final DescribeCohortRequest describeCohortRequestOne = DescribeCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType()).build();
            final DescribeCohortResponse describeCohortResponseOne = clientOne.describeCohort(describeCohortRequestOne);
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
            final ListNodesResponse listNodesResponseOne = clientOne.listNodes(listNodesRequestOne);
            assertEquals(2, listNodesResponseOne.getNodesList().size());

            logger.info("[step-11] stop membershipServiceTwo, memberTwo should've left cohortOne");
            membershipServiceTwo.stop();

            logger.info("[step-12] describe cohortOne, check for presence of memberOne only");
            final DescribeCohortRequest describeCohortRequestThree = DescribeCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType()).build();
            final DescribeCohortResponse describeCohortResponseThree = clientOne.describeCohort(describeCohortRequestThree);
            final Cohort describedCohortTwo = describeCohortResponseThree.getCohort();
            assertEquals(cohortOne.getId(), describedCohortTwo.getId());
            assertEquals(cohortOne.getType(), describedCohortTwo.getType());
            members = describedCohortTwo.getMembersList();
            assertEquals(1, members.size());
            assertEquals(memberOneId, members.get(0).getMemberId());

            logger.info("[step-13] list nodes, check for nodeOne only");
            final ListNodesRequest listNodesRequestTwo = ListNodesRequest.newBuilder()
                    .setNamespace(namespace).build();
            final ListNodesResponse listNodesResponseTwo = clientOne.listNodes(listNodesRequestTwo);
            assertEquals(1, listNodesResponseTwo.getNodesList().size());
            assertTrue(listNodesResponseTwo.getNodesList().get(0).getId().equals(nodeOne.getId()));

            logger.info("[step-14] purge namespace");
            final PurgeNamespaceRequest purgeNamespaceRequestOne = PurgeNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            final PurgeNamespaceResponse purgeNamespaceResponseOne = clientOne.purgeNamespace(purgeNamespaceRequestOne);
            // assertEquals(namespace, purgeNamespaceResponseOne.getNamespace());
            assertTrue(purgeNamespaceResponseOne.getSuccess());
        } finally {
            if (clientOne != null && clientOne.isRunning()) {
                clientOne.stop();
                assertFalse(clientOne.isRunning());
            }
            if (membershipServiceOne != null && membershipServiceOne.isRunning()) {
                membershipServiceOne.stop();
                assertFalse(membershipServiceOne.isRunning());
            }
            if (clientTwo != null && clientTwo.isRunning()) {
                clientTwo.stop();
                assertFalse(clientTwo.isRunning());
            }
            if (membershipServiceTwo != null && membershipServiceTwo.isRunning()) {
                membershipServiceTwo.stop();
                assertFalse(membershipServiceTwo.isRunning());
            }
        }
    }

    @Test
    public void testServiceDeath() throws Exception {
        MembershipServer membershipServiceOne = null;
        MembershipClient clientOne = null;
        try {
            final MembershipServerConfiguration serviceConfigOne = new MembershipServerConfiguration();
            serviceConfigOne.setConnectString(zkCluster.getConnectString());
            serviceConfigOne.setServerHost("localhost");
            serviceConfigOne.setServerPort(4001);
            serviceConfigOne.setWorkerCount(2);
            serviceConfigOne.setClientSessionTimeoutMillis(60 * 1000);
            serviceConfigOne.setClientSessionEstablishmentTimeoutSeconds(5L);
            membershipServiceOne = new MembershipServer(serviceConfigOne);
            membershipServiceOne.start();
            assertTrue(membershipServiceOne.isRunning());

            clientOne = MembershipClient.getClient("localhost", 4001, 2L, 1);
            clientOne.start();
            assertTrue(clientOne.isRunning());

            logger.info("[step-1] create namespace");
            String namespace = "testNodeDeath";
            NewNamespaceRequest newNamespaceRequestOne = NewNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            NewNamespaceResponse newNamespaceResponseOne = clientOne.newNamespace(newNamespaceRequestOne);
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
            final NewNodeResponse newNodeResponseOne = clientOne.newNode(newNodeRequestOne);
            final Node nodeOne = newNodeResponseOne.getNode();
            assertNotNull(nodeOne);
            assertEquals("/" + namespace + "/nodes/" + nodeOne.getId(), nodeOne.getPath());

            logger.info("[step-3] create cohortTypeOne");
            final NewCohortTypeRequest newCohortTypeRequestOne = NewCohortTypeRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortType(CohortType.ONE).build();
            final NewCohortTypeResponse newCohortTypeResponseOne = clientOne.newCohortType(newCohortTypeRequestOne);
            assertTrue(newCohortTypeResponseOne.getSuccess());

            logger.info("[step-4] create cohortOne");
            final NewCohortRequest newCohortRequestOne = NewCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(UUID.randomUUID().toString())
                    .setCohortType(CohortType.ONE).build();
            final NewCohortResponse newCohortResponseOne = clientOne.newCohort(newCohortRequestOne);
            final Cohort cohortOne = newCohortResponseOne.getCohort();
            assertNotNull(cohortOne);
            assertEquals("/" + namespace + "/cohorts/" + newCohortRequestOne.getCohortType().name() + "/" + cohortOne.getId(), cohortOne.getPath());

            logger.info("[step-5] list cohorts, check for cohortOne");
            final ListCohortsRequest listCohortsRequestOne = ListCohortsRequest.newBuilder()
                    .setNamespace(namespace).build();
            final ListCohortsResponse listCohortsResponseOne = clientOne.listCohorts(listCohortsRequestOne);
            assertEquals(1, listCohortsResponseOne.getCohortsList().size());
            assertTrue(listCohortsResponseOne.getCohortsList().contains(cohortOne));

            logger.info("[step-6] memberOne joins cohortOne");
            final JoinCohortRequest joinCohortRequestOne = JoinCohortRequest.newBuilder()
                    .setNamespace(namespace)
                    .setCohortId(cohortOne.getId())
                    .setCohortType(cohortOne.getType())
                    .setNodeId(nodeOne.getId())
                    .setMemberId(UUID.randomUUID().toString()).build();
            final String memberOneId = joinCohortRequestOne.getMemberId();
            final JoinCohortResponse joinCohortResponseOne = clientOne.joinCohort(joinCohortRequestOne);
            assertEquals(1, joinCohortResponseOne.getCohort().getMembersList().size());

            logger.info("[step-7] zk servers all drop dead");
            tiniZkCluster();
            // Thread.sleep(100L);

            logger.info("[step-8] zk servers all come back to life");
            initZkCluster();
            // since our zk ports are dynamic, update the connect string
            serviceConfigOne.setConnectString(zkCluster.getConnectString());

            logger.info("[step-9] restart membership service");
            membershipServiceOne.stop();
            assertFalse(membershipServiceOne.isRunning());

            membershipServiceOne = new MembershipServer(serviceConfigOne);
            membershipServiceOne.start();
            assertTrue(membershipServiceOne.isRunning());

            logger.info("[step-10] create namespace");
            namespace = "testNodeDeath";
            newNamespaceRequestOne = NewNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            newNamespaceResponseOne = clientOne.newNamespace(newNamespaceRequestOne);
            assertTrue(newNamespaceResponseOne.getSuccess());

            logger.info("[step-11] purge namespace");
            final PurgeNamespaceRequest purgeNamespaceRequestOne = PurgeNamespaceRequest.newBuilder()
                    .setNamespace(namespace).build();
            final PurgeNamespaceResponse purgeNamespaceResponseOne = clientOne.purgeNamespace(purgeNamespaceRequestOne);
            assertTrue(purgeNamespaceResponseOne.getSuccess());
        } finally {
            if (clientOne != null && clientOne.isRunning()) {
                clientOne.stop();
                assertFalse(clientOne.isRunning());
            }
            if (membershipServiceOne != null && membershipServiceOne.isRunning()) {
                membershipServiceOne.stop();
                assertFalse(membershipServiceOne.isRunning());
            }
        }
    }

    @Test
    public void testMembershipServiceLCM() throws Exception {
        for (int iter = 0; iter < 2; iter++) {
            final MembershipServerConfiguration configuration = new MembershipServerConfiguration();
            configuration.setConnectString(zkCluster.getConnectString());
            configuration.setServerHost("localhost");
            configuration.setServerPort(6001);
            configuration.setWorkerCount(2);
            configuration.setClientSessionTimeoutMillis(60 * 1000);
            configuration.setClientSessionEstablishmentTimeoutSeconds(3L);
            final MembershipServer membershipServer = new MembershipServer(configuration);
            membershipServer.start();
            assertTrue(membershipServer.isRunning());

            membershipServer.stop();
            assertFalse(membershipServer.isRunning());

            membershipServer.start();
            assertTrue(membershipServer.isRunning());

            membershipServer.stop();
            assertFalse(membershipServer.isRunning());
        }
    }

    @Before
    public void initZkCluster() throws Exception {
        final long startNanos = System.nanoTime();
        logger.info("[step-0] init zk cluster");
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
        zkCluster = new TestingCluster(3);
        zkCluster.start();

        final List<TestingZooKeeperServer> testServers = zkCluster.getServers();
        assertEquals(3, testServers.size());
        for (final TestingZooKeeperServer testServer : testServers) {
            assertTrue(testServer.getQuorumPeer().isRunning());
            // logger.info(testServer.getQuorumPeer());
        }
        logger.info("Started zk cluster {} in {} millis", zkCluster.getConnectString(),
                TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS));
    }

    @After
    public void tiniZkCluster() throws Exception {
        if (zkCluster != null) {
            logger.info("Stopping zk cluster {}", zkCluster.getConnectString());
            zkCluster.close();
        }
        logger.info("[step-n] tini zk cluster");
    }

    // @Before
    public void initZkServer() throws Exception {
        logger.info("[step-0] init zk server");
        final String serverHost = "localhost";
        final int serverPort = 4000;
        final File dataDir = new File("target/zkDataDir");
        final InstanceSpec instanceSpec = new InstanceSpec(dataDir, serverPort, -1, -1, true, -1, -1, 2);
        // System.setProperty("zk.servers", "localhost:" + instanceSpec.getPort());
        System.setProperty("zookeeper.serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");
        zkServer = new TestingServer(instanceSpec, false);
        zkServer.start();
    }

    // @After
    public void tiniZkServer() throws Exception {
        if (zkServer != null) {
            zkServer.close();
        }
        logger.info("[step-n] tini zk server");
    }

}
