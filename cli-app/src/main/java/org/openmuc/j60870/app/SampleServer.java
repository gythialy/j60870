/*
 * Copyright 2014-2026 Fraunhofer ISE
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

import java.io.EOFException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.openmuc.j60870.ASdu;
import org.openmuc.j60870.ASduType;
import org.openmuc.j60870.CauseOfTransmission;
import org.openmuc.j60870.Connection;
import org.openmuc.j60870.ConnectionEventListener;
import org.openmuc.j60870.Server;
import org.openmuc.j60870.Server.Builder;
import org.openmuc.j60870.ServerEventListener;
import org.openmuc.j60870.ie.IeQuality;
import org.openmuc.j60870.ie.IeScaledValue;
import org.openmuc.j60870.ie.IeSingleCommand;
import org.openmuc.j60870.ie.IeTime56;
import org.openmuc.j60870.ie.InformationElement;
import org.openmuc.j60870.ie.InformationObject;
import org.openmuc.j60870.internal.cli.CliParameter;
import org.openmuc.j60870.internal.cli.CliParameterBuilder;
import org.openmuc.j60870.internal.cli.CliParseException;
import org.openmuc.j60870.internal.cli.CliParser;
import org.openmuc.j60870.internal.cli.IntCliParameter;
import org.openmuc.j60870.internal.cli.StringCliParameter;
import org.openmuc.j60870.logging.LoggerInterface;
import org.openmuc.j60870.logging.LoggerFactory;

public class SampleServer {

    private static final LoggerInterface log =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());

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

    public class ServerListener implements ServerEventListener {

        public class ConnectionListener implements ConnectionEventListener {

            private final int connectionId;
            private boolean selected = false;

            public ConnectionListener(Connection connection, int connectionId) {
                this.connectionId = connectionId;
            }

            @Override
            public void newASdu(Connection connection, ASdu aSdu) {
                log.info("Got new ASdu: \n{}\n", aSdu.toString());
                InformationObject informationObject = null;
                try {
                    switch (aSdu.getTypeIdentification()) {
                    // interrogation command
                    case C_IC_NA_1:
                        log.info("Got interrogation command (100). Will send scaled measured values.");
                        connection.sendConfirmation(aSdu);
                        // example GI response values
                        connection.send(new ASdu(ASduType.M_ME_NB_1, true, CauseOfTransmission.INTERROGATED_BY_STATION,
                                false, false, 0, aSdu.getCommonAddress(),
                                new InformationObject(1, new InformationElement[][] {
                                        { new IeScaledValue(-32768), new IeQuality(false, false, false, false, false) },
                                        { new IeScaledValue(10), new IeQuality(false, false, false, false, false) },
                                        { new IeScaledValue(-5),
                                                new IeQuality(false, false, false, false, false) } })));
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
                            log.info("Got single command (45) with select true. Select command.");
                            selected = true;
                            connection.sendConfirmation(aSdu);
                        }
                        else if (!singleCommand.isSelect() && selected) {
                            log.info("Got single command (45) with select false. Execute selected command.");
                            selected = false;
                            connection.sendConfirmation(aSdu);
                        }
                        else {
                            log.info("Got single command (45) with select false. But no command is selected, no execution.");
                        }
                        break;
                    case C_CS_NA_1:
                        IeTime56 ieTime56 = new IeTime56(System.currentTimeMillis());
                        log.info("Got Clock synchronization command (103). Send current time: \n{}", ieTime56);
                        connection.synchronizeClocks(aSdu.getCommonAddress(), ieTime56);
                        break;
                    case C_SE_NB_1:
                        log.info("Got Set point command, scaled value (49)");
                        break;
                    default:
                        log.warn("Got unknown request: {}. Send negative confirm with CoT UNKNOWN_TYPE_ID(44)\n", aSdu);
                        connection.sendConfirmation(aSdu, aSdu.getCommonAddress(), true,
                                CauseOfTransmission.UNKNOWN_TYPE_ID);
                    }

                } catch (EOFException e) {
                    log.error("Will quit listening for commands on connection ({}) because socket was closed.", connectionId);
                } catch (IOException e) {
                    log.error("Will quit listening for commands on connection ({}) because of error: \"{}\".", connectionId, e.getMessage());
                }

            }

            @Override
            public void connectionClosed(Connection connection, IOException e) {
                log.info("Connection ({}}) was closed. {}",connectionId, e.getMessage());
            }

            @Override
            public void dataTransferStateChanged(Connection connection, boolean stopped) {
                String dtState = "started";
                if (stopped) {
                    dtState = "stopped";
                }
                log.info("Data transfer of connection ({}) was {}.", connectionId, dtState);
            }

        }

        @Override
        public ConnectionEventListener connectionIndication(Connection connection) {
            int myConnectionId = connectionIdCounter++;
            log.info("A client (Originator Address {}) has connected using TCP/IP. Will listen for a StartDT request. Connection ID: {}"
                    ,connection.getOriginatorAddress(), myConnectionId);
            log.info("Started data transfer on connection ({}) Will listen for incoming commands.", myConnectionId);

            return new ConnectionListener(connection, myConnectionId);
        }

        @Override
        public void serverStoppedListeningIndication(IOException e) {
            log.warn("Server has stopped listening for new connections : \"{}\". Will quit.",  e.getMessage());
        }

        @Override
        public void connectionAttemptFailed(IOException e) {
            log.error("Connection attempt failed: {}", e.getMessage());
        }

    }

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
            log.error("Error parsing command line parameters: {}\n{}", e.getMessage(), cliParser.getUsageString());
            System.exit(1);
        }
    }

    public void start() throws UnknownHostException {
        log.info(
                "### Starting Server ###\n\nBind Address: {}\nPort:         {}\nIAO length:   {}\nCA length:    {}\nCOT length:   {}\n",
                bindAddressParam.getValue(), portParam.getValue(), iaoLengthParam.getValue(), caLengthParam.getValue(),
                cotLengthParam.getValue());

        Builder builder = Server.builder();
        InetAddress bindAddress = InetAddress.getByName(bindAddressParam.getValue());
        builder.setBindAddr(bindAddress)
                .setPort(portParam.getValue())
                .setIoaFieldLength(iaoLengthParam.getValue())
                .setCommonAddressFieldLength(caLengthParam.getValue())
                .setCotFieldLength(cotLengthParam.getValue());
        Server server = builder.build();

        try {
            server.start(new ServerListener());
        } catch (IOException e) {
            log.error("Unable to start listening: \"{}\". Will quit.", e.getMessage());
        }
    }

}
