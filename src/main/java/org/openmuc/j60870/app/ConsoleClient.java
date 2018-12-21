/*
 * Copyright 2014-17 Fraunhofer ISE
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

import java.io.IOException;
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
import org.openmuc.j60870.internal.cli.Action;
import org.openmuc.j60870.internal.cli.ActionException;
import org.openmuc.j60870.internal.cli.ActionListener;
import org.openmuc.j60870.internal.cli.ActionProcessor;
import org.openmuc.j60870.internal.cli.CliParameter;
import org.openmuc.j60870.internal.cli.CliParameterBuilder;
import org.openmuc.j60870.internal.cli.CliParseException;
import org.openmuc.j60870.internal.cli.CliParser;
import org.openmuc.j60870.internal.cli.IntCliParameter;
import org.openmuc.j60870.internal.cli.StringCliParameter;

public final class ConsoleClient {

    private static final String INTERROGATION_ACTION_KEY = "i";
    private static final String CLOCK_SYNC_ACTION_KEY = "c";

    private static final StringCliParameter hostParam = new CliParameterBuilder("-h")
            .setDescription("The IP/domain address of the server you want to access.")
            .setMandatory()
            .buildStringParameter("host");
    private static final IntCliParameter portParam = new CliParameterBuilder("-p")
            .setDescription("The port to connect to.")
            .buildIntParameter("port", 2404);
    private static final IntCliParameter commonAddrParam = new CliParameterBuilder("-ca")
            .setDescription("The address of the target station or the broad cast address.")
            .buildIntParameter("common_address", 1);

    private static volatile Connection connection;
    private static final ActionProcessor actionProcessor = new ActionProcessor(new ActionExecutor());

    private static class ClientEventListener implements ConnectionEventListener {

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
            actionProcessor.close();
        }

    }

    private static class ActionExecutor implements ActionListener {

        @Override
        public void actionCalled(String actionKey) throws ActionException {
            try {
                switch (actionKey) {
                case INTERROGATION_ACTION_KEY:
                    System.out.println("** Sending general interrogation command.");
                    connection.interrogation(commonAddrParam.getValue(), CauseOfTransmission.ACTIVATION,
                            new IeQualifierOfInterrogation(20));
                    Thread.sleep(2000);
                    break;
                case CLOCK_SYNC_ACTION_KEY:
                    System.out.println("** Sending synchronize clocks command.");
                    connection.synchronizeClocks(commonAddrParam.getValue(), new IeTime56(System.currentTimeMillis()));
                    break;
                default:
                    break;
                }
            } catch (Exception e) {
                throw new ActionException(e);
            }
        }

        @Override
        public void quit() {
            System.out.println("** Closing connection.");
            connection.close();
            return;
        }
    }

    public static void main(String[] args) {

        List<CliParameter> cliParameters = new ArrayList<>();
        cliParameters.add(hostParam);
        cliParameters.add(portParam);
        cliParameters.add(commonAddrParam);

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

        InetAddress address;
        try {
            address = InetAddress.getByName(hostParam.getValue());
        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + hostParam.getValue());
            return;
        }

        ClientConnectionBuilder clientConnectionBuilder = new ClientConnectionBuilder(address)
                .setPort(portParam.getValue());

        try {
            connection = clientConnectionBuilder.connect();
        } catch (IOException e) {
            System.out.println("Unable to connect to remote host: " + hostParam.getValue() + ".");
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                connection.close();
            }
        });

        try {
            connection.startDataTransfer(new ClientEventListener(), 5000);
        } catch (TimeoutException e2) {
            System.out.println("Starting data transfer timed out. Closing connection.");
            connection.close();
            return;
        } catch (IOException e) {
            System.out.println("Connection closed for the following reason: " + e.getMessage());
            return;
        }
        System.out.println("successfully connected");

        actionProcessor.addAction(new Action(INTERROGATION_ACTION_KEY, "interrogation C_IC_NA_1"));
        actionProcessor.addAction(new Action(CLOCK_SYNC_ACTION_KEY, "synchronize clocks C_CS_NA_1"));

        actionProcessor.start();

    }

}
