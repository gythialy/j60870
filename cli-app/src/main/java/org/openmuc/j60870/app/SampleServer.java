/*
 * This file is part of j60870.
 * For more information visit http://www.openmuc.org
 *
 * You are free to use code of this sample file in any
 * way you like and without any restrictions.
 *
 */
package org.openmuc.j60870.app;

import org.openmuc.j60870.*;
import org.openmuc.j60870.Server.Builder;
import org.openmuc.j60870.ie.*;
import org.openmuc.j60870.internal.cli.*;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SampleServer {

    private static final StringCliParameter bindAddressParam = new CliParameterBuilder("-a")
            .setDescription("The bind address.")
            .buildStringParameter("address", "127.0.0.1");
    private static final IntCliParameter portParam = new CliParameterBuilder("-p").setDescription("The port listen on.")
            .buildIntParameter("port", 2404);
    private static final IntCliParameter iaoLengthParam = new CliParameterBuilder("-iaol")
            .setDescription("Information Object Address (IOA) field length.")
            .buildIntParameter("iao_length", 3);
    private static final IntCliParameter cotLengthParam = new CliParameterBuilder("-cotl")
            .setDescription("Cause Of Transmission (CoT) field length.")
            .buildIntParameter("cot_length", 2);
    private static final IntCliParameter caLengthParam = new CliParameterBuilder("-cal")
            .setDescription("Common Address (CA) field length.")
            .buildIntParameter("ca_length", 2);
    private int connectionIdCounter = 1;

    public static void main(String[] args) throws UnknownHostException {
        cliParser(args);
        new SampleServer().start();
    }

    private static void cliParser(String[] args) {
        List<CliParameter> cliParameters = new ArrayList<>();
        cliParameters.add(bindAddressParam);
        cliParameters.add(portParam);
        cliParameters.add(iaoLengthParam);
        cliParameters.add(caLengthParam);
        cliParameters.add(cotLengthParam);

        CliParser cliParser = new CliParser("j60870-sample-server",
                "A sample server/slave application for IEC 60870-5-104 clients/masters.");
        cliParser.addParameters(cliParameters);
        try {
            cliParser.parseArguments(args);
        } catch (CliParseException e) {
            System.err.println("Error parsing command line parameters: " + e.getMessage());
            System.out.println(cliParser.getUsageString());
            System.exit(1);
        }
    }

    public void start() throws UnknownHostException {
        log("### Starting Server ###\n", "\nBind Address: ", bindAddressParam.getValue(), "\nPort:         ",
                String.valueOf(portParam.getValue()), "\nIAO length:   ", String.valueOf(iaoLengthParam.getValue()),
                "\nCA length:    ", String.valueOf(caLengthParam.getValue()), "\nCOT length:   ",
                String.valueOf(cotLengthParam.getValue()), "\n");

        Builder builder = Server.builder();
        InetAddress bindAddress = InetAddress.getByName(bindAddressParam.getValue());
        builder.setBindAddr(bindAddress)
                .setPort(portParam.getValue())
                .setIoaFieldLength(iaoLengthParam.getValue())
                .setCommonAddressFieldLength(caLengthParam.getValue())
                .setCotFieldLength(cotLengthParam.getValue());// .setMaxNumOfOutstandingIPdus(10);
        Server server = builder.build();

        try {
            server.start(new ServerListener());
        } catch (IOException e) {
            log("Unable to start listening: \"", e.getMessage(), "\". Will quit.");
        }
    }

    private void log(String... strings) {
        String time = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS ").format(new Date());
        println(time, strings);
    }

    private void println(String string, String... strings) {
        StringBuilder sb = new StringBuilder();
        sb.append(string);
        for (String s : strings) {
            sb.append(s);
        }
        System.out.println(sb.toString());
    }

    public class ServerListener implements ServerEventListener {

        @Override
        public void connectionIndication(Connection connection) {

            int myConnectionId = connectionIdCounter++;
            log("A client (Originator Address " + connection.getOriginatorAddress()
                    + ") has connected using TCP/IP. Will listen for a StartDT request. Connection ID: "
                    + myConnectionId);
            log("Started data transfer on connection (" + myConnectionId, ") Will listen for incoming commands.");

            connection.setConnectionListener(new ConnectionListener(connection, myConnectionId));
        }

        @Override
        public void serverStoppedListeningIndication(IOException e) {
            log("Server has stopped listening for new connections : \"", e.getMessage(), "\". Will quit.");
        }

        @Override
        public void connectionAttemptFailed(IOException e) {
            log("Connection attempt failed: ", e.getMessage());
        }

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
                log("Got new ASdu:");
                println(aSdu.toString(), "\n");
                InformationObject informationObject = null;
                try {
                    switch (aSdu.getTypeIdentification()) {
                        // interrogation command
                        case C_IC_NA_1:
                            log("Got interrogation command (100). Will send scaled measured values.");
                            connection.sendConfirmation(aSdu);
                            // example GI response values
                            connection.send(new ASdu(ASduType.M_ME_NB_1, true, CauseOfTransmission.INTERROGATED_BY_STATION,
                                    false, false, 0, aSdu.getCommonAddress(),
                                    new InformationObject(1, new InformationElement[][]{
                                            {new IeScaledValue(-32768), new IeQuality(false, false, false, false, false)},
                                            {new IeScaledValue(10), new IeQuality(false, false, false, false, false)},
                                            {new IeScaledValue(-5),
                                                    new IeQuality(false, false, false, false, false)}})));
                            connection.sendActivationTermination(aSdu);
                            break;
                        case C_SC_NA_1:
                            informationObject = aSdu.getInformationObjects()[0];
                            IeSingleCommand singleCommand = (IeSingleCommand) informationObject
                                    .getInformationElements()[0][0];

                            if (informationObject.getInformationObjectAddress() != 5000) {
                                break;
                            }
                            if (singleCommand.isSelect()) {
                                log("Got single command (45) with select true. Select command.");
                                selected = true;
                                connection.sendConfirmation(aSdu);
                            } else if (!singleCommand.isSelect() && selected) {
                                log("Got single command (45) with select false. Execute selected command.");
                                selected = false;
                                connection.sendConfirmation(aSdu);
                            } else {
                                log("Got single command (45) with select false. But no command is selected, no execution.");
                            }
                            break;
                        case C_CS_NA_1:
                            IeTime56 ieTime56 = new IeTime56(System.currentTimeMillis());
                            log("Got Clock synchronization command (103). Send current time: \n", ieTime56.toString());
                            connection.synchronizeClocks(aSdu.getCommonAddress(), ieTime56);
                            break;
                        case C_SE_NB_1:
                            log("Got Set point command, scaled value (49)");
                            break;
                        default:
                            log("Got unknown request: ", aSdu.toString(),
                                    ". Send negative confirm with CoT UNKNOWN_TYPE_ID(44)\n");
                            connection.sendConfirmation(aSdu, aSdu.getCommonAddress(), true,
                                    CauseOfTransmission.UNKNOWN_TYPE_ID);
                    }

                } catch (EOFException e) {
                    log("Will quit listening for commands on connection (" + connectionId,
                            ") because socket was closed.");
                } catch (IOException e) {
                    log("Will quit listening for commands on connection (" + connectionId, ") because of error: \"",
                            e.getMessage(), "\".");
                }

            }

            @Override
            public void connectionClosed(IOException e) {
                log("Connection (" + connectionId, ") was closed. ", e.getMessage());
            }

            @Override
            public void dataTransferStateChanged(boolean stopped) {
                String dtState = "started";
                if (stopped) {
                    dtState = "stopped";
                }
                log("Data transfer of connection (" + connectionId + ") was ", dtState, ".");
            }

        }

    }

}
