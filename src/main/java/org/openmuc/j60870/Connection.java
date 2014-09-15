/*
 * Copyright 2014 Fraunhofer ISE
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
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openmuc.j60870.APdu.APCI_TYPE;

/**
 * Represents a connection between a client and a server. It is created either through an instance of {@link ClientSap}
 * or passed to {@link ServerSapListener}. Once it has been closed it cannot be opened again. A newly created connection
 * has successfully build up a TCP/IP connection to the server. Before receiving ASDUs or sending commands one has to
 * call {@link Connection#startDataTransfer(ConnectionEventListener)} or
 * {@link Connection#waitForStartDT(int, ConnectionEventListener)}. Afterwards incoming ASDUs are forwarded to the
 * {@link ConnectionEventListener}. Incoming ASDUs are queued so that {@link ConnectionEventListener#newASdu(ASdu)} is
 * never called simultaneously for the same connection. Note that the ASduListener is not notified of incoming
 * confirmation messages (CONs).
 * <p/>
 * Connection offers a method for every possible command defined by IEC 60870 (e.g. singleCommand). Every command method
 * will block until a the corresponding confirmation message was received. Every command function may throw an
 * IOException indicating a fatal connection error. In this case the connection will be automatically closed and a new
 * connection will have to be built up.
 *
 * @author Stefan Feuerhahn
 */
public class Connection {

    private final Socket socket;
    private final ServerThread serverThread;
    private final DataOutputStream os;
    private final DataInputStream is;

    private boolean closed = false;

    private final List<RequestMonitor> requestMonitors = new LinkedList<RequestMonitor>();

    private final ConnectionSettings settings;
    private ConnectionEventListener aSduListener = null;

    private int sendSequenceNumber = 0;
    private int receiveSequenceNumber = 0;
    private int acknowledgedReceiveSequenceNumber = 0;

    private int originatorAddress = 0;

    private final byte[] buffer = new byte[255];

    private static final byte[] TESTFR_CON_BUFFER = new byte[]{0x68,
                                                               0x04,
                                                               (byte) 0x83,
                                                               0x00,
                                                               0x00,
                                                               0x00};

    Timer timer = null;

    private IOException closedIOException = null;

