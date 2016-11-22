package org.openmuc.j60870;

/*
 * This file is part of j60870.
 * For more information visit http://www.openmuc.org
 *
 * You are free to use code of this sample file in any
 * way you like and without any restrictions.
 *
 */
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class SampleServer {

    public class ServerListener implements ServerEventListener {

        public class ConnectionListener implements ConnectionEventListener {

            private final Connection connection;
            private final int connectionId;

            public ConnectionListener(Connection connection, int connectionId) {
                this.connection = connection;
                this.connectionId = connectionId;
            }

            @Override
            public void newASdu(ASdu aSdu) {
                try {

                    switch (aSdu.getTypeIdentification()) {
                    // interrogation command
                    case C_IC_NA_1:
                        connection.sendConfirmation(aSdu);
                        System.out.println("Got interrogation command. Will send scaled measured values.\n");

                        connection.send(new ASdu(TypeId.M_ME_NB_1, true, CauseOfTransmission.SPONTANEOUS, false, false,
                                0, aSdu.getCommonAddress(),
                                new InformationObject[] { new InformationObject(1, new InformationElement[][] {
                                        { new IeScaledValue(-32768), new IeQuality(true, true, true, true, true) },
                                        { new IeScaledValue(10), new IeQuality(true, true, true, true, true) },
                                        { new IeScaledValue(-5), new IeQuality(true, true, true, true, true) } }) }));

                        break;
                    default:
                        System.out.println("Got unknown request: " + aSdu + ". Will not confirm it.\n");
                    }

                } catch (EOFException e) {
                    System.out.println("Will quit listening for commands on connection (" + connectionId
                            + ") because socket was closed.");
                } catch (IOException e) {
                    System.out.println("Will quit listening for commands on connection (" + connectionId
                            + ") because of error: \"" + e.getMessage() + "\".");
                }

            }

            @Override
            public void connectionClosed(IOException e) {
                System.out.println("Connection (" + connectionId + ") was closed. " + e.getMessage());
            }

        }

        @Override
        public void connectionIndication(Connection connection) {

            int myConnectionId = connectionIdCounter++;
            System.out.println("A client has connected using TCP/IP. Will listen for a StartDT request. Connection ID: "
                    + myConnectionId);

            try {
                connection.waitForStartDT(new ConnectionListener(connection, myConnectionId), 5000);
            } catch (IOException e) {
                System.out.println("Connection (" + myConnectionId + ") interrupted while waiting for StartDT: "
                        + e.getMessage() + ". Will quit.");
                return;
            } catch (TimeoutException e) {
            }

            System.out.println(
                    "Started data transfer on connection (" + myConnectionId + ") Will listen for incoming commands.");

        }

        @Override
        public void serverStoppedListeningIndication(IOException e) {
            System.out.println(
                    "Server has stopped listening for new connections : \"" + e.getMessage() + "\". Will quit.");
        }

        @Override
        public void connectionAttemptFailed(IOException e) {
            System.out.println("Connection attempt failed: " + e.getMessage());

        }

    }

    private int connectionIdCounter = 1;

    public static void main(String[] args) {
        new SampleServer().start();
    }

    public void start() {
        Server server = new Server.Builder().build();

        try {
            server.start(new ServerListener());
        } catch (IOException e) {
            System.out.println("Unable to start listening: \"" + e.getMessage() + "\". Will quit.");
            return;
        }
    }

}
