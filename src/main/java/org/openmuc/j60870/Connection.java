/*
 * Copyright 2014-20 Fraunhofer ISE
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

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.openmuc.j60870.APdu.ApciType;
import org.openmuc.j60870.ie.IeAckFileOrSectionQualifier;
import org.openmuc.j60870.ie.IeBinaryStateInformation;
import org.openmuc.j60870.ie.IeChecksum;
import org.openmuc.j60870.ie.IeDoubleCommand;
import org.openmuc.j60870.ie.IeFileReadyQualifier;
import org.openmuc.j60870.ie.IeFileSegment;
import org.openmuc.j60870.ie.IeFixedTestBitPattern;
import org.openmuc.j60870.ie.IeLastSectionOrSegmentQualifier;
import org.openmuc.j60870.ie.IeLengthOfFileOrSection;
import org.openmuc.j60870.ie.IeNameOfFile;
import org.openmuc.j60870.ie.IeNameOfSection;
import org.openmuc.j60870.ie.IeNormalizedValue;
import org.openmuc.j60870.ie.IeQualifierOfCounterInterrogation;
import org.openmuc.j60870.ie.IeQualifierOfInterrogation;
import org.openmuc.j60870.ie.IeQualifierOfParameterActivation;
import org.openmuc.j60870.ie.IeQualifierOfParameterOfMeasuredValues;
import org.openmuc.j60870.ie.IeQualifierOfResetProcessCommand;
import org.openmuc.j60870.ie.IeQualifierOfSetPointCommand;
import org.openmuc.j60870.ie.IeRegulatingStepCommand;
import org.openmuc.j60870.ie.IeScaledValue;
import org.openmuc.j60870.ie.IeSectionReadyQualifier;
import org.openmuc.j60870.ie.IeSelectAndCallQualifier;
import org.openmuc.j60870.ie.IeShortFloat;
import org.openmuc.j60870.ie.IeSingleCommand;
import org.openmuc.j60870.ie.IeTestSequenceCounter;
import org.openmuc.j60870.ie.IeTime16;
import org.openmuc.j60870.ie.IeTime56;
import org.openmuc.j60870.ie.InformationElement;
import org.openmuc.j60870.ie.InformationObject;

/**
 * Represents an open connection to a specific 60870 server. It is created either through an instance of
 * {@link ClientConnectionBuilder} or passed to {@link ServerEventListener}. Once it has been closed it cannot be opened
 * again. A newly created connection has successfully build up a TCP/IP connection to the server. Before receiving ASDUs
 * or sending commands one has to call {@link Connection#startDataTransfer(ConnectionEventListener, int)} or
 * {@link Connection#waitForStartDT(ConnectionEventListener, int)}. Afterwards incoming ASDUs are forwarded to the
 * {@link ConnectionEventListener}. Incoming ASDUs are queued so that {@link ConnectionEventListener#newASdu(ASdu)} is
 * never called simultaneously for the same connection.
 *
 * <p>
 * Connection offers a method for every possible command defined by IEC 60870 (e.g. singleCommand). Every command
 * function may throw an IOException indicating a fatal connection error. In this case the connection will be
 * automatically closed and a new connection will have to be built up. The command methods do not wait for an
 * acknowledgment but return right after the command has been sent.
 * </p>
 */
public class Connection implements AutoCloseable {
    private static final byte[] TESTFR_CON_BUFFER = new byte[]{0x68, 0x04, (byte) 0x83, 0x00, 0x00, 0x00};
    private static final byte[] TESTFR_ACT_BUFFER = new byte[]{0x68, 0x04, (byte) 0x43, 0x00, 0x00, 0x00};
    private static final byte[] STARTDT_ACT_BUFFER = new byte[]{0x68, 0x04, 0x07, 0x00, 0x00, 0x00};
    private static final byte[] STARTDT_CON_BUFFER = new byte[]{0x68, 0x04, 0x0b, 0x00, 0x00, 0x00};
    private static final byte[] STOPDT_ACT_BUFFER = new byte[]{0x68, 0x04, 0x13, 0x00, 0x00, 0x00};
    private static final byte[] STOPDT_CON_BUFFER = new byte[]{0x68, 0x04, 0x23, 0x00, 0x00, 0x00};

