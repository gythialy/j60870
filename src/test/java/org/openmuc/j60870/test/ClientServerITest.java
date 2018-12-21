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
package org.openmuc.j60870.test;

import java.io.IOException;
import java.net.InetAddress;

import org.junit.Assert;
import org.junit.Test;
import org.openmuc.j60870.ASdu;
import org.openmuc.j60870.CauseOfTransmission;
import org.openmuc.j60870.ClientConnectionBuilder;
import org.openmuc.j60870.Connection;
import org.openmuc.j60870.ConnectionEventListener;
import org.openmuc.j60870.IeBinaryCounterReading;
import org.openmuc.j60870.IeBinaryStateInformation;
import org.openmuc.j60870.IeDoubleCommand;
import org.openmuc.j60870.IeDoubleCommand.DoubleCommandState;
import org.openmuc.j60870.IeDoublePointWithQuality;
import org.openmuc.j60870.IeDoublePointWithQuality.DoublePointInformation;
import org.openmuc.j60870.IeNormalizedValue;
import org.openmuc.j60870.IeProtectionQuality;
import org.openmuc.j60870.IeProtectionStartEvent;
import org.openmuc.j60870.IeQualifierOfInterrogation;
import org.openmuc.j60870.IeQualifierOfSetPointCommand;
import org.openmuc.j60870.IeQuality;
import org.openmuc.j60870.IeScaledValue;
import org.openmuc.j60870.IeShortFloat;
import org.openmuc.j60870.IeSingleCommand;
import org.openmuc.j60870.IeSinglePointWithQuality;
import org.openmuc.j60870.IeSingleProtectionEvent;
import org.openmuc.j60870.IeSingleProtectionEvent.EventState;
import org.openmuc.j60870.IeStatusAndStatusChanges;
import org.openmuc.j60870.IeTime16;
import org.openmuc.j60870.IeTime24;
import org.openmuc.j60870.IeTime56;
import org.openmuc.j60870.IeValueWithTransientState;
import org.openmuc.j60870.InformationElement;
import org.openmuc.j60870.InformationObject;
import org.openmuc.j60870.Server;
import org.openmuc.j60870.ServerEventListener;
import org.openmuc.j60870.TypeId;
import org.openmuc.j60870.Util;

public class ClientServerITest implements ServerEventListener, ConnectionEventListener {

    private final static int port = 2404;

    String host = "127.0.0.1";
    Server serverSap = null;
    int counter = 0;
    int serverCounter = 0;
    int counter2 = 0;
    Connection serverConnection;
    Exception exception = null;
    volatile long clientTimestamp = 0;
    volatile long serverTimestamp = 0;

    private class ServerReceiver implements ConnectionEventListener {

        @Override
        public void newASdu(ASdu aSdu) {

            // try {
            // logger.debug("\n" + aSdu.toString() + "\n");
            // } catch (Exception e) {
            // logger.debug("------------------------------------------------------------------------", e);
            // }

            serverCounter++;

            try {

                if (serverCounter == 1) {

                    // answer Single Command

                    Assert.assertEquals(TypeId.C_SC_NA_1, aSdu.getTypeIdentification());

                    Assert.assertEquals(52031, aSdu.getCommonAddress());

                    IeSingleCommand singleCommand = (IeSingleCommand) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][0];
                    Assert.assertEquals(true, singleCommand.isCommandStateOn());
                    Assert.assertEquals(true, singleCommand.isSelect());
                    Assert.assertEquals(3, singleCommand.getQualifier());

                    serverConnection.send(new ASdu(TypeId.C_SC_NA_1, false, CauseOfTransmission.ACTIVATION_CON, false,
                            false, 0, aSdu.getCommonAddress(), new InformationObject[] {
                                    new InformationObject(0, new InformationElement[][] { { singleCommand } }) }));

                    // answer Single Command - END

                }
                else if (serverCounter == 2) {

                    // answer clock synchronization

                    Assert.assertEquals(TypeId.C_CS_NA_1, aSdu.getTypeIdentification());

                    Assert.assertTrue(clientTimestamp <= ((IeTime56) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][0]).getTimestamp());

                    serverTimestamp = System.currentTimeMillis();

                    aSdu = new ASdu(TypeId.C_CS_NA_1, false, CauseOfTransmission.ACTIVATION_CON, false, false, 0,
                            aSdu.getCommonAddress(), new InformationObject[] { new InformationObject(0,
                                    new InformationElement[][] { { new IeTime56(serverTimestamp) } }) });

                    serverConnection.send(aSdu);

                    // answer clock synchronization - END

                }
                else if (serverCounter == 3) {

                    // answer Double Command with time tag

                    Assert.assertEquals(TypeId.C_DC_TA_1, aSdu.getTypeIdentification());

                    IeDoubleCommand doubleCommand = (IeDoubleCommand) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][0];
                    Assert.assertEquals(DoubleCommandState.NOT_PERMITTED_A, doubleCommand.getCommandState());
                    Assert.assertEquals(true, doubleCommand.isSelect());
                    Assert.assertEquals(3, doubleCommand.getQualifier());

