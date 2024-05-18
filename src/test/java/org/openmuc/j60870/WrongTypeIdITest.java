/*
 * Copyright 2014-2024 Fraunhofer ISE
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

import org.junit.Assert;
import org.junit.Test;
import org.openmuc.j60870.internal.ByteStreamHelper;
import org.openmuc.j60870.internal.HexUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

public class WrongTypeIdITest {

    private static final int PORT = TestUtils.getAvailablePort();

    @Test
    public void testWrongTypeIdITest() throws Exception {
        Server server = Server.builder()
                .setPort(PORT)
                .setMaxNumOfOutstandingIPdus(32_767)
                .setAllowedASduTypes(new ArrayList<>())
                .build();
        try {
            server.start(new ServerListener());

            Socket socket = new Socket("localhost", PORT);
            socket.setSoTimeout(200_000);

            try {
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                byte[] startdtAct = HexUtils.hexToBytes("680407000000");
                os.write(startdtAct);

                byte[] startdtCon = new byte[6];
                ByteStreamHelper.readFully(is, startdtCon);
                Assert.assertArrayEquals(HexUtils.hexToBytes("68040B000000"), startdtCon);

                byte[] malformedUnsupportedTypeApdu = HexUtils
                        .hexToBytes("6816000000002D01060039000000008D670A99130D190118");
                os.write(malformedUnsupportedTypeApdu);

                byte[] response = new byte[malformedUnsupportedTypeApdu.length];
                ByteStreamHelper.readFully(is, response);

                byte[] expectedResponse = Arrays.copyOf(malformedUnsupportedTypeApdu,
                        malformedUnsupportedTypeApdu.length);
                expectedResponse[4] = 0x02;
                expectedResponse[8] = (byte) (0x40 | CauseOfTransmission.UNKNOWN_TYPE_ID.getId());
                Assert.assertArrayEquals(expectedResponse, response);
            } finally {
                socket.close();
            }
        } finally {
            server.stop();
        }
    }

    class ServerListener implements ServerEventListener {

        @Override
        public ConnectionEventListener connectionIndication(Connection connection) {
            System.out.println("Server: connection indication");
            return new ConnectionListener();
        }

        @Override
        public void serverStoppedListeningIndication(IOException e) {
            System.out.println("Server stopped listening: " + e);
        }

        @Override
        public void connectionAttemptFailed(IOException e) {
            System.out.println("Server: Connection attempt failed: " + e);
        }
    }

    class ConnectionListener implements ConnectionEventListener {

        @Override
        public void newASdu(Connection connection, ASdu aSdu) {
            System.out.println("New Asdu: " + aSdu);
        }

        @Override
        public void connectionClosed(Connection connection, IOException cause) {
            System.out.println("Connection closed");
        }

        @Override
        public void dataTransferStateChanged(Connection connection, boolean stopped) {
            System.out.println("Data transfer state changed: " + (stopped ? "stopped" : "started"));
        }
    }
}
