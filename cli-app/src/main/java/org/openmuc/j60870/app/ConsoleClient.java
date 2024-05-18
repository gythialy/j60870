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
package org.openmuc.j60870.app;

import org.openmuc.j60870.*;
import org.openmuc.j60870.ie.IeQualifierOfCounterInterrogation;
import org.openmuc.j60870.ie.IeQualifierOfInterrogation;
import org.openmuc.j60870.ie.IeSingleCommand;
import org.openmuc.j60870.ie.IeTime56;
import org.openmuc.j60870.internal.cli.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class ConsoleClient {

    private static final String INTERROGATION_ACTION_KEY = "i";
    private static final String COUNTER_INTERROGATION_ACTION_KEY = "ci";
    private static final String CLOCK_SYNC_ACTION_KEY = "c";
    private static final String SINGLE_COMMAND_SELECT = "s";
    private static final String SINGLE_COMMAND_EXECUTE = "e";
    private static final String SEND_STOPDT = "p";
    private static final String SEND_STARTDT = "t";

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
    private static final IntCliParameter startDtRetries = new CliParameterBuilder("-r")
            .setDescription("Send start DT retries.")
            .buildIntParameter("start_DT_retries", 1);
    private static final IntCliParameter connectionTimeout = new CliParameterBuilder("-ct")
            .setDescription("Connection timeout t0.")
            .buildIntParameter("connection_timeout", 20_000);
    private static final IntCliParameter messageFragmentTimeout = new CliParameterBuilder("-mft")
            .setDescription("Message fragment timeout.")
            .buildIntParameter("message_fragment_timeout", 5_000);
    private static final ActionProcessor actionProcessor = new ActionProcessor(new ActionExecutor());
    private static Connection connection;

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
            log(cliParser.getUsageString());
            System.exit(1);
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(hostParam.getValue());
        } catch (UnknownHostException e) {
            log("Unknown host: ", hostParam.getValue());
            return;
        }

        ClientConnectionBuilder clientConnectionBuilder = new ClientConnectionBuilder(address)
                .setMessageFragmentTimeout(messageFragmentTimeout.getValue())
                .setConnectionTimeout(connectionTimeout.getValue())
                .setPort(portParam.getValue())
                .setConnectionEventListener(new ClientEventListener());

        try {
            connection = clientConnectionBuilder.build();
        } catch (IOException e) {
            log("Unable to connect to remote host: ", hostParam.getValue(), ".");
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
                log("Send start DT. Try no. " + i);
                connection.startDataTransfer();
            } catch (InterruptedIOException e2) {
                if (i == retries) {
                    log("Starting data transfer timed out. Closing connection. Because of no more retries.");
                    connection.close();
                    return;
                } else {
                    log("Got Timeout.class Next try.");
                    ++i;
                    continue;
                }
            } catch (IOException e) {
                log("Connection closed for the following reason: ", e.getMessage());
                return;
            }
            connected = true;
        }
        log("successfully connected");

        actionProcessor.addAction(new Action(INTERROGATION_ACTION_KEY, "interrogation C_IC_NA_1"));
        actionProcessor.addAction(new Action(COUNTER_INTERROGATION_ACTION_KEY, "counter interrogation C_CI_NA_1"));
        actionProcessor.addAction(new Action(CLOCK_SYNC_ACTION_KEY, "synchronize clocks C_CS_NA_1"));
        actionProcessor.addAction(new Action(SINGLE_COMMAND_SELECT, "single command select C_SC_NA_1"));
        actionProcessor.addAction(new Action(SINGLE_COMMAND_EXECUTE, "single command execute C_SC_NA_1"));
        actionProcessor.addAction(new Action(SEND_STOPDT, "STOPDT act"));
        actionProcessor.addAction(new Action(SEND_STARTDT, "STARTDT act"));

        actionProcessor.start();
    }

    private static void log(String... strings) {
        String time = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS ").format(new Date());
        println(time, strings);
    }

    private static void println(String string, String... strings) {
        StringBuilder sb = new StringBuilder();
        sb.append(string);
        for (String s : strings) {
            sb.append(s);
        }
        System.out.println(sb.toString());
    }

    private static class ClientEventListener implements ConnectionEventListener {

        @Override
        public void newASdu(Connection connection, ASdu aSdu) {
            log("\nReceived ASDU:\n", aSdu.toString());
        }

        @Override
        public void connectionClosed(Connection connection, IOException e) {
            log("Received connection closed signal. Reason: ");
            if (!e.getMessage().isEmpty()) {
                log(e.getMessage());
            } else {
                log("unknown");
            }
            actionProcessor.close();
        }

        @Override
        public void dataTransferStateChanged(Connection connection, boolean stopped) {
            String dtState = "started";
            if (stopped) {
                dtState = "stopped";
            }
            log("Data transfer was ", dtState);
        }

    }

    private static class ActionExecutor implements ActionListener {
        @Override
        public void actionCalled(String actionKey) throws ActionException {
            try {
                switch (actionKey) {
                    case INTERROGATION_ACTION_KEY:
                        log("** Sending general interrogation command.");
                        connection.interrogation(commonAddrParam.getValue(), CauseOfTransmission.ACTIVATION,
                                new IeQualifierOfInterrogation(20));
                        break;
                    case COUNTER_INTERROGATION_ACTION_KEY:
                        log("Enter the freeze action: 0=read, 1=counter freeze without reset, 2=counter freeze with reset, 3=counter reset");
                        int reference;
                        try {
                            reference = getReference();
                        } catch (NumberFormatException e) {
                            log("Input was not a integer between 0-3.");
                            break;
                        }
                        log("** Sending counter interrogation command.");
                        connection.counterInterrogation(commonAddrParam.getValue(), CauseOfTransmission.ACTIVATION,
                                new IeQualifierOfCounterInterrogation(5, reference));
                        break;
                    case CLOCK_SYNC_ACTION_KEY:
                        log("** Sending synchronize clocks command.");
                        connection.synchronizeClocks(commonAddrParam.getValue(), new IeTime56(System.currentTimeMillis()));
                        break;
                    case SINGLE_COMMAND_SELECT:
                        log("** Sending single command select.");
                        connection.singleCommand(commonAddrParam.getValue(), CauseOfTransmission.ACTIVATION, 5000,
                                new IeSingleCommand(true, 0, true));
                        break;
                    case SINGLE_COMMAND_EXECUTE:
                        log("** Sending single command execute.");
                        connection.singleCommand(commonAddrParam.getValue(), CauseOfTransmission.ACTIVATION, 5000,
                                new IeSingleCommand(false, 0, false));
                        break;
                    case SEND_STOPDT:
                        log("** Sending STOPDT act.");
                        connection.stopDataTransfer();
                        break;
                    case SEND_STARTDT:
                        log("** Sending STARTDT act.");
                        connection.startDataTransfer();
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                throw new ActionException(e);
            }
        }

        private int getReference() throws IOException, NumberFormatException {
            int reference;
            String referenceString = actionProcessor.getReader().readLine();
            reference = Integer.parseInt(referenceString);
            if (reference < 0 || reference > 3) {
                throw new NumberFormatException();
            }
            return reference;
        }

        @Override
        public void quit() {
            log("** Closing connection.");
            connection.close();
        }
    }

}