                    serverConnection.send(new ASdu(TypeId.C_DC_TA_1, false, CauseOfTransmission.ACTIVATION_CON, false,
                            false, 0, aSdu.getCommonAddress(), aSdu.getInformationObjects()));
                    // answer Double Command with time tag - End
                }
                else if (serverCounter == 4) {

                    // answer set-point normalized value command

                    serverConnection.send(new ASdu(TypeId.C_SE_NA_1, false, CauseOfTransmission.ACTIVATION_CON, false,
                            false, 0, aSdu.getCommonAddress(), aSdu.getInformationObjects()));
                    // answer set-point normalized value command - End

                }
                else if (serverCounter == 5) {

                    Assert.assertEquals(TypeId.C_IC_NA_1, aSdu.getTypeIdentification());

                    serverConnection.send(new ASdu(TypeId.C_IC_NA_1, false, CauseOfTransmission.ACTIVATION_CON, false,
                            false, 0, aSdu.getCommonAddress(), new InformationObject[] { new InformationObject(0,
                                    new InformationElement[][] { { new IeQualifierOfInterrogation(20) } }) }));

                }
                else if (serverCounter == 6) {

                    Assert.assertEquals(TypeId.M_SP_NA_1, aSdu.getTypeIdentification());

                    // send spontaneous packets

                    // 1
                    serverConnection.send(new ASdu(TypeId.M_SP_NA_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] {
                                    new InformationObject(1,
                                            new InformationElement[][] {
                                                    { new IeSinglePointWithQuality(true, true, true, true, true) } }),
                                    new InformationObject(2, new InformationElement[][] {
                                            { new IeSinglePointWithQuality(false, false, false, false, false) } }) }));

                    // 2
                    serverConnection
                            .send(new ASdu(TypeId.M_SP_NA_1, true, CauseOfTransmission.SPONTANEOUS, false, false, 0,
                                    aSdu.getCommonAddress(),
                                    new InformationObject[] { new InformationObject(1, new InformationElement[][] {
                                            { new IeSinglePointWithQuality(true, true, true, true, true) },
                                            { new IeSinglePointWithQuality(false, false, false, false, false) } }) }));

