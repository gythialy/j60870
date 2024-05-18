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
import org.openmuc.j60870.ie.IeNormalizedValue;
import org.openmuc.j60870.ie.IeQuality;
import org.openmuc.j60870.ie.InformationElement;
import org.openmuc.j60870.ie.InformationObject;
import org.openmuc.j60870.internal.ByteStreamHelper;
import org.openmuc.j60870.internal.HexUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static org.openmuc.j60870.TestUtils.*;

public class CloseThenAckITest {

    private static final int PORT = TestUtils.getAvailablePort();
    private final ASdu spontaneousAsdu = new ASdu(ASduType.M_ME_NA_1, true, CauseOfTransmission.SPONTANEOUS, false,
            false, 0, 1,
            new InformationObject[]{new InformationObject(1,
                    new InformationElement[][]{
                            {new IeNormalizedValue(-32768), new IeQuality(true, true, true, true, true)},
                            {new IeNormalizedValue(0), new IeQuality(true, true, true, true, true)}})});
    volatile Connection serverConnection = null;

    private static boolean sleep(int ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    @Test
    public void testCloseThenAckITest() throws Exception {
        Server server = Server.builder()
                .setMaxTimeNoAckReceived(4_000)
                .setPort(PORT)
                .setMaxNumOfOutstandingIPdus(32_767)
                .build();
        try {
            server.start(new ServerListener());

            Socket socket = new Socket("localhost", PORT);
            socket.setSoTimeout(200_000);

            try {
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                os.write(STARTDT_ACT_BYTES);

                receive(is, STARTDT_CON_BYTES);

                sleep(1_000);

                serverConnection.send(spontaneousAsdu);

                byte[] iFrame = new byte[21];
                ByteStreamHelper.readFully(is, iFrame);

                os.write(STOPDT_ACT_BYTES);

                sleep(2_000);

                byte[] sAckFrame = HexUtils.hexToBytes("680401000200");
                os.write(sAckFrame);

                receive(is, STOPDT_CON_BYTES);

                os.write(STARTDT_ACT_BYTES);

                receive(is, STARTDT_CON_BYTES);

                serverConnection.send(spontaneousAsdu);

                ByteStreamHelper.readFully(is, iFrame);

                os.write(sAckFrame);

                sleep(1_000);

                Assert.assertEquals(0, is.available());

                serverConnection.send(spontaneousAsdu);

                ByteStreamHelper.readFully(is, iFrame);

                os.write(STOPDT_ACT_BYTES);

                Assert.assertEquals(-1, is.read());

            } finally {
                socket.close();
            }
        } finally {
            server.stop();
        }
    }

    private void receive(InputStream is, byte[] bytesToReceive) throws IOException {
        byte[] receiveBuffer = new byte[bytesToReceive.length];
        ByteStreamHelper.readFully(is, receiveBuffer);
        Assert.assertArrayEquals(receiveBuffer, bytesToReceive);
    }

    class ServerListener implements ServerEventListener {

        @Override
        public ConnectionEventListener connectionIndication(Connection connection) {
            System.out.println("Server: connection indication");
            serverConnection = connection;
            return new ServerConnectionListener();
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

    class ServerConnectionListener implements ConnectionEventListener {

        @Override
        public void newASdu(Connection connection, ASdu aSdu) {
            System.out.println("Server - New Asdu: " + aSdu);
        }

        @Override
        public void connectionClosed(Connection connection, IOException cause) {
            System.out.println("Server - Connection closed");
        }

        @Override
        public void dataTransferStateChanged(Connection connection, boolean stopped) {
            System.out.println("Server - Data transfer state changed: " + (stopped ? "stopped" : "started"));
        }
    }

    class ClientConnectionListener implements ConnectionEventListener {

        @Override
        public void newASdu(Connection connection, ASdu aSdu) {
            System.out.println("Client - New Asdu: " + aSdu);
        }

        @Override
        public void connectionClosed(Connection connection, IOException cause) {
            System.out.println("Client - Connection closed");
        }

        @Override
        public void dataTransferStateChanged(Connection connection, boolean stopped) {
            System.out.println("Client - Data transfer state changed: " + (stopped ? "stopped" : "started"));
        }
    }
}
