/*
 * Copyright 2014-2026 Fraunhofer ISE
 *
 * This file is part of j60870.
 * For more information visit http://www.openmuc.org
 *
 * j60870 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * j60870 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with j60870.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.openmuc.j60870;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmuc.j60870.ie.IeSinglePointWithQuality;
import org.openmuc.j60870.ie.InformationObject;

class ConnectionDataTransferITest {

    private static final long DEFAULT_TIMEOUT_SECONDS = 5L;
    private static final long POLL_INTERVAL_MILLIS = 10L;

    private Server server;
    private Connection clientConnection;
    private Connection serverConnection;

    @BeforeEach
    void setUp() {
        server = null;
        clientConnection = null;
        serverConnection = null;
    }

    @AfterEach
    void tearDown() {
        if (clientConnection != null) {
            assertDoesNotThrow(() -> clientConnection.close());
        }
        if (serverConnection != null && serverConnection != clientConnection) {
            assertDoesNotThrow(() -> serverConnection.close());
        }
        if (server != null) {
            assertDoesNotThrow(() -> server.stop());
        }
    }

    @Test
    void clientInitiatedRestartMustRestoreClientListener() throws Exception {
        runRestartScenario(RestartInitiator.CLIENT, VerifiedListener.CLIENT);
    }

    @Test
    void clientInitiatedRestartMustRestoreServerListener() throws Exception {
        runRestartScenario(RestartInitiator.CLIENT, VerifiedListener.SERVER);
    }

    @Test
    void serverInitiatedRestartMustRestoreServerListener() throws Exception {
        runRestartScenario(RestartInitiator.SERVER, VerifiedListener.SERVER);
    }

    @Test
    void serverInitiatedRestartMustRestoreClientListener() throws Exception {
        runRestartScenario(RestartInitiator.SERVER, VerifiedListener.CLIENT);
    }

    private void runRestartScenario(RestartInitiator restartInitiator, VerifiedListener verifiedListener)
            throws Exception {

        int port = findFreePort();
        InetAddress bindAddress = InetAddress.getByName("127.0.0.1");

        CountDownLatch serverConnectionAccepted = new CountDownLatch(1);

        ListenerProbe clientProbe = new ListenerProbe();
        ListenerProbe serverProbe = new ListenerProbe();

        server = createServer(bindAddress, port, new ServerEventListener() {
            @Override
            public ConnectionEventListener connectionIndication(Connection connection) {
                serverConnection = connection;
                serverConnectionAccepted.countDown();
                return serverProbe;
            }

            @Override
            public void serverStoppedListeningIndication(IOException e) {
                // No-op
            }

            @Override
            public void connectionAttemptFailed(IOException e) {
                throw new AssertionError("Unexpected connection attempt failure", e);
            }
        });

        clientConnection = new ClientConnectionBuilder(bindAddress)
                .setPort(port)
                .setConnectionEventListener(clientProbe)
                .build();

        assertTrue(
                serverConnectionAccepted.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Server did not accept client connection in time");
        assertNotNull(serverConnection, "Server connection must be initialized");

        ListenerProbe verifiedProbe = verifiedListener == VerifiedListener.CLIENT ? clientProbe : serverProbe;
        Connection receivingConnection =
                verifiedListener == VerifiedListener.CLIENT ? clientConnection : serverConnection;
        Connection sendingConnection =
                verifiedListener == VerifiedListener.CLIENT ? serverConnection : clientConnection;

        clientConnection.startDataTransfer();
        waitUntilStoppedState(clientConnection, false);
        waitUntilStoppedState(serverConnection, false);

        if (restartInitiator == RestartInitiator.CLIENT) {
            clientConnection.stopDataTransfer();
            waitUntilStoppedState(clientConnection, true);
            waitUntilStoppedState(serverConnection, true);

            clientConnection.startDataTransfer();
            waitUntilStoppedState(clientConnection, false);
            waitUntilStoppedState(serverConnection, false);
        } else {
            serverConnection.stopDataTransfer();
            waitUntilStoppedState(serverConnection, true);
            waitUntilStoppedState(clientConnection, true);

            serverConnection.startDataTransfer();
            waitUntilStoppedState(serverConnection, false);
            waitUntilStoppedState(clientConnection, false);
        }

        sendingConnection.send(createSinglePointAsdu(verifiedListener == VerifiedListener.CLIENT ? 100 : 101));

        assertTrue(
                verifiedProbe.awaitAsdu(),
                "Verified listener did not receive ASDU after STOPDT -> STARTDT. " + "restartInitiator="
                        + restartInitiator + ", verifiedListener=" + verifiedListener);

        assertEquals(
                2,
                verifiedProbe.startedEvents.get(),
                "Verified listener should have received exactly two start callbacks. " + "restartInitiator="
                        + restartInitiator + ", verifiedListener=" + verifiedListener);

        assertEquals(
                1,
                verifiedProbe.stoppedEvents.get(),
                "Verified listener should have received exactly one stop callback. " + "restartInitiator="
                        + restartInitiator + ", verifiedListener=" + verifiedListener);

        assertFalse(
                receivingConnection.isClosed(),
                "Receiving connection must stay open. " + "restartInitiator=" + restartInitiator + ", verifiedListener="
                        + verifiedListener);

        assertNull(
                verifiedProbe.closedCause.get(),
                "Verified listener must not receive connectionClosed callback during scenario. " + "restartInitiator="
                        + restartInitiator + ", verifiedListener=" + verifiedListener);
    }

    private static Server createServer(InetAddress bindAddress, int port, ServerEventListener serverListener)
            throws IOException {
        Server createdServer =
                Server.builder().setBindAddr(bindAddress).setPort(port).build();
        createdServer.start(serverListener);
        return createdServer;
    }

    private static ASdu createSinglePointAsdu(int informationObjectAddress) {
        return new ASdu(
                ASduType.M_SP_NA_1,
                false,
                CauseOfTransmission.SPONTANEOUS,
                false,
                false,
                0,
                1,
                new InformationObject(
                        informationObjectAddress, new IeSinglePointWithQuality(true, false, false, false, false)));
    }

    private static void waitUntilStoppedState(Connection connection, boolean expectedStopped)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(DEFAULT_TIMEOUT_SECONDS);

        while (System.nanoTime() < deadlineNanos) {
            if (connection.isStopped() == expectedStopped) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }

        throw new AssertionError("Timed out waiting for connection stopped state to become " + expectedStopped);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private enum RestartInitiator {
        CLIENT,
        SERVER
    }

    private enum VerifiedListener {
        CLIENT,
        SERVER
    }

    private static final class ListenerProbe implements ConnectionEventListener {

        private final CountDownLatch asduReceived = new CountDownLatch(1);
        private final AtomicInteger startedEvents = new AtomicInteger(0);
        private final AtomicInteger stoppedEvents = new AtomicInteger(0);
        private final AtomicReference<IOException> closedCause = new AtomicReference<>();

        @Override
        public void newASdu(Connection connection, ASdu aSdu) {
            asduReceived.countDown();
        }

        @Override
        public void connectionClosed(Connection connection, IOException cause) {
            closedCause.set(cause);
        }

        @Override
        public void dataTransferStateChanged(Connection connection, boolean stopped) {
            if (stopped) {
                stoppedEvents.incrementAndGet();
            } else {
                startedEvents.incrementAndGet();
            }
        }

        private boolean awaitAsdu() throws InterruptedException {
            return asduReceived.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }
}
