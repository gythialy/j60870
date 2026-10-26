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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmuc.j60870.ie.InformationObject;
import org.openmuc.j60870.internal.ExtendedDataInputStream;

/**
 * A read command (C_RD_NA_1) has an information object address but no information elements. It must be received,
 * and its mirrored confirmation sent and received, without the connection being closed.
 */
public class ReadCommandITest {

    private static final int PORT = TestUtils.getAvailablePort();
    private static final int COMMON_ADDRESS = 1;
    private static final int IOA = 4711;

    @Test
    public void testReadCommandDecodes() throws IOException {
        ConnectionSettings settings = new ConnectionSettings();
        ASdu sent = new ASdu(
                ASduType.C_RD_NA_1,
                false,
                CauseOfTransmission.REQUEST,
                false,
                false,
                0,
                COMMON_ADDRESS,
                new InformationObject(IOA));

        byte[] buffer = new byte[255];
        int length = sent.encode(buffer, 0, settings);
        ASdu received =
                ASdu.decode(new ExtendedDataInputStream(new ByteArrayInputStream(buffer, 0, length)), settings, length);

        Assertions.assertEquals(ASduType.C_RD_NA_1, received.getTypeIdentification());
        Assertions.assertEquals(1, received.getInformationObjects().length);
        Assertions.assertEquals(IOA, received.getInformationObjects()[0].getInformationObjectAddress());

        byte[] reencoded = new byte[255];
        Assertions.assertEquals(length, received.encode(reencoded, 0, settings));
        Assertions.assertArrayEquals(Arrays.copyOf(buffer, length), Arrays.copyOf(reencoded, length));
    }

    @Test
    public void testReadCommandAndRefusal() throws Exception {
        BlockingQueue<ASdu> atServer = new LinkedBlockingQueue<>();
        BlockingQueue<ASdu> atClient = new LinkedBlockingQueue<>();
        BlockingQueue<IOException> closed = new LinkedBlockingQueue<>();

        Server server = Server.builder().setPort(PORT).build();
        server.start(new ServerEventListener() {
            @Override
            public ConnectionEventListener connectionIndication(Connection connection) {
                return new ConnectionEventListener() {
                    @Override
                    public void newASdu(Connection connection, ASdu aSdu) {
                        atServer.add(aSdu);
                        try {
                            connection.sendConfirmation(
                                    aSdu,
                                    aSdu.getCommonAddress(),
                                    true,
                                    CauseOfTransmission.UNKNOWN_INFORMATION_OBJECT_ADDRESS);
                        } catch (IOException e) {
                            closed.add(e);
                        }
                    }

                    @Override
                    public void connectionClosed(Connection connection, IOException cause) {
                        closed.add(cause == null ? new IOException("server connection closed") : cause);
                    }

                    @Override
                    public void dataTransferStateChanged(Connection connection, boolean stopped) {}
                };
            }

            @Override
            public void serverStoppedListeningIndication(IOException e) {}

            @Override
            public void connectionAttemptFailed(IOException e) {}
        });

        try (Connection client = new ClientConnectionBuilder("127.0.0.1")
                .setPort(PORT)
                .setConnectionEventListener(new ConnectionEventListener() {
                    @Override
                    public void newASdu(Connection connection, ASdu aSdu) {
                        atClient.add(aSdu);
                    }

                    @Override
                    public void connectionClosed(Connection connection, IOException cause) {
                        closed.add(cause == null ? new IOException("client connection closed") : cause);
                    }

                    @Override
                    public void dataTransferStateChanged(Connection connection, boolean stopped) {}
                })
                .build()) {
            client.startDataTransfer();
            client.readCommand(COMMON_ADDRESS, IOA);

            ASdu request = atServer.poll(5, TimeUnit.SECONDS);
            Assertions.assertNotNull(request, "the server did not receive the read command: " + closed.peek());
            Assertions.assertEquals(ASduType.C_RD_NA_1, request.getTypeIdentification());
            Assertions.assertEquals(CauseOfTransmission.REQUEST, request.getCauseOfTransmission());
            Assertions.assertEquals(IOA, request.getInformationObjects()[0].getInformationObjectAddress());

            ASdu refusal = atClient.poll(5, TimeUnit.SECONDS);
            Assertions.assertNotNull(refusal, "the client did not receive the refusal: " + closed.peek());
            Assertions.assertEquals(ASduType.C_RD_NA_1, refusal.getTypeIdentification());
            Assertions.assertTrue(refusal.isNegativeConfirm());
            Assertions.assertEquals(
                    CauseOfTransmission.UNKNOWN_INFORMATION_OBJECT_ADDRESS, refusal.getCauseOfTransmission());
            Assertions.assertEquals(IOA, refusal.getInformationObjects()[0].getInformationObjectAddress());

            Assertions.assertNull(closed.poll(), "a connection was closed");
            Assertions.assertFalse(client.isClosed());
        } finally {
            server.stop();
        }
    }
}
