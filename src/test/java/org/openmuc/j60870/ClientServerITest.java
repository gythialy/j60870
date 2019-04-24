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
package org.openmuc.j60870;

import org.junit.Assert;
import org.junit.Test;
import org.openmuc.j60870.ie.*;
import org.openmuc.j60870.ie.IeBinaryCounterReading.Flag;
import org.openmuc.j60870.ie.IeDoubleCommand.DoubleCommandState;
import org.openmuc.j60870.ie.IeDoublePointWithQuality.DoublePointInformation;
import org.openmuc.j60870.ie.IeSingleProtectionEvent.EventState;

import java.io.IOException;

import static org.junit.Assert.*;

public class ClientServerITest {

    private static final int PORT = 2404;

    static {
        System.out.println(InformationObject.class);
    }

    Server serverSap = null;
    int counter = 0;
    int serverCounter = 0;
    int counter2 = 0;
    Connection serverConnection;
    Exception exception = null;
    volatile long clientTimestamp = 0;
    volatile long serverTimestamp = 0;

    @Test
    public void testClientServerCom() throws Exception {

        serverSap = Server.builder().setPort(PORT).build();
        serverSap.start(new ServerlisnerImpl());

        try (Connection clientConnection = new ClientConnectionBuilder("127.0.0.1").setPort(PORT).build()) {

            clientConnection.startDataTransfer(new ConnectionListenerImpl(), 5000);

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
                    new ASdu(ASduType.M_SP_NA_1, false, CauseOfTransmission.SPONTANEOUS, false, false, 0, commonAddress,
                            new InformationObject[]{
                                    new InformationObject(1,
                                            new InformationElement[][]{
                                                    {new IeSinglePointWithQuality(true, true, true, true, true)}}),
                                    new InformationObject(2, new InformationElement[][]{
                                            {new IeSinglePointWithQuality(false, false, false, false, false)}})}));

            Thread.sleep(500);

            assertEquals(17, counter);
            assertEquals(17, counter2);

            if (exception != null) {
                throw exception;
            }
        }

    }

    private class ServerReceiver implements ConnectionEventListener {

        @Override
        public void newASdu(ASdu aSdu) {

            serverCounter++;

            try {

                if (serverCounter == 1) {

                    System.out.println("Server: answer Single Command");

                    assertEquals(ASduType.C_SC_NA_1, aSdu.getTypeIdentification());

                    assertEquals(52031, aSdu.getCommonAddress());

                    IeSingleCommand singleCommand = (IeSingleCommand) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][0];
                    assertTrue(singleCommand.isCommandStateOn());
                    assertTrue(singleCommand.isSelect());
                    assertEquals(3, singleCommand.getQualifier());

                    serverConnection.send(new ASdu(ASduType.C_SC_NA_1, false, CauseOfTransmission.ACTIVATION_CON, false,
                            false, 0, aSdu.getCommonAddress(), new InformationObject[]{
                            new InformationObject(0, new InformationElement[][]{{singleCommand}})}));

                    System.out.println("Server: answer Single Command - END");

                } else if (serverCounter == 2) {

                    System.out.println("Server: answer clock synchronisation");

                    assertEquals(ASduType.C_CS_NA_1, aSdu.getTypeIdentification());

                    assertTrue(clientTimestamp <= ((IeTime56) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][0]).getTimestamp());

                    serverTimestamp = System.currentTimeMillis();

                    aSdu = new ASdu(ASduType.C_CS_NA_1, false, CauseOfTransmission.ACTIVATION_CON, false, false, 0,
                            aSdu.getCommonAddress(), new InformationObject[]{new InformationObject(0,
                            new InformationElement[][]{{new IeTime56(serverTimestamp)}})});

                    serverConnection.send(aSdu);

                    System.out.println("Server: answer clock synchronisation - END");

                } else if (serverCounter == 3) {

                    System.out.println("Server: answer Double Command with time tag");

                    assertEquals(ASduType.C_DC_TA_1, aSdu.getTypeIdentification());

                    IeDoubleCommand doubleCommand = (IeDoubleCommand) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][0];
                    assertEquals(DoubleCommandState.NOT_PERMITTED_A, doubleCommand.getCommandState());
                    assertTrue(doubleCommand.isSelect());
                    assertEquals(3, doubleCommand.getQualifier());

                    serverConnection.send(new ASdu(ASduType.C_DC_TA_1, false, CauseOfTransmission.ACTIVATION_CON, false,
                            false, 0, aSdu.getCommonAddress(), aSdu.getInformationObjects()));

                    System.out.println("Server: answer Double Command with time tag - End");

                } else if (serverCounter == 4) {

                    System.out.println("Server: answer set-point normalized value command");

                    serverConnection.send(new ASdu(ASduType.C_SE_NA_1, false, CauseOfTransmission.ACTIVATION_CON, false,
                            false, 0, aSdu.getCommonAddress(), aSdu.getInformationObjects()));

                    System.out.println("Server: answer set-point normalized value command - End");

                } else if (serverCounter == 5) {

                    System.out.println("Server: Interrogation command");

                    assertEquals(ASduType.C_IC_NA_1, aSdu.getTypeIdentification());

                    serverConnection.send(new ASdu(ASduType.C_IC_NA_1, false, CauseOfTransmission.ACTIVATION_CON, false,
                            false, 0, aSdu.getCommonAddress(), new InformationObject[]{new InformationObject(0,
                            new InformationElement[][]{{new IeQualifierOfInterrogation(20)}})}));

                    System.out.println("Server: answer Interrogation command - End");

                } else if (serverCounter == 6) {

                    assertEquals(ASduType.M_SP_NA_1, aSdu.getTypeIdentification());

                    System.out.println("Server: send spontaneous packets");

                    System.out.println("Server: 1");

                    serverConnection.send(new ASdu(ASduType.M_SP_NA_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{
                                    new InformationObject(1,
                                            new InformationElement[][]{
                                                    {new IeSinglePointWithQuality(true, true, true, true, true)}}),
                                    new InformationObject(2, new InformationElement[][]{
                                            {new IeSinglePointWithQuality(false, false, false, false, false)}})}));

                    System.out.println("Server: 2");
                    serverConnection
                            .send(new ASdu(ASduType.M_SP_NA_1, true, CauseOfTransmission.SPONTANEOUS, false, false, 0,
                                    aSdu.getCommonAddress(),
                                    new InformationObject[]{new InformationObject(1, new InformationElement[][]{
                                            {new IeSinglePointWithQuality(true, true, true, true, true)},
                                            {new IeSinglePointWithQuality(false, false, false, false, false)}})}));

                    System.out.println("Server: 3");
                    serverConnection.send(new ASdu(ASduType.M_SP_TA_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{
                                    new InformationObject(1,
                                            new InformationElement[][]{
                                                    {new IeSinglePointWithQuality(true, true, true, true, true),
                                                            new IeTime24(50000)}}),
                                    new InformationObject(2,
                                            new InformationElement[][]{
                                                    {new IeSinglePointWithQuality(false, false, false, false, false),
                                                            new IeTime24(60000)}})}));

                    System.out.println("Server: 4");
                    serverConnection.send(new ASdu(ASduType.M_DP_NA_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{new InformationObject(1,
                                    new InformationElement[][]{
                                            {new IeDoublePointWithQuality(DoublePointInformation.OFF, true, true, true,
                                                    true)}})}));

                    System.out.println("Server: 5");
                    serverConnection.send(new ASdu(ASduType.M_ST_NA_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{new InformationObject(1,
                                    new InformationElement[][]{
                                            {new IeValueWithTransientState(-5, true),
                                                    new IeQuality(true, true, true, true, true)},
                                            {new IeValueWithTransientState(-5, false),
                                                    new IeQuality(true, true, true, true, true)},
                                            {new IeValueWithTransientState(-64, true),
                                                    new IeQuality(true, true, true, true, true)},
                                            {new IeValueWithTransientState(10, false),
                                                    new IeQuality(true, true, true, true, true)}})}));

                    System.out.println("Server: 6");
                    serverConnection.send(new ASdu(ASduType.M_BO_NA_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{new InformationObject(1,
                                    new InformationElement[][]{
                                            {new IeBinaryStateInformation(0xff),
                                                    new IeQuality(true, true, true, true, true)},
                                            {new IeBinaryStateInformation(0xffffffff),
                                                    new IeQuality(true, true, true, true, true)}})}));

                    System.out.println("Server: 7");
                    serverConnection.send(new ASdu(ASduType.M_ME_NA_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{new InformationObject(1, new InformationElement[][]{
                                    {new IeNormalizedValue(-32768), new IeQuality(true, true, true, true, true)},
                                    {new IeNormalizedValue(0), new IeQuality(true, true, true, true, true)}})}));

                    System.out.println("Server: 8");
                    serverConnection.send(new ASdu(ASduType.M_ME_NB_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{new InformationObject(1, new InformationElement[][]{
                                    {new IeScaledValue(-32768), new IeQuality(true, true, true, true, true)},
                                    {new IeScaledValue(10), new IeQuality(true, true, true, true, true)},
                                    {new IeScaledValue(-5), new IeQuality(true, true, true, true, true)}})}));

                    System.out.println("Server: 9");
                    serverConnection.send(new ASdu(ASduType.M_ME_NC_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{new InformationObject(1, new InformationElement[][]{
                                    {new IeShortFloat(-32768.2f), new IeQuality(true, true, true, true, true)},
                                    {new IeShortFloat(10.5f), new IeQuality(true, true, true, true, true)}})}));

                    System.out.println("Server: 10");
                    serverConnection.send(new ASdu(ASduType.M_IT_NA_1, true, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{new InformationObject(1,
                                    new InformationElement[][]{{new IeBinaryCounterReading(-300, 5, Flag.CARRY,
                                            Flag.COUNTER_ADJUSTED, Flag.INVALID)},
                                            {new IeBinaryCounterReading(-300, 4)}})}));

                    System.out.println("Server: 11");
                    serverConnection.send(new ASdu(ASduType.M_EP_TA_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{
                                    new InformationObject(1,
                                            new InformationElement[][]{{
                                                    new IeSingleProtectionEvent(IeSingleProtectionEvent.EventState.OFF,
                                                            true, true, true, true, true),
                                                    new IeTime16(300), new IeTime24(400)}}),
                                    new InformationObject(2,
                                            new InformationElement[][]{{
                                                    new IeSingleProtectionEvent(IeSingleProtectionEvent.EventState.ON,
                                                            false, false, false, false, false),
                                                    new IeTime16(300), new IeTime24(400)}})}));

                    System.out.println("Server: 12");
                    serverConnection.send(new ASdu(ASduType.M_EP_TB_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{new InformationObject(1,
                                    new InformationElement[][]{
                                            {new IeProtectionStartEvent(true, true, true, true, true, true),
                                                    new IeProtectionQuality(true, true, true, true, true),
                                                    new IeTime16(300), new IeTime24(400)}})}));

                    System.out.println("Server: 13");
                    serverConnection.send(new ASdu(ASduType.M_PS_NA_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(),
                            new InformationObject[]{new InformationObject(1,
                                    new InformationElement[][]{{new IeStatusAndStatusChanges(0xff0000ff),
                                            new IeQuality(true, true, true, true, true)}})}));

                    Thread.sleep(200);

                    System.out.println("Server: 14");
                    serverConnection.send(new ASdu(ASduType.M_ME_ND_1, false, CauseOfTransmission.SPONTANEOUS, false,
                            false, 0, aSdu.getCommonAddress(), new InformationObject[]{new InformationObject(1,
                            new InformationElement[][]{{new IeNormalizedValue(3)}})}));

                    System.out.println("Server: 15");
                    serverConnection
                            .send(new ASdu(ASduType.M_SP_TB_1, false, CauseOfTransmission.SPONTANEOUS, false, false, 0,
                                    aSdu.getCommonAddress(),
                                    new InformationObject[]{
                                            new InformationObject(1,
                                                    new InformationElement[][]{{
                                                            new IeSinglePointWithQuality(true, true, true, true, true),
                                                            new IeTime56(serverTimestamp)}})}));

                    System.out.println("Server: 16");
                    serverConnection
                            .send(new ASdu(ASduType.M_IT_TB_1, false, CauseOfTransmission.SPONTANEOUS, false, false, 0,
                                    aSdu.getCommonAddress(),
                                    new InformationObject[]{new InformationObject(1,
                                            new InformationElement[][]{{
                                                    new IeBinaryCounterReading(-300, 5, Flag.CARRY, Flag.INVALID,
                                                            Flag.COUNTER_ADJUSTED),
                                                    new IeTime56(serverTimestamp)}})}));
                    System.out.println("Server: 17");
                    serverConnection.send(new ASdu(ASduType.PRIVATE_136, false, 1, CauseOfTransmission.SPONTANEOUS,
                            false, false, 0, aSdu.getCommonAddress(), new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public void connectionClosed(IOException e) {
        }

    }

    private class ServerlisnerImpl implements ServerEventListener {

        @Override
        public void connectionIndication(Connection connection) {
            try {
                serverConnection = connection;
                connection.waitForStartDT(new ServerReceiver(), 5000);
            } catch (IOException e) {
                fail("Received unexpected exception");
            }

        }

        @Override
        public void serverStoppedListeningIndication(IOException e) {
        }

        @Override
        public void connectionAttemptFailed(IOException e) {
        }

    }

    private class ConnectionListenerImpl implements ConnectionEventListener {

        @Override
        public void newASdu(ASdu aSdu) {

            if (aSdu.getCauseOfTransmission() == CauseOfTransmission.ACTIVATION_CON) {
                return;
            }

            try {

                counter++;

                if (counter == 1) {
                    IeSinglePointWithQuality pointWithQuality = (IeSinglePointWithQuality) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];

                    assertTrue(pointWithQuality.isOn());
                    assertTrue(pointWithQuality.isBlocked());
                    assertTrue(pointWithQuality.isInvalid());
                    assertTrue(pointWithQuality.isNotTopical());
                    assertTrue(pointWithQuality.isSubstituted());

                    IeSinglePointWithQuality io2 = (IeSinglePointWithQuality) aSdu.getInformationObjects()[1]
                            .getInformationElements()[0][0];

                    assertEquals(false, io2.isOn());
                    assertEquals(false, io2.isBlocked());
                    assertEquals(false, io2.isInvalid());
                    assertEquals(false, io2.isNotTopical());
                    assertEquals(false, io2.isSubstituted());

                    counter2++;
                } else if (counter == 2) {
                    IeSinglePointWithQuality pointWithQuality = (IeSinglePointWithQuality) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];

                    assertTrue(pointWithQuality.isOn());
                    assertTrue(pointWithQuality.isBlocked());
                    assertTrue(pointWithQuality.isInvalid());
                    assertTrue(pointWithQuality.isNotTopical());
                    assertTrue(pointWithQuality.isSubstituted());

                    IeSinglePointWithQuality io2 = (IeSinglePointWithQuality) aSdu.getInformationObjects()[0]
                            .getInformationElements()[1][0];

                    assertEquals(false, io2.isOn());
                    assertEquals(false, io2.isBlocked());
                    assertEquals(false, io2.isInvalid());
                    assertEquals(false, io2.isNotTopical());
                    assertEquals(false, io2.isSubstituted());

                    counter2++;
                } else if (counter == 3) {
                    IeSinglePointWithQuality pointWithQuality = (IeSinglePointWithQuality) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];
                    IeTime24 time24 = (IeTime24) aSdu.getInformationObjects()[0].getInformationElements()[0][1];

                    assertTrue(pointWithQuality.isOn());
                    assertTrue(pointWithQuality.isBlocked());
                    assertTrue(pointWithQuality.isInvalid());
                    assertTrue(pointWithQuality.isNotTopical());
                    assertTrue(pointWithQuality.isSubstituted());

                    assertEquals(50000, time24.getTimeInMs());

                    counter2++;
                } else if (counter == 4) {
                    IeDoublePointWithQuality doublePointWithQuality = (IeDoublePointWithQuality) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];

                    assertEquals(true,
                            doublePointWithQuality.getDoublePointInformation() == DoublePointInformation.OFF);
                    assertTrue(doublePointWithQuality.isBlocked());
                    assertTrue(doublePointWithQuality.isInvalid());
                    assertTrue(doublePointWithQuality.isNotTopical());
                    assertTrue(doublePointWithQuality.isSubstituted());

                    counter2++;

                } else if (counter == 5) {

                    IeValueWithTransientState valueWithTransientState = (IeValueWithTransientState) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];

                    assertEquals(-5, valueWithTransientState.getValue());
                    assertTrue(valueWithTransientState.getTransientState());

                    IeValueWithTransientState valueWithTransientState2 = (IeValueWithTransientState) aSdu
                            .getInformationObjects()[0].getInformationElements()[1][0];

                    assertEquals(-5, valueWithTransientState2.getValue());
                    assertEquals(false, valueWithTransientState2.getTransientState());

                    IeValueWithTransientState valueWithTransientState3 = (IeValueWithTransientState) aSdu
                            .getInformationObjects()[0].getInformationElements()[2][0];

                    assertEquals(-64, valueWithTransientState3.getValue());
                    assertTrue(valueWithTransientState3.getTransientState());

                    IeValueWithTransientState valueWithTransientState4 = (IeValueWithTransientState) aSdu
                            .getInformationObjects()[0].getInformationElements()[3][0];

                    assertEquals(10, valueWithTransientState4.getValue());
                    assertEquals(false, valueWithTransientState4.getTransientState());

                    counter2++;
                } else if (counter == 6) {

                    IeBinaryStateInformation binaryStateInformation = (IeBinaryStateInformation) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];

                    assertEquals(0xff, binaryStateInformation.getValue());
                    assertTrue(binaryStateInformation.getBinaryState(8));
                    assertEquals(false, binaryStateInformation.getBinaryState(9));

                    IeBinaryStateInformation binaryStateInformation2 = (IeBinaryStateInformation) aSdu
                            .getInformationObjects()[0].getInformationElements()[1][0];

                    assertEquals(0xffffffff, binaryStateInformation2.getValue());
                    counter2++;
                } else if (counter == 7) {

                    IeNormalizedValue normalizedValue = (IeNormalizedValue) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][0];

                    assertEquals(-32768, normalizedValue.getUnnormalizedValue());

                    IeNormalizedValue normalizedValue2 = (IeNormalizedValue) aSdu.getInformationObjects()[0]
                            .getInformationElements()[1][0];

                    assertEquals(0, normalizedValue2.getUnnormalizedValue());
                    counter2++;
                } else if (counter == 8) {

                    IeScaledValue scaledValue = (IeScaledValue) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][0];

                    assertEquals(-32768, scaledValue.getUnnormalizedValue());

                    IeScaledValue scaledValue2 = (IeScaledValue) aSdu.getInformationObjects()[0]
                            .getInformationElements()[1][0];

                    assertEquals(10, scaledValue2.getUnnormalizedValue());
                    counter2++;
                } else if (counter == 9) {

                    IeShortFloat scaledValue = (IeShortFloat) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][0];

                    assertEquals(-32768.2, scaledValue.getValue(), 0.1);

                    IeShortFloat scaledValue2 = (IeShortFloat) aSdu.getInformationObjects()[0]
                            .getInformationElements()[1][0];

                    assertEquals(10.5, scaledValue2.getValue(), 0.1);
                    counter2++;
                } else if (counter == 10) {

                    IeBinaryCounterReading binaryCounterReading = (IeBinaryCounterReading) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];

                    assertEquals(-300, binaryCounterReading.getCounterReading());
                    assertEquals(5, binaryCounterReading.getSequenceNumber());
                    assertEquals(3, binaryCounterReading.getFlags().size());

                    IeBinaryCounterReading binaryCounterReading2 = (IeBinaryCounterReading) aSdu
                            .getInformationObjects()[0].getInformationElements()[1][0];

                    assertEquals(-300, binaryCounterReading2.getCounterReading());
                    assertEquals(4, binaryCounterReading2.getSequenceNumber());

                    assertTrue(binaryCounterReading2.getFlags().isEmpty());
                    counter2++;
                } else if (counter == 11) {

                    IeSingleProtectionEvent singleProtectionEvent = (IeSingleProtectionEvent) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];

                    assertEquals(EventState.OFF, singleProtectionEvent.getEventState());
                    assertTrue(singleProtectionEvent.isBlocked());
                    assertTrue(singleProtectionEvent.isElapsedTimeInvalid());
                    assertTrue(singleProtectionEvent.isEventInvalid());
                    assertTrue(singleProtectionEvent.isNotTopical());
                    assertTrue(singleProtectionEvent.isSubstituted());

                    IeTime16 time16 = (IeTime16) aSdu.getInformationObjects()[0].getInformationElements()[0][1];
                    assertEquals(300, time16.getTimeInMs());

                    IeTime24 time24 = (IeTime24) aSdu.getInformationObjects()[0].getInformationElements()[0][2];
                    assertEquals(400, time24.getTimeInMs());

                    IeSingleProtectionEvent singleProtectionEvent2 = (IeSingleProtectionEvent) aSdu
                            .getInformationObjects()[1].getInformationElements()[0][0];

                    assertEquals(EventState.ON, singleProtectionEvent2.getEventState());
                    assertEquals(false, singleProtectionEvent2.isBlocked());
                    assertEquals(false, singleProtectionEvent2.isElapsedTimeInvalid());
                    assertEquals(false, singleProtectionEvent2.isEventInvalid());
                    assertEquals(false, singleProtectionEvent2.isNotTopical());
                    assertEquals(false, singleProtectionEvent2.isSubstituted());
                    counter2++;
                } else if (counter == 12) {

                    IeProtectionStartEvent singleProtectionEvent = (IeProtectionStartEvent) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];

                    assertTrue(singleProtectionEvent.isGeneralStart());
                    assertTrue(singleProtectionEvent.isStartOperationIe());
                    assertTrue(singleProtectionEvent.isStartOperationL1());
                    assertTrue(singleProtectionEvent.isStartOperationL2());
                    assertTrue(singleProtectionEvent.isStartOperationL3());
                    assertTrue(singleProtectionEvent.isStartReverseOperation());

                    IeProtectionQuality protectionQuality = (IeProtectionQuality) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][1];

                    assertTrue(protectionQuality.isBlocked());
                    assertTrue(protectionQuality.isElapsedTimeInvalid());
                    assertTrue(protectionQuality.isInvalid());
                    assertTrue(protectionQuality.isNotTopical());
                    assertTrue(protectionQuality.isSubstituted());

                    IeTime16 time16 = (IeTime16) aSdu.getInformationObjects()[0].getInformationElements()[0][2];
                    assertEquals(300, time16.getTimeInMs());

                    counter2++;
                } else if (counter == 13) {

                    IeStatusAndStatusChanges statusAndStatusChanges = (IeStatusAndStatusChanges) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];

                    assertEquals(0xff0000ff, statusAndStatusChanges.getValue());
                    assertEquals(false, statusAndStatusChanges.getStatus(8));
                    assertTrue(statusAndStatusChanges.getStatus(9));
                    assertTrue(statusAndStatusChanges.hasStatusChanged(8));
                    assertEquals(false, statusAndStatusChanges.hasStatusChanged(9));

                    counter2++;
                } else if (counter == 14) {

                    IeNormalizedValue normalizedValue = (IeNormalizedValue) aSdu.getInformationObjects()[0]
                            .getInformationElements()[0][0];

                    assertEquals(3, normalizedValue.getUnnormalizedValue());

                    counter2++;
                } else if (counter == 15) {

                    IeTime56 time56 = (IeTime56) aSdu.getInformationObjects()[0].getInformationElements()[0][1];

                    assertEquals(serverTimestamp, time56.getTimestamp());

                    counter2++;
                } else if (counter == 16) {

                    IeBinaryCounterReading binaryCounterReading = (IeBinaryCounterReading) aSdu
                            .getInformationObjects()[0].getInformationElements()[0][0];

                    assertEquals(-300, binaryCounterReading.getCounterReading());
                    assertEquals(5, binaryCounterReading.getSequenceNumber());
                    assertEquals(3, binaryCounterReading.getFlags().size());

                    counter2++;
                } else if (counter == 17) {

                    Assert.assertNull(aSdu.getInformationObjects());
                    Assert.assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, aSdu.getPrivateInformation());

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
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }

    }

}