    private final Socket socket;
    private final ServerThread serverThread;
    private final DataOutputStream os;
    private final ConnectionSettings settings;
    private final byte[] buffer = new byte[255];
    private final TimeoutManager timeoutManager;
    private final TimeoutTask maxTimeNoTestConReceived;
    private final TimeoutTask maxTimeNoAckReceived;
    private final TimeoutTask maxIdleTimeTimer;
    private final TimeoutTask maxTimeNoAckSentTimer;
    private final ExecutorService executor;
    private volatile boolean closed;
    private volatile boolean stopped = true;
    private ConnectionEventListener aSduListener;
    private ConnectionEventListener aSduListenerBack;
    private int sendSequenceNumber;
    private int receiveSequenceNumber;
    private int acknowledgedReceiveSequenceNumber;
    private int acknowledgedSendSequenceNumber;
    private int originatorAddress;
    private IOException closedIOException;
    private CountDownLatch startdtActSignal;
    private CountDownLatch startdtConSignal;
    private CountDownLatch stopdtConSignal;

    Connection(Socket socket, ServerThread serverThread, ConnectionSettings settings) throws IOException {

        try {
            os = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            socket.close();
            throw e;
        }

        this.socket = socket;
        this.settings = settings;
        this.serverThread = serverThread;
        if (serverThread != null) {
            startdtActSignal = new CountDownLatch(1);
        }

        ConnectionReader connectionReader = new ConnectionReader();
        connectionReader.start();

        this.maxTimeNoTestConReceived = new MaxTimeNoAckReceivedTimer();
        this.maxTimeNoAckReceived = new MaxTimeNoAckReceivedTimer();
        this.maxIdleTimeTimer = new MaxIdleTimeTimer();
        this.maxTimeNoAckSentTimer = new MaxTimeNoAckSentTimer();

        if (settings.useSharedThreadPool()) {
            this.executor = ConnectionSettings.getThreadPool();
        } else {
            this.executor = Executors.newFixedThreadPool(2);
        }
        ConnectionSettings.incremntConnectionsCounter();

        this.timeoutManager = new TimeoutManager();
        this.executor.execute(this.timeoutManager);
    }

    private static int sequenceNumberDiff(int number, int ackNumber) {
        // would hold true: ackNumber <= number (without mod 2^15)
        return ackNumber > number ? ((1 << 15) - ackNumber) + number : number - ackNumber;
    }

