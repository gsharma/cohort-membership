package com.github.membership;

import java.io.File;

import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.Test;

public class CohortMembershipTest {

    @Test
    public void testServerLifecycle() throws Exception {
        final int serverPort = 5000;
        final TestingServer server = new TestingServer(serverPort, new File("/tmp/zkDataDir"));
        server.close();
    }
}
