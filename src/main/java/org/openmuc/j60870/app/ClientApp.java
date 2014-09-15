/*
 * Copyright 2014 Fraunhofer ISE
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
package org.openmuc.j60870.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import org.openmuc.j60870.ASdu;
import org.openmuc.j60870.CauseOfTransmission;
import org.openmuc.j60870.ClientSap;
import org.openmuc.j60870.Connection;
import org.openmuc.j60870.ConnectionEventListener;
import org.openmuc.j60870.IeQualifierOfInterrogation;
import org.openmuc.j60870.IeTime56;

public final class ClientApp implements ConnectionEventListener {

    private static Connection clientConnection;

    private final BufferedReader is;

    public ClientApp(BufferedReader is) {
        this.is = is;
    }

    private static void printUsage() {
        System.out.println(
                "SYNOPSIS\n\torg.openmuc.j60870.app.ClientApp <host> [-p <port>] [-ca <common_address>]");
        System.out.println(
                "DESCRIPTION\n\tA client/master application to access IEC 60870-5-104 slaves.");
        System.out.println("OPTIONS");
        System.out.println("\t<host>\n\t    The address of the slave you want to access.\n");
        System.out.println("\t-p <port>\n\t    The port to connect to. The default port is 2404.\n");
        System.out
                .println(
                        "\t-ca <common_address>\n\t    The address of the target station or the broad cast address. The default is 1.\n");
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 5) {
            printUsage();
            return;
        }

        String remoteHost = args[0];

        int port = 2404;
        int commonAddress = 1;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p")) {
                i++;
                if (i == args.length) {
                    printUsage();
                    System.exit(1);
                }
                try {
                    port = Integer.parseInt(args[i]);
                }
                catch (NumberFormatException e) {
                    printUsage();
                    System.exit(1);
                }
            } else if (args[i].equals("-ca")) {
                i++;
                if (i == args.length) {
                    printUsage();
                    System.exit(1);
                }
                try {
                    commonAddress = Integer.parseInt(args[i]);
                }
                catch (NumberFormatException e) {
                    printUsage();
                    System.exit(1);
                }
            } else {
                remoteHost = args[i];
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (clientConnection != null) {
                    clientConnection.close();
                }
            }
        });

        ClientSap clientSap = new ClientSap();

        InetAddress address;
        try {
            address = InetAddress.getByName(remoteHost);
        }
        catch (UnknownHostException e) {
            System.out.println("Unknown host: " + remoteHost);
            return;
        }

        try {
            clientConnection = clientSap.connect(address, port);
        }
        catch (IOException e) {
            System.out.println("Unable to connect to remote host: " + remoteHost + ".");
            return;
        }

        BufferedReader is = new BufferedReader(new InputStreamReader(System.in));

        try {
            try {
                clientConnection.startDataTransfer(new ClientApp(is));
            }
            catch (TimeoutException e2) {
                throw new IOException("starting data transfer timed out.");
            }
            System.out.println("successfully connected");

            String line;
            while (true) {
                try {
                    line = is.readLine();
                }
                catch (IOException e1) {
                    System.out.println("Connection to server was interrupted. Will quit.");
                    return;
                }

                try {
                    if (line.equals("?")) {
                        printHelp();
                    } else if (line.equals("q")) {
                        return;
                    } else if (line.equals("1")) {
                        clientConnection.interrogation(commonAddress,
                                                       CauseOfTransmission.ACTIVATION,
                                                       new IeQualifierOfInterrogation(0));
                    } else if (line.equals("2")) {
                        clientConnection.synchronizeClocks(commonAddress,
                                                           new IeTime56(System.currentTimeMillis()));
                    } else {
                        System.out.println("Unknown command, enter \'?\' for help");
                    }
                }
                catch (TimeoutException e) {
                    System.out.println("Command timed out");
                }
            }

        }
        catch (IOException e) {
            System.out.println("Connection closed for the following reason: " + e.getMessage());
            return;
        }
        finally {
            clientConnection.close();
        }

    }

    private static void printHelp() {
        System.out.println("(1) Interrogation C_IC_NA_1\n(2) Synchronize clocks C_CS_NA_1");
    }

    @Override
    public void newASdu(ASdu aSdu) {
        System.out.println("\nReceived ASDU:\n" + aSdu);

    }

    @Override
    public void connectionClosed(IOException e) {
        System.out.print("Received connection closed signal. Reason: ");
        if (!e.getMessage().isEmpty()) {
            System.out.println(e.getMessage());
        } else {
            System.out.println("unknown");
        }

        try {
            is.close();
        }
        catch (IOException e1) {
        }
    }

}
