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
package org.openmuc.j60870.ie;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmuc.j60870.*;
import org.openmuc.j60870.internal.ExtendedDataInputStream;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class TransmissionControlUsingStartStopTest {

    Server serverSap;
    Connection clientConnection;
    Connection serverConnection;
    ClientConnectionListenerImpl clientConnectionListener;
    ServerConnectionListenerImpl serverConnectionListener;
    IOException clientStoppedCause;
    IOException serverStoppedCause;
    boolean newASduCalled;
    CountDownLatch connectionWaitLatch;

    @Before
    public void initConnection() throws IOException, InterruptedException {
        int port = TestUtils.getAvailablePort();
        newASduCalled = false;
        clientConnectionListener = new ClientConnectionListenerImpl();
        serverConnectionListener = new ServerConnectionListenerImpl();
        ServerListenerImpl serverListener = new ServerListenerImpl();
        connectionWaitLatch = new CountDownLatch(1);
        serverSap = Server.builder().setPort(port).build();
        serverSap.start(serverListener);
        clientConnection = new ClientConnectionBuilder("127.0.0.1").setPort(port)
                .setReservedASduTypeDecoder(new ReservedASduTypeDecoderImpl())
                .setConnectionEventListener(clientConnectionListener)
                .build();
        connectionWaitLatch.await();
    }

    @After
    public void exitConnection() {
        clientConnection.close();
        serverSap.stop();
    }

    /***
     * 5.3.2.70 Description block 2. Expect Active Close on receipt of I- or S-frames.
     */
    @Test
    public void receiveIorSFramesInStoppedConnectionState()
            throws InterruptedException, IOException, NoSuchFieldException, IllegalAccessException {
        Field field = Connection.class.getDeclaredField("stopped");
        field.setAccessible(true);
        field.set(clientConnection, false);
        clientConnection.interrogation(1, CauseOfTransmission.ACTIVATION, new IeQualifierOfInterrogation(20));
        Thread.sleep(1000);
        assertTrue(serverConnection.isClosed());
        assertTrue(clientConnection.isClosed());
        assertFalse(newASduCalled);
        assertEquals(serverStoppedCause.getClass(), IOException.class);
        assertTrue(serverStoppedCause.getMessage().contains("message while STOPDT state"));
        // controlled station (server) closes because it receives an ASdu while in stopped state, thus controller
        // throws EOFException because remote closed
        Thread.sleep(1000);
        assertEquals(EOFException.class, clientStoppedCause.getClass());
        assertTrue(clientStoppedCause.getMessage().contains("Connection was closed by remote."));
    }

    @Test
    public void receiveIorSFramesInStoppedConnectionStateAfterStartAndStop()
            throws InterruptedException, IOException, NoSuchFieldException, IllegalAccessException {
        clientConnection.startDataTransfer();
        clientConnection.stopDataTransfer();
        Field field = Connection.class.getDeclaredField("stopped");
        field.setAccessible(true);
        field.set(clientConnection, false); // overwrite to send illegal message anyway
        clientConnection.interrogation(1, CauseOfTransmission.ACTIVATION, new IeQualifierOfInterrogation(20));
        Thread.sleep(1000);
        assertTrue(serverConnection.isClosed());
        assertTrue(clientConnection.isClosed());
        assertFalse(newASduCalled);
        assertEquals(serverStoppedCause.getClass(), IOException.class);
        assertTrue(serverStoppedCause.getMessage().contains("message while STOPDT state"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwExceptionOnSendInStoppedConnectionStateBeforeStart() throws IOException {
        clientConnection.interrogation(1, CauseOfTransmission.ACTIVATION, new IeQualifierOfInterrogation(20));
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwExceptionOnSendInStoppedConnectionStateAfterStop() throws IOException {
        clientConnection.startDataTransfer();
        clientConnection.stopDataTransfer();
        clientConnection.interrogation(1, CauseOfTransmission.ACTIVATION, new IeQualifierOfInterrogation(20));
    }

    @Test
    public void sendNoneStandardASdu() throws IOException, InterruptedException {
        clientConnection.startDataTransfer();
        serverConnection.send(new ASdu(ASduType.PRIVATE_136, false, 1, CauseOfTransmission.SPONTANEOUS, false, false, 0,
                10, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}));
        Thread.sleep(1000);
        assertTrue(newASduCalled);
    }

    private static class ReservedASduTypeDecoderImpl implements ReservedASduTypeDecoder {

        @Override
        public List<ASduType> getSupportedTypes() {
            List<ASduType> supported = new ArrayList<>();
            supported.add(ASduType.RESERVED_44);
            return supported;
        }

        @Override
        public InformationObject decode(ExtendedDataInputStream is, ASduType aSduType) {
            return new InformationObject(0, new InformationElement() {
                @Override
                int encode(byte[] buffer, int i) {
                    return 0;
                }

                @Override
                public String toString() {
                    return "someString";
                }
            });
        }
    }

    private class ClientConnectionListenerImpl implements ConnectionEventListener {

        @Override
        public void newASdu(Connection connection, ASdu aSdu) {
            newASduCalled = true;
        }

        @Override
        public void connectionClosed(Connection connection, IOException cause) {
            clientStoppedCause = cause;
        }

        @Override
        public void dataTransferStateChanged(Connection connection, boolean stopped) {

        }
    }

    private class ServerConnectionListenerImpl implements ConnectionEventListener {

        @Override
        public void newASdu(Connection connection, ASdu aSdu) {
            newASduCalled = true;
        }

        @Override
        public void connectionClosed(Connection connection, IOException cause) {
            serverStoppedCause = cause;
        }

        @Override
        public void dataTransferStateChanged(Connection connection, boolean stopped) {

        }
    }

    private class ServerListenerImpl implements ServerEventListener {

        @Override
        public ConnectionEventListener connectionIndication(Connection connection) {
            serverConnection = connection;
            connectionWaitLatch.countDown();
            return serverConnectionListener;
        }

        @Override
        public void serverStoppedListeningIndication(IOException e) {

        }

        @Override
        public void connectionAttemptFailed(IOException e) {

        }
    }
}
