/**
 * Copyright 2014-19 Fraunhofer ISE
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
 */
/*
 * This file is part of j60870.
 * For more information visit http://www.openmuc.org
 *
 * You are free to use code of this sample file in any
 * way you like and without any restrictions.
 *
 */
package org.openmuc.j60870.app;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;

import org.openmuc.j60870.ASdu;
import org.openmuc.j60870.ASduType;
import org.openmuc.j60870.CauseOfTransmission;
import org.openmuc.j60870.Connection;
import org.openmuc.j60870.ConnectionEventListener;
import org.openmuc.j60870.Server;
import org.openmuc.j60870.ServerEventListener;
import org.openmuc.j60870.ie.IeQuality;
import org.openmuc.j60870.ie.IeScaledValue;
import org.openmuc.j60870.ie.IeSingleCommand;
import org.openmuc.j60870.ie.InformationElement;
import org.openmuc.j60870.ie.InformationObject;

public class SampleServer {

    public class ServerListener implements ServerEventListener {

        public class ConnectionListener implements ConnectionEventListener {

            private final Connection connection;
            private final int connectionId;
            private boolean selected = false;

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
                        println("Got interrogation command. Will send scaled measured values.\n");

                        connection.send(new ASdu(ASduType.M_ME_NB_1, true, CauseOfTransmission.SPONTANEOUS, false,
                                false, 0, aSdu.getCommonAddress(),
                                new InformationObject(1, new InformationElement[][] {
                                        { new IeScaledValue(-32768), new IeQuality(true, true, true, true, true) },
                                        { new IeScaledValue(10), new IeQuality(true, true, true, true, true) },
                                        { new IeScaledValue(-5), new IeQuality(true, true, true, true, true) } })));

                        break;
                    case C_SC_NA_1:
                        InformationObject informationObject = aSdu.getInformationObjects()[0];
                        IeSingleCommand singleCommand = (IeSingleCommand) informationObject
                                .getInformationElements()[0][0];

                        if (informationObject.getInformationObjectAddress() != 5000) {
                            break;
                        }
                        if (singleCommand.isSelect()) {
                            println("Got single command with select true. Select command.");
                            selected = true;
                        }
                        else if (!singleCommand.isSelect() && selected) {
                            println("Got single command with select false. Execute selected command.");
                            selected = false;
                        }
                        else {
                            println("Got single command with select false. But no command is selected, no execution.");
                        }
                        connection.sendConfirmation(aSdu);
                        break;
                    default:
                        println("Got unknown request: ", aSdu.toString(), ". Will not confirm it.\n");
                    }

                } catch (EOFException e) {
                    println("Will quit listening for commands on connection (" + connectionId,
                            ") because socket was closed.");
                } catch (IOException e) {
                    println("Will quit listening for commands on connection (" + connectionId, ") because of error: \"",
                            e.getMessage(), "\".");
                }

            }

            @Override
            public void connectionClosed(IOException e) {
                println("Connection (" + connectionId, ") was closed. ", e.getMessage());
            }

        }

        @Override
        public void connectionIndication(Connection connection) {

            int myConnectionId = connectionIdCounter++;
            println("A client has connected using TCP/IP. Will listen for a StartDT request. Connection ID: "
                    + myConnectionId);

            try {
                connection.waitForStartDT(new ConnectionListener(connection, myConnectionId), 5000);
            } catch (InterruptedIOException e) {
                // ignore: nothing to do
            } catch (IOException e) {
                println("Connection (" + myConnectionId, ") interrupted while waiting for StartDT: ", e.getMessage(),
                        ". Will quit.");
                return;
            }
            println("Started data transfer on connection (" + myConnectionId, ") Will listen for incoming commands.");

        }

        @Override
        public void serverStoppedListeningIndication(IOException e) {
            println("Server has stopped listening for new connections : \"", e.getMessage(), "\". Will quit.");
        }

        @Override
        public void connectionAttemptFailed(IOException e) {
            println("Connection attempt failed: ", e.getMessage());

        }

    }

    private int connectionIdCounter = 1;

    public static void main(String[] args) {
        new SampleServer().start();
    }

    public void start() {
        Server server = Server.builder().build();

        try {
            server.start(new ServerListener());
        } catch (IOException e) {
            println("Unable to start listening: \"", e.getMessage(), "\". Will quit.");
        }
    }

    private void println(String... strings) {
        StringBuilder sb = new StringBuilder();
        for (String string : strings) {
            sb.append(string);
        }
        System.out.println(sb.toString());
    }

}
