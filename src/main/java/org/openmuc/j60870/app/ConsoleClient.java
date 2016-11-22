/*
 * Copyright 2014-16 Fraunhofer ISE
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.openmuc.j60870.ASdu;
import org.openmuc.j60870.CauseOfTransmission;
import org.openmuc.j60870.ClientConnectionBuilder;
import org.openmuc.j60870.Connection;
import org.openmuc.j60870.ConnectionEventListener;
import org.openmuc.j60870.IeQualifierOfInterrogation;
import org.openmuc.j60870.IeTime56;
import org.openmuc.j60870.internal.cli.CliParameter;
import org.openmuc.j60870.internal.cli.CliParameterBuilder;
import org.openmuc.j60870.internal.cli.CliParseException;
import org.openmuc.j60870.internal.cli.CliParser;
import org.openmuc.j60870.internal.cli.IntCliParameter;
import org.openmuc.j60870.internal.cli.StringCliParameter;

public final class ConsoleClient {

    private final StringCliParameter host = new CliParameterBuilder("-h")
            .setDescription("The IP/domain address of the server you want to access.")
            .setMandatory()
            .buildStringParameter("host");
    private final IntCliParameter port = new CliParameterBuilder("-p").setDescription("The port to connect to.")
            .buildIntegerParameter("port", 2404);
    private final IntCliParameter commonAddress = new CliParameterBuilder("-ca")
            .setDescription("The address of the target station or the broad cast address.")
            .buildIntegerParameter("common_address", 1);

    private class ClientEventListener implements ConnectionEventListener {

        @Override
        public void newASdu(ASdu aSdu) {
            System.out.println("\nReceived ASDU:\n" + aSdu);

        }

        @Override
        public void connectionClosed(IOException e) {
            System.out.print("Received connection closed signal. Reason: ");
            if (!e.getMessage().isEmpty()) {
                System.out.println(e.getMessage());
            }
            else {
                System.out.println("unknown");
            }

            try {
                is.close();
            } catch (IOException e1) {
            }
        }

    }

    private volatile Connection clientConnection;

    private BufferedReader is;

    public static void main(String[] args) {
        new ConsoleClient(args).start();
    }

    public ConsoleClient(String[] args) {
        List<CliParameter> cliParameters = new ArrayList<CliParameter>();
        cliParameters.add(host);
        cliParameters.add(port);
        cliParameters.add(commonAddress);

        CliParser cliParser = new CliParser("j60870-console-client",
                "A client/master application to access IEC 60870-5-104 servers/slaves.");
        cliParser.addParameters(cliParameters);

        try {
            cliParser.parseArguments(args);
        } catch (CliParseException e1) {
            System.err.println("Error parsing command line parameters: " + e1.getMessage());
            System.out.println(cliParser.getUsageString());
            System.exit(1);
        }

    }

    public void start() {

        InetAddress address;
        try {
            address = InetAddress.getByName(host.getValue());
        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + host.getValue());
            return;
        }

        ClientConnectionBuilder clientConnectionBuilder = new ClientConnectionBuilder(address).setPort(port.getValue());

        try {
            clientConnection = clientConnectionBuilder.connect();
        } catch (IOException e) {
            System.out.println("Unable to connect to remote host: " + host.getValue() + ".");
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                clientConnection.close();
            }
        });

        is = new BufferedReader(new InputStreamReader(System.in));

        try {
            try {
                clientConnection.startDataTransfer(new ClientEventListener(), 5000);
            } catch (TimeoutException e2) {
                throw new IOException("starting data transfer timed out.");
            }
            System.out.println("successfully connected");

            String line;
            while (true) {
                line = is.readLine();

                if (line == null) {
                    throw new IOException("InputStream unexpectedly reached end of stream.");
                }
                else if (line.equals("?")) {
                    printHelp();
                }
                else if (line.equals("q")) {
                    return;
                }
                else if (line.equals("1")) {
                    clientConnection.interrogation(commonAddress.getValue(), CauseOfTransmission.ACTIVATION,
                            new IeQualifierOfInterrogation(20));
                }
                else if (line.equals("2")) {
                    clientConnection.synchronizeClocks(commonAddress.getValue(),
                            new IeTime56(System.currentTimeMillis()));
                }
                else {
                    System.out.println("Unknown command, enter \'?\' for help");
                }
            }

        } catch (IOException e) {
            System.out.println("Connection closed for the following reason: " + e.getMessage());
            return;
        } finally {
            clientConnection.close();
        }

    }

    private static void printHelp() {
        System.out.println("(1) Interrogation C_IC_NA_1\n(2) Synchronize clocks C_CS_NA_1\n(q) Quit");
    }

}
