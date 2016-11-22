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
package org.openmuc.j60870;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openmuc.j60870.APdu.APCI_TYPE;
import org.openmuc.j60870.internal.ConnectionSettings;

/**
 * Represents a connection between a client and a server. It is created either through an instance of
 * {@link ClientConnectionBuilder} or passed to {@link ServerEventListener}. Once it has been closed it cannot be opened
 * again. A newly created connection has successfully build up a TCP/IP connection to the server. Before receiving ASDUs
 * or sending commands one has to call {@link Connection#startDataTransfer(ConnectionEventListener, int)} or
 * {@link Connection#waitForStartDT(ConnectionEventListener, int)}. Afterwards incoming ASDUs are forwarded to the
 * {@link ConnectionEventListener}. Incoming ASDUs are queued so that {@link ConnectionEventListener#newASdu(ASdu)} is
 * never called simultaneously for the same connection.
 *
 * Connection offers a method for every possible command defined by IEC 60870 (e.g. singleCommand). Every command
 * function may throw an IOException indicating a fatal connection error. In this case the connection will be
 * automatically closed and a new connection will have to be built up. The command methods do not wait for an
 * acknowledgment but return right after the command has been sent.
 *
 * @author Stefan Feuerhahn
 *
 */
public class Connection {

    private final Socket socket;
    private final ServerThread serverThread;
    private final DataOutputStream os;
    private final DataInputStream is;

    private boolean closed = false;

    private final ConnectionSettings settings;
    private ConnectionEventListener aSduListener = null;

    private int sendSequenceNumber = 0;
    private int receiveSequenceNumber = 0;
    private int acknowledgedReceiveSequenceNumber = 0;
    private int acknowledgedSendSequenceNumber = 0;

    private int originatorAddress = 0;

    private final byte[] buffer = new byte[255];

    private static final byte[] TESTFR_CON_BUFFER = new byte[] { 0x68, 0x04, (byte) 0x83, 0x00, 0x00, 0x00 };
    private static final byte[] TESTFR_ACT_BUFFER = new byte[] { 0x68, 0x04, (byte) 0x43, 0x00, 0x00, 0x00 };
    private static final byte[] STARTDT_ACT_BUFFER = new byte[] { 0x68, 0x04, 0x07, 0x00, 0x00, 0x00 };
    private static final byte[] STARTDT_CON_BUFFER = new byte[] { 0x68, 0x04, 0x0b, 0x00, 0x00, 0x00 };

    private final ScheduledExecutorService maxTimeNoAckSentTimer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> maxTimeNoAckSentFuture = null;

    private final ScheduledExecutorService maxTimeNoAckReceivedTimer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> maxTimeNoAckReceivedFuture = null;

    private final ScheduledExecutorService maxIdleTimeTimer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> maxIdleTimeTimerFuture = null;

    private final ScheduledExecutorService maxTimeNoTestConReceivedTimer = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> maxTimeNoTestConReceivedFuture = null;

    private IOException closedIOException = null;

