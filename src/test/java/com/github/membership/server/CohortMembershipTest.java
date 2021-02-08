package com.github.membership.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class CohortMembershipTest {
    private static final Logger logger = LogManager.getLogger(CohortMembershipTest.class.getSimpleName());

    private final String serverHost = "localhost";
    private final int serverPort = 4000;
    private final File dataDir = new File("target/zkDataDir");
    private final String namespace = "test";

    private TestingServer testingServer;
    private MembershipService membershipService;

    @Test
    public void newCohortTest() throws Exception {
        final NewCohortRequest request = new NewCohortRequest();
        request.setCohortId(UUID.randomUUID().toString());
        request.setCohortType(CohortType.ONE);
        final NewCohortResponse response = membershipService.newCohort(request);
        final Cohort cohort = response.getCohort();
        assertNotNull(cohort);
        assertEquals("/" + namespace + "/cohorts/" + request.getCohortType().name() + "/" + cohort.getId(), cohort.getPath());
        logger.info(response);
    }

    @Before
    public void initServer() throws Exception {
        final InstanceSpec instanceSpec = new InstanceSpec(dataDir, serverPort, -1, -1, true, -1, -1, 2);
        System.setProperty("zk.servers", "localhost:" + instanceSpec.getPort());
        System.setProperty("zookeeper.serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");
        testingServer = new TestingServer(instanceSpec, false);
        testingServer.start();

        final List<InetSocketAddress> serverAddresses = Collections.singletonList(new InetSocketAddress(serverHost, serverPort));
        membershipService = MembershipService.getMembership(serverAddresses, namespace);
        membershipService.start();
        assertTrue(membershipService.isRunning());
    }

    @After
    public void tiniServer() throws Exception {
        if (membershipService != null) {
            membershipService.stop();
            assertFalse(membershipService.isRunning());
        }
        if (testingServer != null) {
            testingServer.close();
        }
    }

}