                    // 3
                    serverConnection.send(new ASdu(TypeId.M_SP_TA_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] {
                                    new InformationObject(1,
                                            new InformationElement[][] {
                                                    { new IeSinglePointWithQuality(true, true, true, true, true),
                                                            new IeTime24(50000) } }),
                                    new InformationObject(2,
                                            new InformationElement[][] {
                                                    { new IeSinglePointWithQuality(false, false, false, false, false),
                                                            new IeTime24(60000) } }) }));

                    // 4
                    serverConnection.send(new ASdu(TypeId.M_DP_NA_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] { new InformationObject(1,
                                    new InformationElement[][] {
                                            { new IeDoublePointWithQuality(DoublePointInformation.OFF, true, true, true,
                                                    true) } }) }));

                    // 5
                    serverConnection.send(new ASdu(TypeId.M_ST_NA_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] { new InformationObject(1,
                                    new InformationElement[][] {
                                            { new IeValueWithTransientState(-5, true),
                                                    new IeQuality(true, true, true, true, true) },
                                            { new IeValueWithTransientState(-5, false),
                                                    new IeQuality(true, true, true, true, true) },
                                            { new IeValueWithTransientState(-64, true),
                                                    new IeQuality(true, true, true, true, true) },
                                            { new IeValueWithTransientState(10, false),
                                                    new IeQuality(true, true, true, true, true) } }) }));

                    // 6
                    serverConnection.send(new ASdu(TypeId.M_BO_NA_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] { new InformationObject(1,
                                    new InformationElement[][] {
                                            { new IeBinaryStateInformation(0xff),
                                                    new IeQuality(true, true, true, true, true) },
                                            { new IeBinaryStateInformation(0xffffffff),
                                                    new IeQuality(true, true, true, true, true) } }) }));

                    // 7
                    serverConnection.send(new ASdu(TypeId.M_ME_NA_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] { new InformationObject(1, new InformationElement[][] {
                                    { new IeNormalizedValue(-32768), new IeQuality(true, true, true, true, true) },
                                    { new IeNormalizedValue(0), new IeQuality(true, true, true, true, true) } }) }));

                    // 8
                    serverConnection.send(new ASdu(TypeId.M_ME_NB_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] { new InformationObject(1, new InformationElement[][] {
                                    { new IeScaledValue(-32768), new IeQuality(true, true, true, true, true) },
                                    { new IeScaledValue(10), new IeQuality(true, true, true, true, true) },
                                    { new IeScaledValue(-5), new IeQuality(true, true, true, true, true) } }) }));

                    serverConnection.send(new ASdu(TypeId.M_ME_NC_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] { new InformationObject(1, new InformationElement[][] {
                                    { new IeShortFloat(-32768.2f), new IeQuality(true, true, true, true, true) },
                                    { new IeShortFloat(10.5f), new IeQuality(true, true, true, true, true) } }) }));

                    // 10
                    serverConnection.send(new ASdu(TypeId.M_IT_NA_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] { new InformationObject(1,
                                    new InformationElement[][] {
                                            { new IeBinaryCounterReading(-300, 5, true, true, true) },
                                            { new IeBinaryCounterReading(-300, 4, false, false, false) } }) }));

                    serverConnection.send(new ASdu(TypeId.M_EP_TA_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] {
                                    new InformationObject(1,
                                            new InformationElement[][] { {
                                                    new IeSingleProtectionEvent(IeSingleProtectionEvent.EventState.OFF,
                                                            true, true, true, true, true),
                                                    new IeTime16(300), new IeTime24(400) } }),
                                    new InformationObject(2,
                                            new InformationElement[][] { {
                                                    new IeSingleProtectionEvent(IeSingleProtectionEvent.EventState.ON,
                                                            false, false, false, false, false),
                                                    new IeTime16(300), new IeTime24(400) } }) }));

                    // 12
                    serverConnection.send(new ASdu(TypeId.M_EP_TB_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] { new InformationObject(1,
                                    new InformationElement[][] {
                                            { new IeProtectionStartEvent(true, true, true, true, true, true),
                                                    new IeProtectionQuality(true, true, true, true, true),
                                                    new IeTime16(300), new IeTime24(400) } }) }));

                    serverConnection.send(new ASdu(TypeId.M_PS_NA_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] { new InformationObject(1,
                                    new InformationElement[][] { { new IeStatusAndStatusChanges(0xff0000ff),
                                            new IeQuality(true, true, true, true, true) } }) }));

                    Thread.sleep(1000);

                    // 14
                    serverConnection.send(new ASdu(TypeId.M_ME_ND_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(), new InformationObject[] { new InformationObject(1,
                                    new InformationElement[][] { { new IeNormalizedValue(3) } }) }));

                    serverConnection
                            .send(new ASdu(TypeId.M_SP_TB_1, false, CauseOfTransmission.SPONTANEOUS, false, false, 0,
                                    aSdu.getCommonAddress(),
                                    new InformationObject[] {
                                            new InformationObject(1,
                                                    new InformationElement[][] { {
                                                            new IeSinglePointWithQuality(true, true, true, true, true),
                                                            new IeTime56(serverTimestamp) } }) }));

                    // 16
                    serverConnection.send(new ASdu(TypeId.M_IT_TB_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[] { new InformationObject(1,
                                    new InformationElement[][] {
                                            { new IeBinaryCounterReading(-300, 5, true, true, true),
                                                    new IeTime56(serverTimestamp) } }) }));

                    serverConnection.send(new ASdu(TypeId.PRIVATE_136, false, 1, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(), new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 }));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public void connectionClosed(IOException e) {
        }

    }

    // public static void main(String[] args) throws IOException {
    // ServerSap serverSap;
    // serverSap = new ServerSap(new ClientServerITest());
    // serverSap.startListening();
    // }

    @Test
    public void testClientServerCom() throws Exception {

        serverSap = new Server.Builder().setPort(port).build();// (port, this);
        serverSap.start(this);

        Connection clientConnection = new ClientConnectionBuilder(InetAddress.getByName("127.0.0.1")).setPort(port)
                .connect();
        try {

            clientConnection.startDataTransfer(this, 5000);

            clientTimestamp = System.currentTimeMillis();

            int commonAddress = 1;

            clientConnection.singleCommand(Util.convertToCommonAddress(63, 203), CauseOfTransmission.ACTIVATION, 1,
                    new IeSingleCommand(true, 3, true));

            clientConnection.synchronizeClocks(commonAddress, new IeTime56(System.currentTimeMillis()));

            clientConnection.doubleCommandWithTimeTag(commonAddress, CauseOfTransmission.ACTIVATION, 2,
                    new IeDoubleCommand(DoubleCommandState.NOT_PERMITTED_A, 3, true),
                    new IeTime56(System.currentTimeMillis()));

            clientConnection.setNormalizedValueCommand(commonAddress, CauseOfTransmission.ACTIVATION, 3,
                    new IeNormalizedValue(30000), new IeQualifierOfSetPointCommand(3, false));

            clientConnection.interrogation(commonAddress, CauseOfTransmission.ACTIVATION,
                    new IeQualifierOfInterrogation(20));

            clientConnection.send(
                    new ASdu(TypeId.M_SP_NA_1, false, CauseOfTransmission.SPONTANEOUS, false, false, 0, commonAddress,
                            new InformationObject[] {
                                    new InformationObject(1,
                                            new InformationElement[][] {
                                                    { new IeSinglePointWithQuality(true, true, true, true, true) } }),
                                    new InformationObject(2, new InformationElement[][] {
                                            { new IeSinglePointWithQuality(false, false, false, false, false) } }) }));

            Thread.sleep(3000);

            if (exception != null) {
                throw new AssertionError(exception);
            }

            Assert.assertEquals(17, counter);
            Assert.assertEquals(17, counter2);
        } catch (Exception e) {
            if (exception != null) {
                throw new AssertionError(exception);
            }
            else {
                throw e;
            }
        } finally {
            clientConnection.close();

        }

    }

    @Override
    public void connectionIndication(Connection connection) {

        try {

            try {
                this.serverConnection = connection;
                connection.waitForStartDT(new ServerReceiver(), 5000);
            } catch (IOException e) {
                System.err.println("IOException waiting for StartDT");
                e.printStackTrace();
                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void serverStoppedListeningIndication(IOException e) {
    }

    @Override
    public void newASdu(ASdu aSdu) {

        if (aSdu.getCauseOfTransmission() == CauseOfTransmission.ACTIVATION_CON) {
            return;
        }

        // try {
        // logger.debug("\n" + aSdu.toString() + "\n");
        // } catch (Exception e) {
        // logger.debug("------------------------------------------------------------------------", e);
        // }

        try {

            counter++;

            if (counter == 1) {
                IeSinglePointWithQuality pointWithQuality = (IeSinglePointWithQuality) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][0];

                Assert.assertEquals(true, pointWithQuality.isOn());
                Assert.assertEquals(true, pointWithQuality.isBlocked());
                Assert.assertEquals(true, pointWithQuality.isInvalid());
                Assert.assertEquals(true, pointWithQuality.isNotTopical());
                Assert.assertEquals(true, pointWithQuality.isSubstituted());

                IeSinglePointWithQuality io2 = (IeSinglePointWithQuality) aSdu.getInformationObjects()[1]
                        .getInformationElements()[0][0];

                Assert.assertEquals(false, io2.isOn());
                Assert.assertEquals(false, io2.isBlocked());
                Assert.assertEquals(false, io2.isInvalid());
                Assert.assertEquals(false, io2.isNotTopical());
                Assert.assertEquals(false, io2.isSubstituted());

                counter2++;
            }
            else if (counter == 2) {
                IeSinglePointWithQuality pointWithQuality = (IeSinglePointWithQuality) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][0];

                Assert.assertEquals(true, pointWithQuality.isOn());
                Assert.assertEquals(true, pointWithQuality.isBlocked());
                Assert.assertEquals(true, pointWithQuality.isInvalid());
                Assert.assertEquals(true, pointWithQuality.isNotTopical());
                Assert.assertEquals(true, pointWithQuality.isSubstituted());

                IeSinglePointWithQuality io2 = (IeSinglePointWithQuality) aSdu.getInformationObjects()[0]
                        .getInformationElements()[1][0];

                Assert.assertEquals(false, io2.isOn());
                Assert.assertEquals(false, io2.isBlocked());
                Assert.assertEquals(false, io2.isInvalid());
                Assert.assertEquals(false, io2.isNotTopical());
                Assert.assertEquals(false, io2.isSubstituted());

                counter2++;
            }
            else if (counter == 3) {
                IeSinglePointWithQuality pointWithQuality = (IeSinglePointWithQuality) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][0];
                IeTime24 time24 = (IeTime24) aSdu.getInformationObjects()[0].getInformationElements()[0][1];

                Assert.assertEquals(true, pointWithQuality.isOn());
                Assert.assertEquals(true, pointWithQuality.isBlocked());
                Assert.assertEquals(true, pointWithQuality.isInvalid());
                Assert.assertEquals(true, pointWithQuality.isNotTopical());
                Assert.assertEquals(true, pointWithQuality.isSubstituted());

                Assert.assertEquals(50000, time24.getTimeInMs());

                counter2++;
            }
            else if (counter == 4) {
                IeDoublePointWithQuality doublePointWithQuality = (IeDoublePointWithQuality) aSdu
                        .getInformationObjects()[0].getInformationElements()[0][0];

                Assert.assertEquals(true,
                        doublePointWithQuality.getDoublePointInformation() == DoublePointInformation.OFF);
                Assert.assertEquals(true, doublePointWithQuality.isBlocked());
                Assert.assertEquals(true, doublePointWithQuality.isInvalid());
                Assert.assertEquals(true, doublePointWithQuality.isNotTopical());
                Assert.assertEquals(true, doublePointWithQuality.isSubstituted());

                counter2++;

            }
            else if (counter == 5) {

                IeValueWithTransientState valueWithTransientState = (IeValueWithTransientState) aSdu
                        .getInformationObjects()[0].getInformationElements()[0][0];

                Assert.assertEquals(-5, valueWithTransientState.getValue());
                Assert.assertEquals(true, valueWithTransientState.isTransientState());

                IeValueWithTransientState valueWithTransientState2 = (IeValueWithTransientState) aSdu
                        .getInformationObjects()[0].getInformationElements()[1][0];

                Assert.assertEquals(-5, valueWithTransientState2.getValue());
                Assert.assertEquals(false, valueWithTransientState2.isTransientState());

                IeValueWithTransientState valueWithTransientState3 = (IeValueWithTransientState) aSdu
                        .getInformationObjects()[0].getInformationElements()[2][0];

                Assert.assertEquals(-64, valueWithTransientState3.getValue());
                Assert.assertEquals(true, valueWithTransientState3.isTransientState());

                IeValueWithTransientState valueWithTransientState4 = (IeValueWithTransientState) aSdu
                        .getInformationObjects()[0].getInformationElements()[3][0];

                Assert.assertEquals(10, valueWithTransientState4.getValue());
                Assert.assertEquals(false, valueWithTransientState4.isTransientState());

                counter2++;
            }
            else if (counter == 6) {

                IeBinaryStateInformation binaryStateInformation = (IeBinaryStateInformation) aSdu
                        .getInformationObjects()[0].getInformationElements()[0][0];

                Assert.assertEquals(0xff, binaryStateInformation.getValue());
                Assert.assertEquals(true, binaryStateInformation.getBinaryState(8));
                Assert.assertEquals(false, binaryStateInformation.getBinaryState(9));

                IeBinaryStateInformation binaryStateInformation2 = (IeBinaryStateInformation) aSdu
                        .getInformationObjects()[0].getInformationElements()[1][0];

                Assert.assertEquals(0xffffffff, binaryStateInformation2.getValue());
                counter2++;
            }
            else if (counter == 7) {

                IeNormalizedValue normalizedValue = (IeNormalizedValue) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][0];

                Assert.assertEquals(-32768, normalizedValue.getUnnormalizedValue());

                IeNormalizedValue normalizedValue2 = (IeNormalizedValue) aSdu.getInformationObjects()[0]
                        .getInformationElements()[1][0];

                Assert.assertEquals(0, normalizedValue2.getUnnormalizedValue());
                counter2++;
            }
            else if (counter == 8) {

                IeScaledValue scaledValue = (IeScaledValue) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][0];

                Assert.assertEquals(-32768, scaledValue.getUnnormalizedValue());

                IeScaledValue scaledValue2 = (IeScaledValue) aSdu.getInformationObjects()[0]
                        .getInformationElements()[1][0];

                Assert.assertEquals(10, scaledValue2.getUnnormalizedValue());
                counter2++;
            }
            else if (counter == 9) {

                IeShortFloat scaledValue = (IeShortFloat) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][0];

                Assert.assertEquals(-32768.2, scaledValue.getValue(), 0.1);

                IeShortFloat scaledValue2 = (IeShortFloat) aSdu.getInformationObjects()[0]
                        .getInformationElements()[1][0];

                Assert.assertEquals(10.5, scaledValue2.getValue(), 0.1);
                counter2++;
            }
            else if (counter == 10) {

                IeBinaryCounterReading binaryCounterReading = (IeBinaryCounterReading) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][0];

                Assert.assertEquals(-300, binaryCounterReading.getCounterReading());
                Assert.assertEquals(5, binaryCounterReading.getSequenceNumber());
                Assert.assertEquals(true, binaryCounterReading.isCarry());
                Assert.assertEquals(true, binaryCounterReading.isCounterAdjusted());
                Assert.assertEquals(true, binaryCounterReading.isInvalid());

                IeBinaryCounterReading binaryCounterReading2 = (IeBinaryCounterReading) aSdu.getInformationObjects()[0]
                        .getInformationElements()[1][0];

                Assert.assertEquals(-300, binaryCounterReading2.getCounterReading());
                Assert.assertEquals(4, binaryCounterReading2.getSequenceNumber());
                Assert.assertEquals(false, binaryCounterReading2.isCarry());
                Assert.assertEquals(false, binaryCounterReading2.isCounterAdjusted());
                Assert.assertEquals(false, binaryCounterReading2.isInvalid());
                counter2++;
            }
            else if (counter == 11) {

                IeSingleProtectionEvent singleProtectionEvent = (IeSingleProtectionEvent) aSdu
                        .getInformationObjects()[0].getInformationElements()[0][0];

                Assert.assertEquals(EventState.OFF, singleProtectionEvent.getEventState());
                Assert.assertEquals(true, singleProtectionEvent.isBlocked());
                Assert.assertEquals(true, singleProtectionEvent.isElapsedTimeInvalid());
                Assert.assertEquals(true, singleProtectionEvent.isEventInvalid());
                Assert.assertEquals(true, singleProtectionEvent.isNotTopical());
                Assert.assertEquals(true, singleProtectionEvent.isSubstituted());

                IeTime16 time16 = (IeTime16) aSdu.getInformationObjects()[0].getInformationElements()[0][1];
                Assert.assertEquals(300, time16.getTimeInMs());

                IeTime24 time24 = (IeTime24) aSdu.getInformationObjects()[0].getInformationElements()[0][2];
                Assert.assertEquals(400, time24.getTimeInMs());

                IeSingleProtectionEvent singleProtectionEvent2 = (IeSingleProtectionEvent) aSdu
                        .getInformationObjects()[1].getInformationElements()[0][0];

                Assert.assertEquals(EventState.ON, singleProtectionEvent2.getEventState());
                Assert.assertEquals(false, singleProtectionEvent2.isBlocked());
                Assert.assertEquals(false, singleProtectionEvent2.isElapsedTimeInvalid());
                Assert.assertEquals(false, singleProtectionEvent2.isEventInvalid());
                Assert.assertEquals(false, singleProtectionEvent2.isNotTopical());
                Assert.assertEquals(false, singleProtectionEvent2.isSubstituted());
                counter2++;
            }
            else if (counter == 12) {

                IeProtectionStartEvent singleProtectionEvent = (IeProtectionStartEvent) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][0];

                Assert.assertEquals(true, singleProtectionEvent.isGeneralStart());
                Assert.assertEquals(true, singleProtectionEvent.isStartOperationIe());
                Assert.assertEquals(true, singleProtectionEvent.isStartOperationL1());
                Assert.assertEquals(true, singleProtectionEvent.isStartOperationL2());
                Assert.assertEquals(true, singleProtectionEvent.isStartOperationL3());
                Assert.assertEquals(true, singleProtectionEvent.isStartReverseOperation());

                IeProtectionQuality protectionQuality = (IeProtectionQuality) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][1];

                Assert.assertEquals(true, protectionQuality.isBlocked());
                Assert.assertEquals(true, protectionQuality.isElapsedTimeInvalid());
                Assert.assertEquals(true, protectionQuality.isInvalid());
                Assert.assertEquals(true, protectionQuality.isNotTopical());
                Assert.assertEquals(true, protectionQuality.isSubstituted());

                IeTime16 time16 = (IeTime16) aSdu.getInformationObjects()[0].getInformationElements()[0][2];
                Assert.assertEquals(300, time16.getTimeInMs());

                counter2++;
            }
            else if (counter == 13) {

                IeStatusAndStatusChanges statusAndStatusChanges = (IeStatusAndStatusChanges) aSdu
                        .getInformationObjects()[0].getInformationElements()[0][0];

                Assert.assertEquals(0xff0000ff, statusAndStatusChanges.getValue());
                Assert.assertEquals(false, statusAndStatusChanges.getStatus(8));
                Assert.assertEquals(true, statusAndStatusChanges.getStatus(9));
                Assert.assertEquals(true, statusAndStatusChanges.hasStatusChanged(8));
                Assert.assertEquals(false, statusAndStatusChanges.hasStatusChanged(9));

                counter2++;
            }
            else if (counter == 14) {

                IeNormalizedValue normalizedValue = (IeNormalizedValue) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][0];

                Assert.assertEquals(3, normalizedValue.getUnnormalizedValue());

                counter2++;
            }
            else if (counter == 15) {

                IeTime56 time56 = (IeTime56) aSdu.getInformationObjects()[0].getInformationElements()[0][1];

                Assert.assertEquals(serverTimestamp, time56.getTimestamp());

                counter2++;
            }
            else if (counter == 16) {

                IeBinaryCounterReading binaryCounterReading = (IeBinaryCounterReading) aSdu.getInformationObjects()[0]
                        .getInformationElements()[0][0];

                Assert.assertEquals(-300, binaryCounterReading.getCounterReading());
                Assert.assertEquals(5, binaryCounterReading.getSequenceNumber());
                Assert.assertEquals(true, binaryCounterReading.isCarry());
                Assert.assertEquals(true, binaryCounterReading.isCounterAdjusted());
                Assert.assertEquals(true, binaryCounterReading.isInvalid());

                counter2++;
            }
            else if (counter == 17) {

                Assert.assertNull(aSdu.getInformationObjects());
                Assert.assertArrayEquals(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 }, aSdu.getPrivateInformation());

                counter2++;
            }

        } catch (Exception e) {
            if (exception == null) {
                exception = e;
                e.printStackTrace();
            }
        }

    }

    @Override
    public void connectionClosed(IOException e) {

    }

    @Override
    public void connectionAttemptFailed(IOException e) {

    }

}