    /**
     * Stops the data transfer. Sends a STOPDT act and waits for a STOPDT con. If successful the data transfer stops and
     * releases the ASduListener. If no STOPDT con could be received, while t1, the connection will be closed.
     */
    public void stopDataTransfer() throws IOException {

        synchronized (this) {
            stopdtConSignal = new CountDownLatch(1);
            os.write(STOPDT_ACT_BUFFER);
        }
        os.flush();

        boolean success;
        try {
            success = stopdtConSignal.await(settings.getMaxTimeNoAckReceived(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            success = true;
            Thread.currentThread().interrupt();
        }

        if (!success) {
            throw new InterruptedIOException("Request timed out.");
        }

        synchronized (this) {
            aSduListenerBack = aSduListener;
            aSduListener = null;
            stopped = true;
        }
    }

    /**
     * Starts a connection. Sends a STARTDT act and waits for a STARTDT con. If successful a new thread will be started
     * that listens for incoming ASDUs and notifies the given ASduListener.
     *
     * @param listener the listener that is notified of incoming ASDUs
     * @param timeout  the maximum time in ms to wait for a STARDT CON message after sending the STARTDT ACT message. If set
     *                 to zero, timeout is disabled.
     * @throws IOException if any kind of IOException occurs.
     * @deprecated will be replaced by {@link #startDataTransfer(ConnectionEventListener listener)} in future releases.
     */
    @Deprecated
    public void startDataTransfer(ConnectionEventListener listener, int timeout) throws IOException {

        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout may not be negative.");
        }

        synchronized (this) {
            startdtConSignal = new CountDownLatch(1);
            os.write(STARTDT_ACT_BUFFER);
        }
        os.flush();

        if (timeout == 0) {
            try {
                startdtConSignal.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            boolean success;
            try {
                success = startdtConSignal.await(settings.getMaxTimeNoAckReceived(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                success = true;
                Thread.currentThread().interrupt();
            }

            if (!success) {
                throw new InterruptedIOException("Request timed out.");
            }
        }

        synchronized (this) {
            this.aSduListener = listener;
            stopped = false;
        }
    }

    /**
     * Starts a connection. Sends a STARTDT act and waits for a STARTDT con. If successful a new thread will be started
     * that listens for incoming ASDUs and notifies the given ASduListener.
     *
     * @param listener the listener that is notified of incoming ASDUs
     * @throws IOException if any kind of IOException occurs.
     */
    public void startDataTransfer(ConnectionEventListener listener) throws IOException {

        synchronized (this) {
            startdtConSignal = new CountDownLatch(1);
            os.write(STARTDT_ACT_BUFFER);
        }
        os.flush();

        boolean success;
        try {
            success = startdtConSignal.await(settings.getMaxTimeNoAckReceived(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            success = true;
            Thread.currentThread().interrupt();
        }

        if (!success) {
            throw new InterruptedIOException("Request timed out.");
        }

        synchronized (this) {
            this.aSduListener = listener;
            stopped = false;
        }
    }

    /**
     * Sets connection listener to be notified of incoming ASDUs and disconnect events.
     *
     * @param listener the listener that is to be notified of incoming ASDUs and disconnect events
     */
    public void setConnectionListener(ConnectionEventListener listener) {
        synchronized (this) {
            this.aSduListener = listener;
        }
    }

    /**
     * Waits for incoming STARTDT ACT message and response with a STARTDT CON message. Throws a TimeoutException if no
     * STARTDT message is received within the specified timeout span.
     *
     * @param listener the listener that is to be notified of incoming ASDUs and disconnect events
     * @param timeout  the maximum time in ms to wait for STARTDT ACT message before throwing a TimeoutException. If set to
     *                 zero, timeout is disabled.
     * @throws IOException if a fatal communication error occurred
     * @deprecated waitForStartDT is no longer needed and will be removed in future releases. Replaced by
     * {@link #setConnectionListener(ConnectionEventListener)}.
     */
    @Deprecated
    public void waitForStartDT(ConnectionEventListener listener, int timeout) throws IOException {

        if (timeout < 0) {
            throw new IllegalArgumentException("timeout may not be negative");
        }

        if (timeout == 0) {
            try {
                startdtActSignal.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            boolean success = true;
            try {
                success = startdtActSignal.await(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!success) {
                throw new InterruptedIOException();
            }
        }

        synchronized (this) {
            this.aSduListener = listener;
            stopped = false;
        }

        resetMaxIdleTimeTimer();
    }

    private void sendSFormatPdu() throws IOException {

        int length = new APdu(0, receiveSequenceNumber, ApciType.S_FORMAT, null).encode(buffer, settings);

        os.write(buffer, 0, length);
        os.flush();

        acknowledgedReceiveSequenceNumber = receiveSequenceNumber;

        resetMaxIdleTimeTimer();
    }

    /**
     * Get the configured Originator Address.
     *
     * @return the Originator Address
     */
    public int getOriginatorAddress() {
        return originatorAddress;
    }

    /**
     * Set the Originator Address. It is the address of controlling station (client) so that responses can be routed
     * back to it. Originator addresses from 1 to 255 are used to address a particular controlling station. Address 0 is
     * the default and is used if responses are to be routed to all controlling stations in the system. Note that the
     * same Originator Address is sent in a command and its confirmation.
     *
     * @param originatorAddress the Originator Address. Valid values are 0...255.
     */
    public void setOriginatorAddress(int originatorAddress) {
        if (originatorAddress < 0 || originatorAddress > 255) {
            throw new IllegalArgumentException("Originator Address must be between 0 and 255.");
        }
        this.originatorAddress = originatorAddress;
    }

    public int getNumUnconfirmedAPdusSent() {
        synchronized (this) {
            return sequenceNumberDiff(sendSequenceNumber, acknowledgedSendSequenceNumber);
        }
    }

    /**
     * Will close the TCP connection if its still open and free any resources of this connection.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        try {
            // close the socket, which also closes the streams
            socket.close();
        } catch (Exception e) {
            // ignore this here
        } finally {
            closed = true;
        }

        if (serverThread != null) {
            serverThread.connectionClosedSignal();
        }
    }

    /**
     * Returns true if connection is closed else false.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns true if data transfer is stopped.
     *
     * @return true if stopped
     */
    public boolean isStopped() {
        return stopped;
    }

    public synchronized void send(ASdu aSdu) throws IOException {

        while (getNumUnconfirmedAPdusSent() >= settings.getMaxNumOfOutstandingIPdus()) {
            try {
                this.wait(1);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        acknowledgedReceiveSequenceNumber = receiveSequenceNumber;
        APdu requestAPdu = new APdu(sendSequenceNumber, receiveSequenceNumber, ApciType.I_FORMAT, aSdu);

        int oldSendSequenceNumber = sendSequenceNumber;
        sendSequenceNumber = (sendSequenceNumber + 1) % (1 << 15); // 32768 = 2^15

        // check for sendSequenceNumber overflow
        if (oldSendSequenceNumber > sendSequenceNumber) {
            sendSFormatPdu();
        }

        if (this.maxTimeNoAckSentTimer.isPlanned()) {
            this.maxTimeNoAckSentTimer.cancel();
        }

        if (!this.maxTimeNoAckReceived.isPlanned()) {
            this.timeoutManager.addTimerTask(this.maxTimeNoAckReceived);
        }

        int length = requestAPdu.encode(buffer, settings);
        os.write(buffer, 0, length);
        os.flush();
        resetMaxIdleTimeTimer();
    }

    private void resetMaxIdleTimeTimer() {
        this.maxIdleTimeTimer.cancel();
        this.timeoutManager.addTimerTask(maxIdleTimeTimer);
    }

    /**
     * Send response with given aSdu. Common ASDU address of given ASDU is used as station address.
     *
     * @param aSdu ASDU which response to
     * @throws IOException if a fatal communication error occurred.
     */
    public void sendConfirmation(ASdu aSdu) throws IOException {
        sendConfirmation(aSdu, aSdu.getCommonAddress());
    }

    /**
     * Send response with given aSdu. Given station address is used as Common ASDU Address, if we response to broadcast
     * else given Common ASDU Address of aSdu.
     *
     * @param aSdu           ASDU which response to
     * @param stationAddress address of this station
     * @throws IOException if a fatal communication error occurred.
     */
    public void sendConfirmation(ASdu aSdu, int stationAddress) throws IOException {
        CauseOfTransmission cot = cotFrom(aSdu);
        int commonAddress = aSdu.getCommonAddress();
        int broadcastAddress;

        if (settings.getCommonAddressFieldLength() == 2) {
            broadcastAddress = 65535;
        } else {
            broadcastAddress = 255;
        }
        if (commonAddress == broadcastAddress) {
            commonAddress = stationAddress;
        }

        send(new ASdu(aSdu.getTypeIdentification(), aSdu.isSequenceOfElements(), cot, aSdu.isTestFrame(),
                aSdu.isNegativeConfirm(), aSdu.getOriginatorAddress(), commonAddress, aSdu.getInformationObjects()));
    }

    private CauseOfTransmission cotFrom(ASdu aSdu) {
        CauseOfTransmission cot = aSdu.getCauseOfTransmission();
        switch (cot) {
            case ACTIVATION:
                return CauseOfTransmission.ACTIVATION_CON;
            case DEACTIVATION:
                return CauseOfTransmission.DEACTIVATION_CON;
            default:
                return cot;
        }
    }

    /**
     * Sends a single command (C_SC_NA_1, TI: 45).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param singleCommand            the command to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void singleCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                              IeSingleCommand singleCommand) throws IOException {
        ASdu aSdu = new ASdu(ASduType.C_SC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, singleCommand));
        send(aSdu);
    }

    /**
     * Sends a single command with time tag CP56Time2a (C_SC_TA_1, TI: 58).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param singleCommand            the command to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void singleCommandWithTimeTag(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                                         IeSingleCommand singleCommand, IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_SC_TA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, singleCommand, timeTag));
        send(aSdu);
    }

    /**
     * Sends a double command (C_DC_NA_1, TI: 46).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param doubleCommand            the command to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void doubleCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                              IeDoubleCommand doubleCommand) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_DC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, doubleCommand));
        send(aSdu);
    }

    /**
     * Sends a double command with time tag CP56Time2a (C_DC_TA_1, TI: 59).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param doubleCommand            the command to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void doubleCommandWithTimeTag(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                                         IeDoubleCommand doubleCommand, IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_DC_TA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, doubleCommand, timeTag));
        send(aSdu);
    }

    /**
     * Sends a regulating step command (C_RC_NA_1, TI: 47).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param regulatingStepCommand    the command to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void regulatingStepCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                                      IeRegulatingStepCommand regulatingStepCommand) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_RC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, regulatingStepCommand));
        send(aSdu);
    }

    /**
     * Sends a regulating step command with time tag CP56Time2a (C_RC_TA_1, TI: 60).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param regulatingStepCommand    the command to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void regulatingStepCommandWithTimeTag(int commonAddress, CauseOfTransmission cot,
                                                 int informationObjectAddress, IeRegulatingStepCommand regulatingStepCommand, IeTime56 timeTag)
            throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_RC_TA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, regulatingStepCommand, timeTag));
        send(aSdu);
    }

    /**
     * Sends a set-point command, normalized value (C_SE_NA_1, TI: 48).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param normalizedValue          the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void setNormalizedValueCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                                          IeNormalizedValue normalizedValue, IeQualifierOfSetPointCommand qualifier) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_SE_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, normalizedValue, qualifier));

        send(aSdu);
    }

    /**
     * Sends a set-point command with time tag CP56Time2a, normalized value (C_SE_TA_1, TI: 61).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param normalizedValue          the value to be sent.
     * @param qualifier                the qualifier to be sent.executor
     * @param timeTag                  the time tag to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void setNormalizedValueCommandWithTimeTag(int commonAddress, CauseOfTransmission cot,
                                                     int informationObjectAddress, IeNormalizedValue normalizedValue, IeQualifierOfSetPointCommand qualifier,
                                                     IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_SE_TA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, normalizedValue, qualifier, timeTag));

        send(aSdu);
    }

    /**
     * Sends a set-point command, scaled value (C_SE_NB_1, TI: 49).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param scaledValue              the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void setScaledValueCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                                      IeScaledValue scaledValue, IeQualifierOfSetPointCommand qualifier) throws IOException {

        ASduType typeId = ASduType.C_SE_NB_1;
        ASdu aSdu = new ASdu(typeId, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, scaledValue, qualifier));
        send(aSdu);
    }

    /**
     * Sends a set-point command with time tag CP56Time2a, scaled value (C_SE_TB_1, TI: 62).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param scaledValue              the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void setScaledValueCommandWithTimeTag(int commonAddress, CauseOfTransmission cot,
                                                 int informationObjectAddress, IeScaledValue scaledValue, IeQualifierOfSetPointCommand qualifier,
                                                 IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_SE_TB_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, scaledValue, qualifier, timeTag));
        send(aSdu);
    }

    /**
     * Sends a set-point command, short floating point number (C_SE_NC_1, TI: 50).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param floatVal                 the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void setShortFloatCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                                     IeShortFloat floatVal, IeQualifierOfSetPointCommand qualifier) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_SE_NC_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, floatVal, qualifier));
        send(aSdu);
    }

    /**
     * Sends a set-point command with time tag CP56Time2a, short floating point number (C_SE_TC_1, TI: 63).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param shortFloat               the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void setShortFloatCommandWithTimeTag(int commonAddress, CauseOfTransmission cot,
                                                int informationObjectAddress, IeShortFloat shortFloat, IeQualifierOfSetPointCommand qualifier,
                                                IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_SE_TC_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, shortFloat, qualifier, timeTag));

        send(aSdu);
    }

    /**
     * Sends a bitstring of 32 bit (C_BO_NA_1, TI: 51).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param binaryStateInformation   the value to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void bitStringCommand(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                                 IeBinaryStateInformation binaryStateInformation) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_BO_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, binaryStateInformation));
        send(aSdu);
    }

    /**
     * Sends a bitstring of 32 bit with time tag CP56Time2a (C_BO_TA_1, TI: 64).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param binaryStateInformation   the value to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void bitStringCommandWithTimeTag(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                                            IeBinaryStateInformation binaryStateInformation, IeTime56 timeTag) throws IOException {

        ASdu aSdu = new ASdu(ASduType.C_BO_TA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, binaryStateInformation, timeTag));
        send(aSdu);
    }

    /**
     * Sends an interrogation command (C_IC_NA_1, TI: 100).
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot           the cause of transmission. Allowed are activation and deactivation.
     * @param qualifier     the qualifier to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void interrogation(int commonAddress, CauseOfTransmission cot, IeQualifierOfInterrogation qualifier)
            throws IOException {
        ASdu aSdu = new ASdu(ASduType.C_IC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(0, qualifier));

        send(aSdu);
    }

    /**
     * Sends a counter interrogation command (C_CI_NA_1, TI: 101).
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot           the cause of transmission. Allowed are activation and deactivation.
     * @param qualifier     the qualifier to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void counterInterrogation(int commonAddress, CauseOfTransmission cot,
                                     IeQualifierOfCounterInterrogation qualifier) throws IOException {
        ASdu aSdu = new ASdu(ASduType.C_CI_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(0, qualifier));
        send(aSdu);
    }

    /**
     * Sends a read command (C_RD_NA_1, TI: 102).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress the information object address.
     * @throws IOException if a fatal communication error occurred.
     */
    public void readCommand(int commonAddress, int informationObjectAddress) throws IOException {
        ASdu aSdu = new ASdu(ASduType.C_RD_NA_1, false, CauseOfTransmission.REQUEST, false, false, originatorAddress,
                commonAddress, new InformationObject(informationObjectAddress));
        send(aSdu);
    }

    /**
     * Sends a clock synchronization command (C_CS_NA_1, TI: 103).
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param time          the time to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void synchronizeClocks(int commonAddress, IeTime56 time) throws IOException {
        InformationObject io = new InformationObject(0, time);

        ASdu aSdu = new ASdu(ASduType.C_CS_NA_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, io);

        send(aSdu);
    }

    /**
     * Sends a test command (C_TS_NA_1, TI: 104).
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @throws IOException if a fatal communication error occurred.
     */
    public void testCommand(int commonAddress) throws IOException {
        ASdu aSdu = new ASdu(ASduType.C_TS_NA_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject(0, new IeFixedTestBitPattern()));

        send(aSdu);
    }

    /**
     * Sends a reset process command (C_RP_NA_1, TI: 105).
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param qualifier     the qualifier to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void resetProcessCommand(int commonAddress, IeQualifierOfResetProcessCommand qualifier) throws IOException {
        ASdu aSdu = new ASdu(ASduType.C_RP_NA_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject(0, qualifier));
        send(aSdu);
    }

    /**
     * Sends a delay acquisition command (C_CD_NA_1, TI: 106).
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot           the cause of transmission. Allowed are activation and spontaneous.
     * @param time          the time to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void delayAcquisitionCommand(int commonAddress, CauseOfTransmission cot, IeTime16 time) throws IOException {
        ASdu aSdu = new ASdu(ASduType.C_CD_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(0, time));
        send(aSdu);
    }

    /**
     * Sends a test command with time tag CP56Time2a (C_TS_TA_1, TI: 107).
     *
     * @param commonAddress       the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param testSequenceCounter the value to be sent.
     * @param time                the time to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void testCommandWithTimeTag(int commonAddress, IeTestSequenceCounter testSequenceCounter, IeTime56 time)
            throws IOException {
        ASdu aSdu = new ASdu(ASduType.C_TS_TA_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject(0, testSequenceCounter, time));
        send(aSdu);
    }

    /**
     * Sends a parameter of measured values, normalized value (P_ME_NA_1, TI: 110).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress the information object address.
     * @param normalizedValue          the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void parameterNormalizedValueCommand(int commonAddress, int informationObjectAddress,
                                                IeNormalizedValue normalizedValue, IeQualifierOfParameterOfMeasuredValues qualifier) throws IOException {
        ASdu aSdu = new ASdu(ASduType.P_ME_NA_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject(informationObjectAddress, normalizedValue, qualifier));
        send(aSdu);
    }

    /**
     * Sends a parameter of measured values, scaled value (P_ME_NB_1, TI: 111).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress the information object address.
     * @param scaledValue              the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void parameterScaledValueCommand(int commonAddress, int informationObjectAddress, IeScaledValue scaledValue,
                                            IeQualifierOfParameterOfMeasuredValues qualifier) throws IOException {
        ASdu aSdu = new ASdu(ASduType.P_ME_NB_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject(informationObjectAddress, scaledValue, qualifier));
        send(aSdu);
    }

    /**
     * Sends a parameter of measured values, short floating point number (P_ME_NC_1, TI: 112).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress the information object address.
     * @param shortFloat               the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void parameterShortFloatCommand(int commonAddress, int informationObjectAddress, IeShortFloat shortFloat,
                                           IeQualifierOfParameterOfMeasuredValues qualifier) throws IOException {
        ASdu aSdu = new ASdu(ASduType.P_ME_NC_1, false, CauseOfTransmission.ACTIVATION, false, false, originatorAddress,
                commonAddress, new InformationObject(informationObjectAddress, shortFloat, qualifier));
        send(aSdu);
    }

    /**
     * Sends a parameter activation (P_AC_NA_1, TI: 113).
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException if a fatal communication error occurred.
     */
    public void parameterActivation(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                                    IeQualifierOfParameterActivation qualifier) throws IOException {
        ASdu aSdu = new ASdu(ASduType.P_AC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, qualifier));
        send(aSdu);
    }

    public void fileReady(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
                          IeLengthOfFileOrSection lengthOfFile, IeFileReadyQualifier qualifier) throws IOException {
        ASdu aSdu = new ASdu(ASduType.F_FR_NA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, nameOfFile, lengthOfFile, qualifier));
        send(aSdu);
    }

    public void sectionReady(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
                             IeNameOfSection nameOfSection, IeLengthOfFileOrSection lengthOfSection, IeSectionReadyQualifier qualifier)
            throws IOException {
        ASdu aSdu = new ASdu(ASduType.F_SR_NA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, nameOfFile, nameOfSection, lengthOfSection, qualifier));
        send(aSdu);
    }

    public void callOrSelectFiles(int commonAddress, CauseOfTransmission cot, int informationObjectAddress,
                                  IeNameOfFile nameOfFile, IeNameOfSection nameOfSection, IeSelectAndCallQualifier qualifier)
            throws IOException {
        ASdu aSdu = new ASdu(ASduType.F_SC_NA_1, false, cot, false, false, originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, nameOfFile, nameOfSection, qualifier));
        send(aSdu);
    }

    public void lastSectionOrSegment(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
                                     IeNameOfSection nameOfSection, IeLastSectionOrSegmentQualifier qualifier, IeChecksum checksum)
            throws IOException {
        ASdu aSdu = new ASdu(ASduType.F_LS_NA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, nameOfFile, nameOfSection, qualifier, checksum));
        send(aSdu);
    }

    public void ackFileOrSection(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
                                 IeNameOfSection nameOfSection, IeAckFileOrSectionQualifier qualifier) throws IOException {
        ASdu aSdu = new ASdu(ASduType.F_AF_NA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, nameOfFile, nameOfSection, qualifier));
        send(aSdu);
    }

    public void sendSegment(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
                            IeNameOfSection nameOfSection, IeFileSegment segment) throws IOException {
        ASdu aSdu = new ASdu(ASduType.F_SG_NA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, nameOfFile, nameOfSection, segment));
        send(aSdu);
    }

    public void sendDirectory(int commonAddress, int informationObjectAddress, InformationElement[][] directory)
            throws IOException {
        ASdu aSdu = new ASdu(ASduType.F_DR_TA_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress, new InformationObject(informationObjectAddress, directory));
        send(aSdu);
    }

    public void queryLog(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
                         IeTime56 rangeStartTime, IeTime56 rangeEndTime) throws IOException {
        ASdu aSdu = new ASdu(ASduType.F_SC_NB_1, false, CauseOfTransmission.FILE_TRANSFER, false, false,
                originatorAddress, commonAddress,
                new InformationObject(informationObjectAddress, nameOfFile, rangeStartTime, rangeEndTime));
        send(aSdu);
    }

    /**
     * Time-out for acknowledges in case of no data messages t2 < t1 (t2: default 10)
     */
    private class MaxTimeNoAckSentTimer extends TimeoutTask {

        public MaxTimeNoAckSentTimer() {
            super(settings.getMaxTimeNoAckSent());
        }

        @Override
        public void execute() {

            synchronized (Connection.this) {
                if (Thread.interrupted()) {
                    return;
                }
                try {
                    sendSFormatPdu();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Time-out for sending test frames in case of a long idle state (t3: default 20 s)
     */
    private class MaxIdleTimeTimer extends TimeoutTask {
        public MaxIdleTimeTimer() {
            super(Connection.this.settings.getMaxIdleTime());
        }

        @Override
        public void execute() {

            synchronized (Connection.this) {
                if (Thread.interrupted()) {
                    return;
                }
                try {
                    os.write(TESTFR_ACT_BUFFER, 0, TESTFR_ACT_BUFFER.length);
                    os.flush();
                } catch (IOException e) {
                }
                timeoutManager.addTimerTask(maxTimeNoTestConReceived);
            }
        }

    }

    /**
     * Time-out of send or test APDUs (t2: default 15 s)
     */
    private class MaxTimeNoAckReceivedTimer extends TimeoutTask {

        public MaxTimeNoAckReceivedTimer() {
            super(Connection.this.settings.getMaxTimeNoAckReceived());
        }

        @Override
        public void execute() {

            synchronized (Connection.this) {
                if (Thread.interrupted()) {
                    return;
                }
                close();
                if (aSduListener != null) {
                    aSduListener.connectionClosed(new IOException(
                            "The maximum time that no confirmation was received (t1) has been exceeded. t1 = "
                                    + settings.getMaxTimeNoAckReceived() + "ms"));
                }
            }
        }
    }

    private class ConnectionReader extends Thread {

        @Override
        public void run() {
            Thread.currentThread().setName("ConnectionReader");

            try {
                while (true) {
                    final APdu aPdu = APdu.decode(socket, settings);

                    synchronized (Connection.this) {

                        switch (aPdu.getApciType()) {
                            case I_FORMAT:
                                closeIfStopped();
                                handleIFrame(aPdu);
                                break;
                            case S_FORMAT:
                                closeIfStopped();
                                handleReceiveSequenceNumber(aPdu.getReceiveSeqNumber());
                                break;
                            case STARTDT_CON:
                                if (startdtConSignal != null) {
                                    startdtConSignal.countDown();
                                }
                                break;
                            case STARTDT_ACT:
                                handleStartDtAct();
                                if (startdtActSignal != null) {
                                    startdtActSignal.countDown();
                                }
                                break;
                            case TESTFR_ACT:
                                sendTestFrameCon();
                                break;
                            case TESTFR_CON:
                                maxTimeNoTestConReceived.cancel();
                                break;
                            case STOPDT_CON:
                                if (stopdtConSignal != null) {
                                    stopdtConSignal.countDown();
                                }
                                break;
                            case STOPDT_ACT:
                                handleStopDtAct();
                                break;
                            default:
                                // should not occur.
                                throw new IOException("Got unexpected message with APCI Type: " + aPdu.getApciType());
                        }
                        resetMaxIdleTimeTimer();
                    }
                }
            } catch (EOFException e) {
                closedIOException = new IOException("Connection was closed by server.", e);
            } catch (IOException e) {
                closedIOException = e;
            } catch (Exception e) {
                closedIOException = new IOException("Unexpected Exception.", e);
            } finally {
                synchronized (Connection.this) {
                    if (!closed) {
                        close();
                        if (aSduListener != null) {
                            aSduListener.connectionClosed(closedIOException);
                        }
                    }
                    closeThreadPool();
                }
            }
        }

        private void sendTestFrameCon() throws IOException {
            os.write(TESTFR_CON_BUFFER);
            os.flush();
        }

        private void closeIfStopped() {
            if (serverThread != null && stopped) {
                close();
                if (aSduListenerBack != null) {
                    aSduListenerBack.connectionClosed(new IOException("Got S/I-Format message while STOPDT state."));
                }
            }
        }

        private void closeThreadPool() {
            if (settings.useSharedThreadPool()) {
                ConnectionSettings.decrementConnectionsCounter();
            } else {
                executor.shutdownNow();
            }
        }

        private void handleStopDtAct() throws IOException {

            synchronized (this) {
                os.write(STOPDT_CON_BUFFER);

                if (aSduListener != null) {
                    aSduListenerBack = aSduListener;
                    aSduListener = null;
                }
                stopped = true;
            }
            os.flush();
        }

        private void handleStartDtAct() throws IOException {

            synchronized (this) {
                os.write(STARTDT_CON_BUFFER, 0, STARTDT_CON_BUFFER.length);

                if (aSduListener == null) {
                    aSduListener = aSduListenerBack;
                }
                stopped = false;
            }
            os.flush();

            resetMaxIdleTimeTimer();
        }

        private void handleIFrame(final APdu aPdu) throws IOException {

            updateReceiveSeqNum(aPdu.getSendSeqNumber());

            handleReceiveSequenceNumber(aPdu.getReceiveSeqNumber());

            if (aSduListener != null) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        Thread.currentThread().setName("aSduListener");
                        aSduListener.newASdu(aPdu.getASdu());
                    }
                });
            }

            int numUnconfirmedIPdusReceived = sequenceNumberDiff(receiveSequenceNumber,
                    acknowledgedReceiveSequenceNumber);

            if (numUnconfirmedIPdusReceived >= settings.getMaxUnconfirmedIPdusReceived()) {
                sendSFormatPdu();
                if (maxTimeNoAckSentTimer.isPlanned()) {
                    maxTimeNoAckSentTimer.cancel();
                }
            } else if (!maxTimeNoAckSentTimer.isPlanned()
                    || (maxTimeNoAckSentTimer.isPlanned() && numUnconfirmedIPdusReceived == 1)) {
                timeoutManager.addTimerTask(maxTimeNoAckSentTimer);
            }

        }

        private void updateReceiveSeqNum(int sendSeqNumber) throws IOException {
            verifySeqNumber(sendSeqNumber);

            receiveSequenceNumber = (sendSeqNumber + 1) % (1 << 15); // 32768

            // check for receiveSequenceNumber overflow
            if (sendSeqNumber > receiveSequenceNumber) {
                sendSFormatPdu();
            }
        }

        private void verifySeqNumber(int sendSeqNumber) throws IOException {
            if (receiveSequenceNumber != sendSeqNumber) {
                String msg = MessageFormat.format("Got unexpected send sequence number: {0}, expected: {1}.",
                        sendSeqNumber, receiveSequenceNumber);
                throw new IOException(msg);
            }
        }

        private void handleReceiveSequenceNumber(int receiveSeqNumber) throws IOException {
            if (acknowledgedSendSequenceNumber == receiveSeqNumber) {
                return;
            }

            int diff = sequenceNumberDiff(receiveSeqNumber, acknowledgedSendSequenceNumber);
            if (diff > getNumUnconfirmedAPdusSent()) {
                String msg = MessageFormat.format(
                        "Got unexpected receive sequence number: {0}, expected a number between: {1} and {2}.",
                        receiveSeqNumber, acknowledgedSendSequenceNumber, sendSequenceNumber);
                throw new IOException(msg);
            }

            if (maxTimeNoAckReceived.isPlanned()) {
                maxTimeNoAckReceived.cancel();
            }

            acknowledgedSendSequenceNumber = receiveSeqNumber;

            if (sendSequenceNumber != acknowledgedSendSequenceNumber) {
                if (getNumUnconfirmedAPdusSent() > settings.getMaxNumOfOutstandingIPdus()) {
                    throw new IOException("Max number of outstanding IPdus is exceeded.");
                } else {
                    timeoutManager.addTimerTask(maxTimeNoAckReceived);
                }
            }
        }

    }

}
