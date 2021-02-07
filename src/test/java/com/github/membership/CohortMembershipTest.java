package com.github.membership;

import java.io.File;

import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public final class CohortMembershipTest {
    private static final Logger logger = LogManager.getLogger(CohortMembershipTest.class.getSimpleName());

    private TestingServer testingServer;

    @Test
    public void testServerLifecycle() throws Exception {
        final int serverPort = 4000;
        final File dataDir = new File("target/zkDataDir");
        final InstanceSpec instanceSpec = new InstanceSpec(dataDir, serverPort, -1, -1, true, -1, -1, 2);
        System.setProperty("zk.servers", "localhost:" + instanceSpec.getPort());
        System.setProperty("zookeeper.serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");
        TestingServer server = null;
        try {
            server = new TestingServer(instanceSpec, false);
            server.start();
        } finally {
            if (server != null) {
                server.close();
            }
        }
    }

    public void initServer() throws Exception {
        final int serverPort = 4000;
        final File dataDir = new File("target/zkDataDir");
        final InstanceSpec instanceSpec = new InstanceSpec(dataDir, serverPort, -1, -1, true, -1, -1, 2);
        System.setProperty("zk.servers", "localhost:" + instanceSpec.getPort());
        System.setProperty("zookeeper.serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");
        testingServer = new TestingServer(instanceSpec, false);
        testingServer.start();
    }

    public void tiniServer() throws Exception {
        if (testingServer != null) {
            testingServer.close();
        }
    }

}