    private CountDownLatch startdtactSignal;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private class ConnectionReader extends Thread {

        Connection outerClientConnection = Connection.this;

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

                    switch (aPdu.getApciType()) {
                    case I_FORMAT:
                        receiveSequenceNumber = (aPdu.getSendSeqNumber() + 1) % 32768;

                        CauseOfTransmission cot = aPdu.getASdu().getCauseOfTransmission();

                        boolean isConfirmation = false;
                        if (settings.waitForConfirmation
                            && (cot == CauseOfTransmission.ACTIVATION_CON
                                || cot == CauseOfTransmission.DEACTIVATION_CON)) {
                            synchronized (requestMonitors) {
                                for (RequestMonitor requestAndThread : requestMonitors) {
                                    if (requestAndThread.requestAPdu.getASdu()
                                                                    .isConfirmation(aPdu.getASdu(),
                                                                                    settings)) {
                                        isConfirmation = true;
                                        requestAndThread.responseAPdu = aPdu;
                                        requestAndThread.confirmationSignal.countDown();
                                        break;
                                    }
                                }
                            }
                        }

                        if (isConfirmation == false) {
                            if (aSduListener != null) {
                                executor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        aSduListener.newASdu(aPdu.getASdu());
                                    }
                                });
                            }
                        }

                        synchronized (Connection.this) {

                            int numNotAcknowledged = receiveSequenceNumber
                                                     - acknowledgedReceiveSequenceNumber;
                            if (numNotAcknowledged < 0) {
                                numNotAcknowledged += 32768;
                            }

                            if (numNotAcknowledged > settings.maxIPdusReceivedWithoutAck) {
                                encodeWriteReadDecode(APCI_TYPE.S_FORMAT, null, false);

                            } else {

                                if (timer == null) {

                                    timer = new Timer();

                                    TimerTask tt = new TimerTask() {
                                        @Override
                                        public void run() {
                                            try {
                                                outerClientConnection.encodeWriteReadDecode(
                                                        APCI_TYPE.S_FORMAT,
                                                        null,
                                                        false);
                                            }
                                            catch (IOException e) {
                                            }
                                            catch (TimeoutException e) {
                                            }
                                        }
                                    };
                                    timer.schedule(tt, settings.maxTimeWithoutAck);

                                }
                            }

                        }

                        break;
                    case STARTDT_CON:
                        synchronized (requestMonitors) {
                            for (RequestMonitor requestAndThread : requestMonitors) {
                                if (requestAndThread.requestAPdu.getApciType()
                                    == APCI_TYPE.STARTDT_ACT) {
                                    requestAndThread.responseAPdu = aPdu;
                                    requestAndThread.confirmationSignal.countDown();
                                }
                            }
                        }
                        break;
                    case STARTDT_ACT:
                        startdtactSignal.countDown();
                        break;
                    case S_FORMAT:
                        // receiveSequenceNumber = aPdu.getSendSeqNumber() + 1;
                        break;
                    case TESTFR_ACT:
                        writeTestFrCon();
                        break;
                    default:
                        throw new IOException("Got unexpected message with APCI Type: "
                                              + aPdu.getApciType());
                    }

                }
            }
            catch (EOFException e2) {
                if (closed == false) {
                    closedIOException = new EOFException("Socket was closed by remote host.");
                } else {
                    closedIOException = new EOFException("Socket is closed");
                }
            }
            catch (IOException e2) {
                closedIOException = e2;
            }
            catch (Exception e2) {
                closedIOException = new IOException("Unexpected Exception", e2);
            }
            finally {
                if (closed == false) {
                    close();
                    if (aSduListener != null) {
                        aSduListener.connectionClosed(closedIOException);
                    }
                }

                synchronized (requestMonitors) {
                    for (RequestMonitor requestAndThread : requestMonitors) {
                        requestAndThread.ioException = closedIOException;
                        requestAndThread.confirmationSignal.countDown();
                    }
                }
            }
        }
    }

    Connection(Socket socket, ServerThread serverThread, ConnectionSettings settings)
            throws IOException {

        try {
            os = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }
        catch (IOException e) {
            socket.close();
            throw e;
        }
        try {
            is = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        }
        catch (IOException e) {
            try {
                // this will also close the socket
                os.close();
            }
            catch (Exception e1) {
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
     * @param listener the listener that is notified of incoming ASDUs
     * @throws IOException      if any kind of IOException occurs
     * @throws TimeoutException if the configured response timeout runs out
     */
    public void startDataTransfer(ConnectionEventListener listener)
            throws IOException, TimeoutException {

        encodeWriteReadDecode(APCI_TYPE.STARTDT_ACT, null, true);

        this.aSduListener = listener;
    }

    /**
     * Waits for incoming STARTDT ACT message and response with a STARTDT CON message. Throws a TimeoutException if no
     * STARTDT message is received within the specified timeout span.
     *
     * @param timeout  the maximum time to wait for STARTDT ACT message before throwing a TimeoutException. If set to zero,
     *                 timeout is disabled.
     * @param listener the listener that is to be notified of incoming ASDUs and disconnect events
     * @throws IOException      if a fatal communication error occurred
     * @throws TimeoutException if the given timeout runs out before the STARTDT ACT message is received.
     */
    public void waitForStartDT(int timeout, ConnectionEventListener listener)
            throws IOException, TimeoutException {

        if (timeout < 0) {
            throw new IllegalArgumentException("timeout may not be negative");
        }

        if (timeout == 0) {
            try {
                startdtactSignal.await();
            }
            catch (InterruptedException e) {
            }
        } else {
            boolean success = true;
            try {
                success = startdtactSignal.await(timeout, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
            }
            if (!success) {
                throw new TimeoutException();
            }
        }

        encodeWriteReadDecode(APCI_TYPE.STARTDT_CON, null, false);
        this.aSduListener = listener;
    }

    private APdu encodeWriteReadDecode(ASdu requestASdu) throws IOException, TimeoutException {

        return encodeWriteReadDecode(APCI_TYPE.I_FORMAT, requestASdu, settings.waitForConfirmation);

    }

    private APdu encodeWriteReadDecode(APCI_TYPE apciType, ASdu requestASdu, boolean waitForCon)
            throws IOException,
            TimeoutException {

        APdu requestAPdu = null;
        CountDownLatch confirmationSignal;
        RequestMonitor requestMonitor;

        synchronized (this) {

            if (apciType == APCI_TYPE.STARTDT_ACT) {
                requestAPdu = new APdu(0, 0, APCI_TYPE.STARTDT_ACT, null);
            } else if (apciType == APCI_TYPE.STARTDT_CON) {
                requestAPdu = new APdu(0, 0, APCI_TYPE.STARTDT_CON, null);
            } else {
                acknowledgedReceiveSequenceNumber = receiveSequenceNumber;
                requestAPdu = new APdu((sendSequenceNumber++) % 32768,
                                       receiveSequenceNumber,
                                       apciType,
                                       requestASdu);
            }

            if (timer != null) {
                timer.cancel();
                timer = null;
            }

            int length = requestAPdu.encode(buffer, settings);

            if (!waitForCon) {
                os.write(buffer, 0, length);
                os.flush();
                return null;
            }

            confirmationSignal = new CountDownLatch(1);
            requestMonitor = new RequestMonitor(requestAPdu, confirmationSignal);

            synchronized (requestMonitors) {
                requestMonitors.add(requestMonitor);
            }
            try {
                os.write(buffer, 0, length);
                os.flush();
            }
            catch (IOException e) {
                synchronized (requestMonitors) {
                    requestMonitors.remove(requestMonitor);
                }
                throw e;
            }

        }

        if (settings.responseTimeout == 0) {
            try {
                confirmationSignal.await();
            }
            catch (InterruptedException e) {
            }
        } else {
            boolean success = true;
            try {
                success = confirmationSignal.await(settings.responseTimeout, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
            }
            if (!success) {
                throw new TimeoutException();
            }
        }

        synchronized (requestMonitors) {
            requestMonitors.remove(requestMonitor);
        }

        if (requestMonitor.ioException != null) {
            throw new IOException(requestMonitor.ioException);
        }
        if (requestMonitor.responseAPdu == null) {
            throw new TimeoutException();
        }

        return requestMonitor.responseAPdu;

    }

    private void writeTestFrCon() throws IOException {
        synchronized (this) {
            os.write(TESTFR_CON_BUFFER, 0, TESTFR_CON_BUFFER.length);
            os.flush();
        }
    }

    /**
     * The response timeout is the maximum time that the client will wait for the confirmation message after sending a
     * command. If such a timeout occurs the corresponding command function (e.g.
     * {@link Connection#interrogation(int, CauseOfTransmission, IeQualifierOfInterrogation) interrogation}) will throw
     * a TimeoutException.
     *
     * @param timeout the response timeout in milliseconds. The initial value is configured through {@link ClientSap} or
     *                {@link ServerSap}.
     */
    public void setResponseTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("invalid response timeout");
        }
        settings.responseTimeout = timeout;
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

    /**
     * Get the configured Originator Address.
     *
     * @return the Originator Address
     */
    public int getOriginatorAddress() {
        return originatorAddress;
    }

    /**
     * Sets whether the different command methods of <code>ClientConnection</code> shall wait for confirmation messages
     * or not before returning. If set to true the command message will only return after a confirmation message was
     * received or a timeout is thrown. If set to false the command functions will return immediately after the command
     * message was sent.
     *
     * @param wait whether the different command methods of <code>ClientSap</code> shall wait for confirmation messages
     *             or not. The initial value is configured through <code>ClientSap</code>.
     */
    public void setWaitForConfirmation(boolean wait) {
        settings.waitForConfirmation = wait;
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
            }
            catch (Exception e) {
            }
            try {
                is.close();
            }
            catch (Exception e) {
            }
            if (serverThread != null) {
                serverThread.connectionClosedSignal();
            }
        }
    }

    public void send(ASdu aSdu) throws IOException {
        try {
            encodeWriteReadDecode(APCI_TYPE.I_FORMAT, aSdu, false);
        }
        catch (TimeoutException e) {
        }
    }

    public void sendConfirmation(ASdu aSdu) throws IOException {
        CauseOfTransmission cot = aSdu.getCauseOfTransmission();
        if (cot == CauseOfTransmission.ACTIVATION) {
            cot = CauseOfTransmission.ACTIVATION_CON;
        } else if (cot == CauseOfTransmission.DEACTIVATION) {
            cot = CauseOfTransmission.DEACTIVATION_CON;
        }
        send(new ASdu(aSdu.getTypeIdentification(),
                      aSdu.isSequenceOfElements(),
                      cot,
                      aSdu.isTestFrame(),
                      aSdu.isNegativeConfirm(),
                      aSdu.getOriginatorAddress(),
                      aSdu.getCommonAddress(),
                      aSdu.getInformationObjects()));
    }

    /**
     * Sends a single command (C_SC_NA_1, TI: 45) and blocks until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress the information object address.
     * @param singleCommand            the command to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void singleCommand(int commonAddress,
                              int informationObjectAddress,
                              IeSingleCommand singleCommand)
            throws IOException, TimeoutException {
        CauseOfTransmission cot;
        if (singleCommand.isCommandStateOn()) {
            cot = CauseOfTransmission.ACTIVATION;
        } else {
            cot = CauseOfTransmission.DEACTIVATION;
        }
        ASdu aSdu = new ASdu(TypeId.C_SC_NA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {singleCommand}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a single command with time tag CP56Time2a (C_SC_TA_1, TI: 58) and blocks until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress the information object address.
     * @param singleCommand            the command to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void singleCommandWithTimeTag(int commonAddress, int informationObjectAddress,
                                         IeSingleCommand singleCommand, IeTime56 timeTag)
            throws IOException, TimeoutException {
        CauseOfTransmission cot;
        if (singleCommand.isCommandStateOn()) {
            cot = CauseOfTransmission.ACTIVATION;
        } else {
            cot = CauseOfTransmission.DEACTIVATION;
        }
        ASdu aSdu = new ASdu(TypeId.C_SC_TA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {singleCommand,
                                                                                    timeTag}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a double command (C_DC_NA_1, TI: 46) and blocks until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param doubleCommand            the command to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void doubleCommand(int commonAddress,
                              CauseOfTransmission cot,
                              int informationObjectAddress,
                              IeDoubleCommand doubleCommand) throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_DC_NA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {doubleCommand}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a double command with time tag CP56Time2a (C_DC_TA_1, TI: 59) and blocks until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param doubleCommand            the command to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void doubleCommandWithTimeTag(int commonAddress,
                                         CauseOfTransmission cot,
                                         int informationObjectAddress,
                                         IeDoubleCommand doubleCommand,
                                         IeTime56 timeTag) throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_DC_TA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {doubleCommand,
                                                                                    timeTag}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a regulating step command (C_RC_NA_1, TI: 47) and blocks until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param regulatingStepCommand    the command to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void regulatingStepCommand(int commonAddress,
                                      CauseOfTransmission cot,
                                      int informationObjectAddress,
                                      IeRegulatingStepCommand regulatingStepCommand)
            throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_RC_NA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {regulatingStepCommand}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a regulating step command with time tag CP56Time2a (C_RC_TA_1, TI: 60) and blocks until a confirmation is
     * received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param regulatingStepCommand    the command to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void regulatingStepCommandWithTimeTag(int commonAddress,
                                                 CauseOfTransmission cot,
                                                 int informationObjectAddress,
                                                 IeRegulatingStepCommand regulatingStepCommand,
                                                 IeTime56 timeTag)
            throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_RC_TA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {regulatingStepCommand,
                                                                                    timeTag}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a set-point command, normalized value (C_SE_NA_1, TI: 48) and blocks until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param normalizedValue          the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void setNormalizedValueCommand(int commonAddress,
                                          CauseOfTransmission cot,
                                          int informationObjectAddress,
                                          IeNormalizedValue normalizedValue,
                                          IeQualifierOfSetPointCommand qualifier)
            throws IOException,
            TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_SE_NA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {normalizedValue,
                                                                                    qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a set-point command with time tag CP56Time2a, normalized value (C_SE_TA_1, TI: 61) and blocks until a
     * confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param normalizedValue          the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void setNormalizedValueCommandWithTimeTag(int commonAddress,
                                                     CauseOfTransmission cot,
                                                     int informationObjectAddress,
                                                     IeNormalizedValue normalizedValue,
                                                     IeQualifierOfSetPointCommand qualifier,
                                                     IeTime56 timeTag)
            throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_SE_TA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {normalizedValue,
                                                                                    qualifier,
                                                                                    timeTag}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a set-point command, scaled value (C_SE_NB_1, TI: 49) and blocks until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param scaledValue              the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void setScaledValueCommand(int commonAddress,
                                      CauseOfTransmission cot,
                                      int informationObjectAddress,
                                      IeScaledValue scaledValue,
                                      IeQualifierOfSetPointCommand qualifier)
            throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_SE_NB_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {scaledValue,
                                                                                    qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a set-point command with time tag CP56Time2a, scaled value (C_SE_TB_1, TI: 62) and blocks until a
     * confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param scaledValue              the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void setScaledValueCommandWithTimeTag(int commonAddress,
                                                 CauseOfTransmission cot,
                                                 int informationObjectAddress,
                                                 IeScaledValue scaledValue,
                                                 IeQualifierOfSetPointCommand qualifier,
                                                 IeTime56 timeTag)
            throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_SE_TB_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {scaledValue,
                                                                                    qualifier,
                                                                                    timeTag}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a set-point command, short floating point number (C_SE_NC_1, TI: 50) and blocks until a confirmation is
     * received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param shortFloat               the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void setShortFloatCommand(int commonAddress,
                                     CauseOfTransmission cot,
                                     int informationObjectAddress,
                                     IeShortFloat shortFloat,
                                     IeQualifierOfSetPointCommand qualifier)
            throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_SE_NC_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {shortFloat,
                                                                                    qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a set-point command with time tag CP56Time2a, short floating point number (C_SE_TC_1, TI: 63) and blocks
     * until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param shortFloat               the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void setShortFloatCommandWithTimeTag(int commonAddress,
                                                CauseOfTransmission cot,
                                                int informationObjectAddress,
                                                IeShortFloat shortFloat,
                                                IeQualifierOfSetPointCommand qualifier,
                                                IeTime56 timeTag)
            throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_SE_TC_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {shortFloat,
                                                                                    qualifier,
                                                                                    timeTag}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a bitstring of 32 bit (C_BO_NA_1, TI: 51) and blocks until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param binaryStateInformation   the value to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void bitStringCommand(int commonAddress,
                                 CauseOfTransmission cot,
                                 int informationObjectAddress,
                                 IeBinaryStateInformation binaryStateInformation)
            throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_BO_NA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {binaryStateInformation}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a bitstring of 32 bit with time tag CP56Time2a (C_BO_TA_1, TI: 64) and blocks until a confirmation is
     * received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param binaryStateInformation   the value to be sent.
     * @param timeTag                  the time tag to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void bitStringCommandWithTimeTag(int commonAddress,
                                            CauseOfTransmission cot,
                                            int informationObjectAddress,
                                            IeBinaryStateInformation binaryStateInformation,
                                            IeTime56 timeTag) throws IOException, TimeoutException {

        ASdu aSdu = new ASdu(TypeId.C_BO_TA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {binaryStateInformation,
                                                                                    timeTag}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends an interrogation command (C_IC_NA_1, TI: 100) and blocks until a confirmation is received.
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot           the cause of transmission. Allowed are activation and deactivation.
     * @param qualifier     the qualifier to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void interrogation(int commonAddress,
                              CauseOfTransmission cot,
                              IeQualifierOfInterrogation qualifier)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.C_IC_NA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(0,
                                                                           new InformationElement[][]{
                                                                                   {qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a counter interrogation command (C_CI_NA_1, TI: 101) and blocks until a confirmation is received.
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot           the cause of transmission. Allowed are activation and deactivation.
     * @param qualifier     the qualifier to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void counterInterrogation(int commonAddress, CauseOfTransmission cot,
                                     IeQualifierOfCounterInterrogation qualifier)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.C_CI_NA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(0,
                                                                           new InformationElement[][]{
                                                                                   {qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a read command (C_RD_NA_1, TI: 102) and blocks until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress the information object address.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void readCommand(int commonAddress, int informationObjectAddress)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.C_RD_NA_1,
                             false,
                             CauseOfTransmission.REQUEST,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[0][0])});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a clock synchronization command (C_CS_NA_1, TI: 103) and blocks until a confirmation is received.
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param time          the time to be sent.
     * @return the time that was returned by the server in the confirmation message. Null if waiting for confirmation
     * messages was disabled.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public IeTime56 synchronizeClocks(int commonAddress, IeTime56 time)
            throws IOException, TimeoutException {
        InformationObject io = new InformationObject(0, new InformationElement[][]{{time}});

        InformationObject[] ios = new InformationObject[]{io};

        ASdu aSdu = new ASdu(TypeId.C_CS_NA_1,
                             false,
                             CauseOfTransmission.ACTIVATION,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             ios);

        APdu responsePdu = encodeWriteReadDecode(aSdu);

        return (IeTime56) responsePdu.getASdu()
                                     .getInformationObjects()[0].getInformationElements()[0][0];
    }

    /**
     * Sends a test command (C_TS_NA_1, TI: 104) and blocks until a confirmation is received.
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void testCommand(int commonAddress) throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.C_TS_NA_1,
                             false,
                             CauseOfTransmission.ACTIVATION,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(0,
                                                                           new InformationElement[][]{
                                                                                   {new IeFixedTestBitPattern()}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a reset process command (C_RP_NA_1, TI: 105) and blocks until a confirmation is received.
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param qualifier     the qualifier to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void resetProcessCommand(int commonAddress, IeQualifierOfResetProcessCommand qualifier)
            throws IOException,
            TimeoutException {
        ASdu aSdu = new ASdu(TypeId.C_RP_NA_1,
                             false,
                             CauseOfTransmission.ACTIVATION,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(0,
                                                                           new InformationElement[][]{
                                                                                   {qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a delay acquisition command (C_CD_NA_1, TI: 106) and blocks until a confirmation is received.
     *
     * @param commonAddress the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot           the cause of transmission. Allowed are activation and spontaneous.
     * @param time          the time to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void delayAcquisitionCommand(int commonAddress, CauseOfTransmission cot, IeTime16 time)
            throws IOException,
            TimeoutException {
        ASdu aSdu = new ASdu(TypeId.C_CD_NA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(0,
                                                                           new InformationElement[][]{
                                                                                   {time}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a test command with time tag CP56Time2a (C_TS_TA_1, TI: 107) and blocks until a confirmation is received.
     *
     * @param commonAddress       the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param testSequenceCounter the value to be sent.
     * @param time                the time to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void testCommandWithTimeTag(int commonAddress,
                                       IeTestSequenceCounter testSequenceCounter,
                                       IeTime56 time)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.C_TS_TA_1,
                             false,
                             CauseOfTransmission.ACTIVATION,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(0,
                                                                           new InformationElement[][]{
                                                                                   {
                                                                                           testSequenceCounter,
                                                                                           time}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a parameter of measured values, normalized value (P_ME_NA_1, TI: 110) and blocks until a confirmation is
     * received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress the information object address.
     * @param normalizedValue          the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void parameterNormalizedValueCommand(int commonAddress,
                                                int informationObjectAddress,
                                                IeNormalizedValue normalizedValue,
                                                IeQualifierOfParameterOfMeasuredValues qualifier)
            throws IOException,
            TimeoutException {
        ASdu aSdu = new ASdu(TypeId.P_ME_NA_1,
                             false,
                             CauseOfTransmission.ACTIVATION,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {normalizedValue,
                                                                                    qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a parameter of measured values, scaled value (P_ME_NB_1, TI: 111) and blocks until a confirmation is
     * received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress the information object address.
     * @param scaledValue              the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void parameterScaledValueCommand(int commonAddress,
                                            int informationObjectAddress,
                                            IeScaledValue scaledValue,
                                            IeQualifierOfParameterOfMeasuredValues qualifier)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.P_ME_NB_1,
                             false,
                             CauseOfTransmission.ACTIVATION,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {scaledValue,
                                                                                    qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a parameter of measured values, short floating point number (P_ME_NC_1, TI: 112) and blocks until a
     * confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param informationObjectAddress the information object address.
     * @param shortFloat               the value to be sent.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void parameterShortFloatCommand(int commonAddress,
                                           int informationObjectAddress,
                                           IeShortFloat shortFloat,
                                           IeQualifierOfParameterOfMeasuredValues qualifier)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.P_ME_NC_1,
                             false,
                             CauseOfTransmission.ACTIVATION,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {shortFloat,
                                                                                    qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    /**
     * Sends a parameter activation (P_AC_NA_1, TI: 113) and blocks until a confirmation is received.
     *
     * @param commonAddress            the Common ASDU Address. Valid value are 1...255 or 1...65535 for field lengths 1 or 2 respectively.
     * @param cot                      the cause of transmission. Allowed are activation and deactivation.
     * @param informationObjectAddress the information object address.
     * @param qualifier                the qualifier to be sent.
     * @throws IOException      if a fatal communication error occurred.
     * @throws TimeoutException if the configured response timeout runs out before the confirmation message is received.
     */
    public void parameterActivation(int commonAddress,
                                    CauseOfTransmission cot,
                                    int informationObjectAddress,
                                    IeQualifierOfParameterActivation qualifier)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.P_AC_NA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    public void fileReady(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
                          IeLengthOfFileOrSection lengthOfFile, IeFileReadyQualifier qualifier)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.F_FR_NA_1,
                             false,
                             CauseOfTransmission.FILE_TRANSFER,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(
                                     informationObjectAddress,
                                     new InformationElement[][]{{nameOfFile,
                                                                 lengthOfFile,
                                                                 qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    public void sectionReady(int commonAddress,
                             int informationObjectAddress,
                             IeNameOfFile nameOfFile,
                             IeNameOfSection nameOfSection,
                             IeLengthOfFileOrSection lengthOfSection,
                             IeSectionReadyQualifier qualifier)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.F_SR_NA_1,
                             false,
                             CauseOfTransmission.FILE_TRANSFER,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(
                                     informationObjectAddress,
                                     new InformationElement[][]{{nameOfFile, nameOfSection,
                                                                 lengthOfSection, qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    public void callOrSelectFiles(int commonAddress,
                                  CauseOfTransmission cot,
                                  int informationObjectAddress,
                                  IeNameOfFile nameOfFile,
                                  IeNameOfSection nameOfSection,
                                  IeSelectAndCallQualifier qualifier)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.F_SC_NA_1,
                             false,
                             cot,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {nameOfFile,
                                                                                    nameOfSection,
                                                                                    qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    public void lastSectionOrSegment(int commonAddress,
                                     int informationObjectAddress,
                                     IeNameOfFile nameOfFile,
                                     IeNameOfSection nameOfSection,
                                     IeLastSectionOrSegmentQualifier qualifier,
                                     IeChecksum checksum)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.F_LS_NA_1,
                             false,
                             CauseOfTransmission.FILE_TRANSFER,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(
                                     informationObjectAddress,
                                     new InformationElement[][]{{nameOfFile,
                                                                 nameOfSection,
                                                                 qualifier,
                                                                 checksum}})});
        encodeWriteReadDecode(aSdu);
    }

    public void ackFileOrSection(int commonAddress,
                                 int informationObjectAddress,
                                 IeNameOfFile nameOfFile,
                                 IeNameOfSection nameOfSection,
                                 IeAckFileOrSectionQualifier qualifier)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.F_AF_NA_1,
                             false,
                             CauseOfTransmission.FILE_TRANSFER,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(
                                     informationObjectAddress,
                                     new InformationElement[][]{{nameOfFile,
                                                                 nameOfSection,
                                                                 qualifier}})});
        encodeWriteReadDecode(aSdu);
    }

    public void sendSegment(int commonAddress,
                            int informationObjectAddress,
                            IeNameOfFile nameOfFile,
                            IeNameOfSection nameOfSection,
                            IeFileSegment segment) throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.F_SG_NA_1,
                             false,
                             CauseOfTransmission.FILE_TRANSFER,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(informationObjectAddress,
                                                                           new InformationElement[][]{
                                                                                   {nameOfFile,
                                                                                    nameOfSection,
                                                                                    segment}})});
        encodeWriteReadDecode(aSdu);
    }

    public void sendDirectory(int commonAddress,
                              int informationObjectAddress,
                              InformationElement[][] directory)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.F_DR_TA_1,
                             false,
                             CauseOfTransmission.FILE_TRANSFER,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(
                                     informationObjectAddress, directory)});
        encodeWriteReadDecode(aSdu);
    }

    public void queryLog(int commonAddress, int informationObjectAddress, IeNameOfFile nameOfFile,
                         IeTime56 rangeStartTime, IeTime56 rangeEndTime)
            throws IOException, TimeoutException {
        ASdu aSdu = new ASdu(TypeId.F_SC_NB_1,
                             false,
                             CauseOfTransmission.FILE_TRANSFER,
                             false,
                             false,
                             originatorAddress,
                             commonAddress,
                             new InformationObject[]{new InformationObject(
                                     informationObjectAddress,
                                     new InformationElement[][]{{nameOfFile, rangeStartTime,
                                                                 rangeEndTime}})});
        encodeWriteReadDecode(aSdu);
    }

}