    private CountDownLatch startdtactSignal;
    private CountDownLatch startdtConSignal;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private class ConnectionReader extends Thread {

        @Override
        public void run() {

            try {
                while (true) {

                    socket.setSoTimeout(0);

                    if (is.readByte() != 0x68) {
                        throw new IOException("Message does not start with 0x68");
                    }

                    socket.setSoTimeout(settings.messageFragmentTimeout);

                    final APdu aPdu = new APdu(is, settings);

                    synchronized (Connection.this) {

                        switch (aPdu.getApciType()) {
                        case I_FORMAT:

                            if (receiveSequenceNumber != aPdu.getSendSeqNumber()) {
                                throw new IOException("Got unexpected send sequence number: " + aPdu.getSendSeqNumber()
                                        + ", expected: " + receiveSequenceNumber);
                            }

                            receiveSequenceNumber = (aPdu.getSendSeqNumber() + 1) % 32768;

                            handleReceiveSequenceNumber(aPdu);

                            if (aSduListener != null) {
                                executor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        aSduListener.newASdu(aPdu.getASdu());
                                    }
                                });
                            }

                            int numUnconfirmedIPdusReceived = getSequenceNumberDifference(receiveSequenceNumber,
                                    acknowledgedReceiveSequenceNumber);

                            if (numUnconfirmedIPdusReceived > settings.maxUnconfirmedIPdusReceived) {
                                sendSFormatPdu();
                                if (maxTimeNoAckSentFuture != null) {
                                    maxTimeNoAckSentFuture.cancel(true);
                                    maxTimeNoAckSentFuture = null;
                                }
                            }
                            else {

                                if (maxTimeNoAckSentFuture == null) {

                                    maxTimeNoAckSentFuture = maxTimeNoAckSentTimer.schedule(new Runnable() {
                                        @Override
                                        public void run() {

                                            synchronized (Connection.this) {
                                                if (Thread.interrupted()) {
                                                    return;
                                                }
                                                try {
                                                    sendSFormatPdu();
                                                } catch (IOException e) {
                                                }
                                                maxTimeNoAckSentFuture = null;
                                            }
                                        }
                                    }, settings.maxTimeNoAckSent, TimeUnit.MILLISECONDS);
                                }
                            }
                            resetMaxIdleTimeTimer();

                            break;
                        case STARTDT_CON:
                            if (startdtConSignal != null) {
                                startdtConSignal.countDown();
                            }
                            resetMaxIdleTimeTimer();
                            break;
                        case STARTDT_ACT:
                            if (startdtactSignal != null) {
                                startdtactSignal.countDown();
                            }
                            break;
                        case S_FORMAT:
                            handleReceiveSequenceNumber(aPdu);
                            resetMaxIdleTimeTimer();
                            break;
                        case TESTFR_ACT:
                            os.write(TESTFR_CON_BUFFER, 0, TESTFR_CON_BUFFER.length);
                            os.flush();
                            resetMaxIdleTimeTimer();
                            break;
                        case TESTFR_CON:
                            if (maxTimeNoTestConReceivedFuture != null) {
                                maxTimeNoTestConReceivedFuture.cancel(true);
                                maxTimeNoTestConReceivedFuture = null;
                            }
                            resetMaxIdleTimeTimer();
                            break;
                        default:
                            throw new IOException("Got unexpected message with APCI Type: " + aPdu.getApciType());
                        }

                    }

                }
            } catch (EOFException e2) {
                closedIOException = new EOFException("Socket was closed by remote host.");
            } catch (IOException e2) {
                closedIOException = e2;
            } catch (Exception e2) {
                closedIOException = new IOException("Unexpected Exception", e2);
            } finally {
                if (closed == false) {
                    close();
                    if (aSduListener != null) {
                        aSduListener.connectionClosed(closedIOException);
                    }
                }
                maxTimeNoAckSentTimer.shutdownNow();
                maxTimeNoAckReceivedTimer.shutdownNow();
                maxIdleTimeTimer.shutdownNow();
                maxTimeNoTestConReceivedTimer.shutdownNow();
            }
        }

        private void handleReceiveSequenceNumber(final APdu aPdu) throws IOException {
            if (acknowledgedSendSequenceNumber != aPdu.getReceiveSeqNumber()) {

                if (getSequenceNumberDifference(aPdu.getReceiveSeqNumber(),
                        acknowledgedSendSequenceNumber) > getNumUnconfirmedIPdusSent()) {
                    throw new IOException("Got unexpected receive sequence number: " + aPdu.getReceiveSeqNumber()
                            + ", expected a number between: " + acknowledgedSendSequenceNumber + " and "
                            + sendSequenceNumber);
                }

                if (maxTimeNoAckReceivedFuture != null) {
                    maxTimeNoAckReceivedFuture.cancel(true);
                    maxTimeNoAckReceivedFuture = null;
                }

                acknowledgedSendSequenceNumber = aPdu.getReceiveSeqNumber();

                if (sendSequenceNumber != acknowledgedSendSequenceNumber) {
                    scheduleMaxTimeNoAckReceivedFuture();
                }

            }
        }
    }

    Connection(Socket socket, ServerThread serverThread, ConnectionSettings settings) throws IOException {

        try {
            os = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        try {
            is = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        } catch (IOException e) {
            try {
                // this will also close the socket
                os.close();
            } catch (Exception e1) {
            }
            throw e;
        }

        this.socket = socket;
        this.settings = settings;
        this.serverThread = serverThread;
        if (serverThread != null) {
            startdtactSignal = new CountDownLatch(1);
        }

        ConnectionReader connectionReader = new ConnectionReader();
        connectionReader.start();

    }

    /**
     * Starts a connection. Sends a STARTDT act and waits for a STARTDT con. If successful a new thread will be started
     * that listens for incoming ASDUs and notifies the given ASduListener.
     *
     * @param listener
     *            the listener that is notified of incoming ASDUs
     * @param timeout
     *            the maximum time in ms to wait for a STARDT CON message after sending the STARTDT ACT message. If set
     *            to zero, timeout is disabled.
     * @throws IOException
     *             if any kind of IOException occurs
     * @throws TimeoutException
     *             if the configured response timeout runs out
     */
    public void startDataTransfer(ConnectionEventListener listener, int timeout) throws IOException, TimeoutException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout may not be negative");
        }

        startdtConSignal = new CountDownLatch(1);

        synchronized (this) {
            os.write(STARTDT_ACT_BUFFER, 0, STARTDT_ACT_BUFFER.length);
        }
        os.flush();

        if (timeout == 0) {
            try {
                startdtConSignal.await();
            } catch (InterruptedException e) {
            }
        }
        else {
            boolean success = true;
            try {
                success = startdtConSignal.await(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
            if (!success) {
                throw new TimeoutException();
            }
        }

        this.aSduListener = listener;
    }

    /**
     * Waits for incoming STARTDT ACT message and response with a STARTDT CON message. Throws a TimeoutException if no
     * STARTDT message is received within the specified timeout span.
     *
     * @param listener
     *            the listener that is to be notified of incoming ASDUs and disconnect events
     * @param timeout
     *            the maximum time in ms to wait for STARTDT ACT message before throwing a TimeoutException. If set to
     *            zero, timeout is disabled.
     * @throws IOException
     *             if a fatal communication error occurred
     * @throws TimeoutException
     *             if the given timeout runs out before the STARTDT ACT message is received.
     */
    public void waitForStartDT(ConnectionEventListener listener, int timeout) throws IOException, TimeoutException {

        if (timeout < 0) {
            throw new IllegalArgumentException("timeout may not be negative");
        }

        if (timeout == 0) {
            try {
                startdtactSignal.await();
            } catch (InterruptedException e) {
            }
        }
        else {
            boolean success = true;
            try {
                success = startdtactSignal.await(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
            if (!success) {
                throw new TimeoutException();
            }
        }

        synchronized (this) {
            os.write(STARTDT_CON_BUFFER, 0, STARTDT_CON_BUFFER.length);
        }
        os.flush();

        this.aSduListener = listener;

        resetMaxIdleTimeTimer();
    }

    private void sendSFormatPdu() throws IOException {
        APdu requestAPdu = new APdu(0, receiveSequenceNumber, APCI_TYPE.S_FORMAT, null);
        requestAPdu.encode(buffer, settings);

        os.write(buffer, 0, 6);
        os.flush();

        acknowledgedReceiveSequenceNumber = receiveSequenceNumber;

        resetMaxIdleTimeTimer();
    }

    /**
     * Set the Originator Address. It is the address of controlling station (client) so that responses can be routed
     * back to it. Originator addresses from 1 to 255 are used to address a particular controlling station. Address 0 is
     * the default and is used if responses are to be routed to all controlling stations in the system. Note that the
     * same Originator Address is sent in a command and its confirmation.
     *
     * @param originatorAddress
     *            the Originator Address. Valid values are 0...255.
     */
    public void setOriginatorAddress(int originatorAddress) {
        if (originatorAddress < 0 || originatorAddress > 255) {
            throw new IllegalArgumentException("Originator Address must be between 0 and 255.");
        }
        this.originatorAddress = originatorAddress;
    }

    /**
     * Get the configured Originator Address.
     *
     * @return the Originator Address
     */
    public int getOriginatorAddress() {
        return originatorAddress;
    }

    public int getNumUnconfirmedIPdusSent() {
        synchronized (this) {
            return getSequenceNumberDifference(sendSequenceNumber, acknowledgedSendSequenceNumber);
        }
    }

    /**
     * Will close the TCP connection to the server if its still open and free any resources of this connection.
     */
    public void close() {
        if (!closed) {
            closed = true;
            try {
                // will also close socket
                os.close();
            } catch (Exception e) {
            }
            try {
                is.close();
            } catch (Exception e) {
            }
            if (serverThread != null) {
                serverThread.connectionClosedSignal();
            }
        }
    }

    public void send(ASdu aSdu) throws IOException {

        synchronized (this) {

            acknowledgedReceiveSequenceNumber = receiveSequenceNumber;
            APdu requestAPdu = new APdu(sendSequenceNumber, receiveSequenceNumber, APCI_TYPE.I_FORMAT, aSdu);
            sendSequenceNumber = (sendSequenceNumber + 1) % 32768;

            if (maxTimeNoAckSentFuture != null) {
                maxTimeNoAckSentFuture.cancel(true);
                maxTimeNoAckSentFuture = null;
            }

            if (maxTimeNoAckReceivedFuture == null) {
                scheduleMaxTimeNoAckReceivedFuture();
            }

            int length = requestAPdu.encode(buffer, settings);
            os.write(buffer, 0, length);
            os.flush();
            resetMaxIdleTimeTimer();
        }
    }

    private void scheduleMaxTimeNoAckReceivedFuture() {
        maxTimeNoAckReceivedFuture = maxTimeNoAckReceivedTimer.schedule(new Runnable() {
            @Override
            public void run() {

                synchronized (Connection.this) {
                    if (Thread.interrupted()) {
                        return;
                    }
                    close();
                    maxTimeNoAckReceivedFuture = null;
                    if (aSduListener != null) {
                        aSduListener.connectionClosed(new IOException(
                                "The maximum time that no confirmation was received (t1) has been exceeded. t1 = "
                                        + settings.maxTimeNoAckReceived + "ms"));
                    }
                }
            }
        }, settings.maxTimeNoAckReceived, TimeUnit.MILLISECONDS);
    }

    private void scheduleMaxTimeNoTestConReceivedFuture() {
        maxTimeNoTestConReceivedFuture = maxTimeNoTestConReceivedTimer.schedule(new Runnable() {
            @Override
            public void run() {

                synchronized (Connection.this) {
                    if (Thread.interrupted()) {
                        return;
                    }
                    close();
                    maxTimeNoTestConReceivedFuture = null;
                    if (aSduListener != null) {
                        aSduListener.connectionClosed(new IOException(
                                "The maximum time that no test frame confirmation was received (t1) has been exceeded. t1 = "
                                        + settings.maxTimeNoAckReceived + "ms"));
                    }
                }
            }
        }, settings.maxTimeNoAckReceived, TimeUnit.MILLISECONDS);
    }

    private int getSequenceNumberDifference(int x, int y) {
        int difference = x - y;
        if (difference < 0) {
            difference += 32768;
        }
        return difference;
    }

    private void resetMaxIdleTimeTimer() {
        if (maxIdleTimeTimerFuture != null) {
            maxIdleTimeTimerFuture.cancel(true);
        }
        maxIdleTimeTimerFuture = maxIdleTimeTimer.schedule(new Runnable() {
            @Override
            public void run() {

                synchronized (Connection.this) {
                    if (Thread.interrupted()) {
                        return;
                    }
                    try {
                        os.write(TESTFR_ACT_BUFFER, 0, TESTFR_ACT_BUFFER.length);
                        os.flush();
                    } catch (IOException e) {
                    }
                    maxIdleTimeTimerFuture = null;
                    scheduleMaxTimeNoTestConReceivedFuture();
                }
            }
        }, settings.maxIdleTime, TimeUnit.MILLISECONDS);
    }

    public void sendConfirmation(ASdu aSdu) throws IOException {
        CauseOfTransmission cot = aSdu.getCauseOfTransmission();
        if (cot == CauseOfTransmission.ACTIVATION) {
            cot = CauseOfTransmission.ACTIVATION_CON;
        }
        else if (cot == CauseOfTransmission.DEACTIVATION) {
            cot = CauseOfTransmission.DEACTIVATION_CON;
        }
        send(new ASdu(aSdu.getTypeIdentification(), aSdu.isSequenceOfElements(), cot, aSdu.isTestFrame(),
                aSdu.isNegativeConfirm(), aSdu.getOriginatorAddress(), aSdu.getCommonAddress(),
                aSdu.getInformationObjects()));
    }

    /**
     * Sends a single command (C_SC_NA_1, TI: 45).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress
     *            the information object address.
     * @param singleCommand
     *            the command to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void singleCommand(int commonAddress, int informationObjectAddress, IeSingleCommand singleCommand)
            throws IOException {
        CauseOfTransmission cot;
        if (singleCommand.isCommandStateOn()) {
            cot = CauseOfTransmission.ACTIVATION;
        }
        else {
            cot = CauseOfTransmission.DEACTIVATION;
        }
        ASdu aSdu = new ASdu(TypeId.C_SC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { singleCommand } }) });
        send(aSdu);
    }

    /**
     * Sends a single command with time tag CP56Time2a (C_SC_TA_1, TI: 58).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress
     *            the information object address.
     * @param singleCommand
     *            the command to be sent.
     * @param timeTag
     *            the time tag to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void singleCommandWithTimeTag(int commonAddress, int informationObjectAddress, IeSingleCommand singleCommand,
            IeTime56 timeTag) throws IOException {
        CauseOfTransmission cot;
        if (singleCommand.isCommandStateOn()) {
            cot = CauseOfTransmission.ACTIVATION;
        }
        else {
            cot = CauseOfTransmission.DEACTIVATION;
        }
        ASdu aSdu = new ASdu(TypeId.C_SC_TA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { singleCommand, timeTag } }) });
        send(aSdu);
    }

    /**
     * Sends a double command (C_DC_NA_1, TI: 46).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param doubleCommand
     *            the command to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void doubleCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
            IeDoubleCommand doubleCommand) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_DC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { doubleCommand } }) });
        send(aSdu);
    }

    /**
     * Sends a double command with time tag CP56Time2a (C_DC_TA_1, TI: 59).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param doubleCommand
     *            the command to be sent.
     * @param timeTag
     *            the time tag to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void doubleCommandWithTimeTag(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
            IeDoubleCommand doubleCommand, IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_DC_TA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { doubleCommand, timeTag } }) });
        send(aSdu);
    }

    /**
     * Sends a regulating step command (C_RC_NA_1, TI: 47).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param regulatingStepCommand
     *            the command to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void regulatingStepCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
            IeRegulatingStepCommand regulatingStepCommand) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_RC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { regulatingStepCommand } }) });
        send(aSdu);
    }

    /**
     * Sends a regulating step command with time tag CP56Time2a (C_RC_TA_1, TI: 60).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param regulatingStepCommand
     *            the command to be sent.
     * @param timeTag
     *            the time tag to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void regulatingStepCommandWithTimeTag(int commonAddress, CauseOfTransmission cot,
            int informationObjectAddress, IeRegulatingStepCommand regulatingStepCommand, IeTime56 timeTag)
            throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_RC_TA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { regulatingStepCommand, timeTag } }) });
        send(aSdu);
    }

    /**
     * Sends a set-point command, normalized value (C_SE_NA_1, TI: 48).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param normalizedValue
     *            the value to be sent.
     * @param qualifier
     *            the qualifier to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void setNormalizedValueCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
            IeNormalizedValue normalizedValue, IeQualifierOfSetPointCommand qualifier) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_SE_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { normalizedValue, qualifier } }) });
        send(aSdu);
    }

    /**
     * Sends a set-point command with time tag CP56Time2a, normalized value (C_SE_TA_1, TI: 61).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param normalizedValue
     *            the value to be sent.
     * @param qualifier
     *            the qualifier to be sent.
     * @param timeTag
     *            the time tag to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void setNormalizedValueCommandWithTimeTag(int commonAddress, CauseOfTransmission cot,
            int informationObjectAddress, IeNormalizedValue normalizedValue, IeQualifierOfSetPointCommand qualifier,
            IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_SE_TA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { normalizedValue, qualifier, timeTag } }) });
        send(aSdu);
    }

    /**
     * Sends a set-point command, scaled value (C_SE_NB_1, TI: 49).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param scaledValue
     *            the value to be sent.
     * @param qualifier
     *            the qualifier to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void setScaledValueCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
            IeScaledValue scaledValue, IeQualifierOfSetPointCommand qualifier) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_SE_NB_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { scaledValue, qualifier } }) });
        send(aSdu);
    }

    /**
     * Sends a set-point command with time tag CP56Time2a, scaled value (C_SE_TB_1, TI: 62).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param scaledValue
     *            the value to be sent.
     * @param qualifier
     *            the qualifier to be sent.
     * @param timeTag
     *            the time tag to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void setScaledValueCommandWithTimeTag(int commonAddress, CauseOfTransmission cot,
            int informationObjectAddress, IeScaledValue scaledValue, IeQualifierOfSetPointCommand qualifier,
            IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_SE_TB_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { scaledValue, qualifier, timeTag } }) });
        send(aSdu);
    }

    /**
     * Sends a set-point command, short floating point number (C_SE_NC_1, TI: 50).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param shortFloat
     *            the value to be sent.
     * @param qualifier
     *            the qualifier to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void setShortFloatCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
            IeShortFloat shortFloat, IeQualifierOfSetPointCommand qualifier) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_SE_NC_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { shortFloat, qualifier } }) });
        send(aSdu);
    }

    /**
     * Sends a set-point command with time tag CP56Time2a, short floating point number (C_SE_TC_1, TI: 63).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param shortFloat
     *            the value to be sent.
     * @param qualifier
     *            the qualifier to be sent.
     * @param timeTag
     *            the time tag to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void setShortFloatCommandWithTimeTag(int commonAddress, CauseOfTransmission cot,
            int informationObjectAddress, IeShortFloat shortFloat, IeQualifierOfSetPointCommand qualifier,
            IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_SE_TC_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { shortFloat, qualifier, timeTag } }) });
        send(aSdu);
    }

    /**
     * Sends a bitstring of 32 bit (C_BO_NA_1, TI: 51).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param binaryStateInformation
     *            the value to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void bitStringCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
            IeBinaryStateInformation binaryStateInformation) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_BO_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { binaryStateInformation } }) });
        send(aSdu);
    }

    /**
     * Sends a bitstring of 32 bit with time tag CP56Time2a (C_BO_TA_1, TI: 64).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param binaryStateInformation
     *            the value to be sent.
     * @param timeTag
     *            the time tag to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void bitStringCommandWithTimeTag(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
            IeBinaryStateInformation binaryStateInformation, IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(TypeId.C_BO_TA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { binaryStateInformation, timeTag } }) });
        send(aSdu);
    }

    /**
     * Sends an interrogation command (C_IC_NA_1, TI: 100).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param qualifier
     *            the qualifier to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void interrogation(int commonAddress, CauseOfTransmission cot, IeQualifierOfInterrogation qualifier)
            throws IOException {
        ASdu aSdu = new ASdu(TypeId.C_IC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(0, new InformationElement[][] { { qualifier } }) });
        send(aSdu);
    }

    /**
     * Sends a counter interrogation command (C_CI_NA_1, TI: 101).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param qualifier
     *            the qualifier to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void counterInterrogation(int commonAddress, CauseOfTransmission cot,
            IeQualifierOfCounterInterrogation qualifier) throws IOException {
        ASdu aSdu = new ASdu(TypeId.C_CI_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(0, new InformationElement[][] { { qualifier } }) });
        send(aSdu);
    }

    /**
     * Sends a read command (C_RD_NA_1, TI: 102).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress
     *            the information object address.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void readCommand(int commonAddress, int informationObjectAddress) throws IOException {
        ASdu aSdu = new ASdu(TypeId.C_RD_NA_1, false, CauseOfTransmission.REQUEST, false, false, originatorAddress,
                commonAddress, new InformationObject[] {
                        new InformationObject(informationObjectAddress, new InformationElement[0][0]) });
        send(aSdu);
    }

    /**
     * Sends a clock synchronization command (C_CS_NA_1, TI: 103).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param time
     *            the time to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void synchronizeClocks(int commonAddress, IeTime56 time) throws IOException {
        InformationObject io = new InformationObject(0, new InformationElement[][] { { time } });

        InformationObject[] ios = new InformationObject[] { io };

        ASdu aSdu = new ASdu(TypeId.C_CS_NA_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, ios);

        send(aSdu);
    }

    /**
     * Sends a test command (C_TS_NA_1, TI: 104).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void testCommand(int commonAddress) throws IOException {
        ASdu aSdu = new ASdu(TypeId.C_TS_NA_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject[] {
                        new InformationObject(0, new InformationElement[][] { { new IeFixedTestBitPattern() } }) });
        send(aSdu);
    }

    /**
     * Sends a reset process command (C_RP_NA_1, TI: 105).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param qualifier
     *            the qualifier to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void resetProcessCommand(int commonAddress, IeQualifierOfResetProcessCommand qualifier) throws IOException {
        ASdu aSdu = new ASdu(TypeId.C_RP_NA_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress,
                new InformationObject[] { new InformationObject(0, new InformationElement[][] { { qualifier } }) });
        send(aSdu);
    }

    /**
     * Sends a delay acquisition command (C_CD_NA_1, TI: 106).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and spontaneous.
     * @param time
     *            the time to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void delayAcquisitionCommand(int commonAddress, CauseOfTransmission cot, IeTime16 time) throws IOException {
        ASdu aSdu = new ASdu(TypeId.C_CD_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(0, new InformationElement[][] { { time } }) });
        send(aSdu);
    }

    /**
     * Sends a test command with time tag CP56Time2a (C_TS_TA_1, TI: 107).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param testSequenceCounter
     *            the value to be sent.
     * @param time
     *            the time to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void testCommandWithTimeTag(int commonAddress, IeTestSequenceCounter testSequenceCounter, IeTime56 time)
            throws IOException {
        ASdu aSdu = new ASdu(TypeId.C_TS_TA_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject[] {
                        new InformationObject(0, new InformationElement[][] { { testSequenceCounter, time } }) });
        send(aSdu);
    }

    /**
     * Sends a parameter of measured values, normalized value (P_ME_NA_1, TI: 110).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress
     *            the information object address.
     * @param normalizedValue
     *            the value to be sent.
     * @param qualifier
     *            the qualifier to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void parameterNormalizedValueCommand(int commonAddress, int informationObjectAddress,
            IeNormalizedValue normalizedValue, IeQualifierOfParameterOfMeasuredValues qualifier) throws IOException {
        ASdu aSdu = new ASdu(TypeId.P_ME_NA_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { normalizedValue, qualifier } }) });
        send(aSdu);
    }

    /**
     * Sends a parameter of measured values, scaled value (P_ME_NB_1, TI: 111).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress
     *            the information object address.
     * @param scaledValue
     *            the value to be sent.
     * @param qualifier
     *            the qualifier to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void parameterScaledValueCommand(int commonAddress, int informationObjectAddress, IeScaledValue scaledValue,
            IeQualifierOfParameterOfMeasuredValues qualifier) throws IOException {
        ASdu aSdu = new ASdu(TypeId.P_ME_NB_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { scaledValue, qualifier } }) });
        send(aSdu);
    }

    /**
     * Sends a parameter of measured values, short floating point number (P_ME_NC_1, TI: 112).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress
     *            the information object address.
     * @param shortFloat
     *            the value to be sent.
     * @param qualifier
     *            the qualifier to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void parameterShortFloatCommand(int commonAddress, int informationObjectAddress, IeShortFloat shortFloat,
            IeQualifierOfParameterOfMeasuredValues qualifier) throws IOException {
        ASdu aSdu = new ASdu(TypeId.P_ME_NC_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { shortFloat, qualifier } }) });
        send(aSdu);
    }

    /**
     * Sends a parameter activation (P_AC_NA_1, TI: 113).
     *
     * @param commonAddress
     *            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot
     *            the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress
     *            the information object address.
     * @param qualifier
     *            the qualifier to be sent.
     * @throws IOException
     *             if a fatal communication error occurred.
     */
    public void parameterActivation(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
            IeQualifierOfParameterActivation qualifier) throws IOException {
        ASdu aSdu = new ASdu(TypeId.P_AC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { qualifier } }) });
        send(aSdu);
    }

    public void fileReady(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
            IeLengthOfFileOrSection lengthOfFile, IeFileReadyQualifier qualifier) throws IOException {
        ASdu aSdu = new ASdu(TypeId.F_FR_NA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { nameOfFile, lengthOfFile, qualifier } }) });
        send(aSdu);
    }

    public void sectionReady(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
            IeNameOfSection nameOfSection, IeLengthOfFileOrSection lengthOfSection, IeSectionReadyQualifier qualifier)
            throws IOException {
        ASdu aSdu = new ASdu(TypeId.F_SR_NA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { nameOfFile, nameOfSection, lengthOfSection, qualifier } }) });
        send(aSdu);
    }

    public void callOrSelectFiles(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
            IeNameOfFile nameOfFile, IeNameOfSection nameOfSection, IeSelectAndCallQualifier qualifier)
            throws IOException {
        ASdu aSdu = new ASdu(TypeId.F_SC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { nameOfFile, nameOfSection, qualifier } }) });
        send(aSdu);
    }

    public void lastSectionOrSegment(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
            IeNameOfSection nameOfSection, IeLastSectionOrSegmentQualifier qualifier, IeChecksum checksum)
            throws IOException {
        ASdu aSdu = new ASdu(TypeId.F_LS_NA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { nameOfFile, nameOfSection, qualifier, checksum } }) });
        send(aSdu);
    }

    public void ackFileOrSection(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
            IeNameOfSection nameOfSection, IeAckFileOrSectionQualifier qualifier) throws IOException {
        ASdu aSdu = new ASdu(TypeId.F_AF_NA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { nameOfFile, nameOfSection, qualifier } }) });
        send(aSdu);
    }

    public void sendSegment(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
            IeNameOfSection nameOfSection, IeFileSegment segment) throws IOException {
        ASdu aSdu = new ASdu(TypeId.F_SG_NA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { nameOfFile, nameOfSection, segment } }) });
        send(aSdu);
    }

    public void sendDirectory(int commonAddress, int informationObjectAddress, InformationElement[][] directory)
            throws IOException {
        ASdu aSdu = new ASdu(TypeId.F_DR_TA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress, directory) });
        send(aSdu);
    }

    public void queryLog(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
            IeTime56 rangeStartTime, IeTime56 rangeEndTime) throws IOException {
        ASdu aSdu = new ASdu(TypeId.F_SC_NB_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject[] { new InformationObject(informationObjectAddress,
                        new InformationElement[][] { { nameOfFile, rangeStartTime, rangeEndTime } }) });
        send(aSdu);
    }

}
