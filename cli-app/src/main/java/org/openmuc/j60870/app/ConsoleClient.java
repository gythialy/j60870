/*
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
 *
 */
package org.openmuc.j60870.app;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.openmuc.j60870.ASdu;
import org.openmuc.j60870.CauseOfTransmission;
import org.openmuc.j60870.ClientConnectionBuilder;
import org.openmuc.j60870.Connection;
import org.openmuc.j60870.ConnectionEventListener;
import org.openmuc.j60870.ie.IeQualifierOfInterrogation;
import org.openmuc.j60870.ie.IeSingleCommand;
import org.openmuc.j60870.ie.IeTime56;
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
    private static final String SINGLE_COMMAND_SELECT = "s";
    private static final String SINGLE_COMMAND_EXECUTE = "e";

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
    private static final IntCliParameter startDtTimeout = new CliParameterBuilder("-t")
            .setDescription("Start DT timeout. For deactivating with set 0.")
            .buildIntParameter("start_DT_timeout", 5000);
    private static final IntCliParameter startDtRetries = new CliParameterBuilder("-r")
            .setDescription("Send start DT retries.")
            .buildIntParameter("start_DT_retries", 1);
    private static final IntCliParameter messageFragmentTimeout = new CliParameterBuilder("-mft")
            .setDescription("Message fragment timeout.")
            .buildIntParameter("message_fragment_timeout", 5000);

    private static Connection connection;
    private static final ActionProcessor actionProcessor = new ActionProcessor(new ActionExecutor());

    private static class ClientEventListener implements ConnectionEventListener {

        @Override
        public void newASdu(ASdu aSdu) {
            println("\nReceived ASDU:\n", aSdu.toString());
        }

        @Override
        public void connectionClosed(IOException e) {
            println("Received connection closed signal. Reason: ");
            if (!e.getMessage().isEmpty()) {
                println(e.getMessage());
            }
            else {
                println("unknown");
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
                    println("** Sending general interrogation command.");
                    connection.interrogation(commonAddrParam.getValue(), CauseOfTransmission.ACTIVATION,
                            new IeQualifierOfInterrogation(20));
                    break;
                case CLOCK_SYNC_ACTION_KEY:
                    println("** Sending synchronize clocks command.");
                    connection.synchronizeClocks(commonAddrParam.getValue(), new IeTime56(System.currentTimeMillis()));
                    break;
                case SINGLE_COMMAND_SELECT:
                    println("** Sending single command select.");
                    connection.singleCommand(commonAddrParam.getValue(), CauseOfTransmission.ACTIVATION, 5000,
                            new IeSingleCommand(true, 0, true));
                    break;
                case SINGLE_COMMAND_EXECUTE:
                    println("** Sending single command execute.");
                    connection.singleCommand(commonAddrParam.getValue(), CauseOfTransmission.ACTIVATION, 5000,
                            new IeSingleCommand(false, 0, false));
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
            println("** Closing connection.");
            connection.close();
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
            println(cliParser.getUsageString());
            System.exit(1);
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(hostParam.getValue());
        } catch (UnknownHostException e) {
            println("Unknown host: ", hostParam.getValue());
            return;
        }

        ClientConnectionBuilder clientConnectionBuilder = new ClientConnectionBuilder(address)
                .setMessageFragmentTimeout(messageFragmentTimeout.getValue())
                .setPort(portParam.getValue());

        try {
            connection = clientConnectionBuilder.build();
        } catch (IOException e) {
            println("Unable to connect to remote host: ", hostParam.getValue(), ".");
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                connection.close();
            }
        });

        boolean connected = false;
        int retries = startDtRetries.getValue();
        int i = 1;

        while (!connected && i <= retries) {
            try {
                println("Send start DT. Try no. " + i);
                connection.startDataTransfer(new ClientEventListener(), startDtTimeout.getValue());
            } catch (InterruptedIOException e2) {
                if (i == retries) {
                    println("Starting data transfer timed out. Closing connection. Because of no more retries.");
                    connection.close();
                    return;
                }
                else {
                    println("Got Timeout.class Next try.");
                    ++i;
                    continue;
                }
            } catch (IOException e) {
                println("Connection closed for the following reason: ", e.getMessage());
                return;
            }
            connected = true;
        }
        println("successfully connected");

        actionProcessor.addAction(new Action(INTERROGATION_ACTION_KEY, "interrogation C_IC_NA_1"));
        actionProcessor.addAction(new Action(CLOCK_SYNC_ACTION_KEY, "synchronize clocks C_CS_NA_1"));
        actionProcessor.addAction(new Action(SINGLE_COMMAND_SELECT, "single command select C_SC_NA_1"));
        actionProcessor.addAction(new Action(SINGLE_COMMAND_EXECUTE, "single command execute C_SC_NA_1"));

        actionProcessor.start();
    }

    private static void println(String... strings) {
        StringBuilder sb = new StringBuilder();
        for (String string : strings) {
            sb.append(string);
        }
        System.out.println(sb.toString());
    }

}
