package com.woodlogix.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WoodLogixServerApplicationTest {
    @LocalServerPort
    private int serverPort;

    @Test
    void startsWithAnEmbeddedWebServer() {
        assertTrue(serverPort > 0);
    }
}
