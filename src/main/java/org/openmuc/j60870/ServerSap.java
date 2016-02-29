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

import java.io.IOException;
import java.net.InetAddress;

import javax.net.ServerSocketFactory;

/**
 * The Server Service Access Point is used to start listening for IEC 60870-5-104 client connections.
 * 
 * @author Stefan Feuerhahn
 * 
 */
public class ServerSap {

    private ServerThread serverThread;

    private final int port;
    private final InetAddress bindAddr;
    private final int backlog;
    private final ServerSapListener connectionListener;
    private final ServerSocketFactory serverSocketFactory;

    private final ConnectionSettings settings = new ConnectionSettings();
    private int maxConnections = 100;

    /**
     * Use this constructor to create a ServerSAP that listens on port 2404 using the default ServerSocketFactory.
     * 
     * @param connectionListener
     *            the ServerConnectionListener that will be notified when remote clients are connecting or the server
     *            stopped listening.
     */
    public ServerSap(ServerSapListener connectionListener) {
        this(2404, 0, null, ServerSocketFactory.getDefault(), connectionListener);
    }

    /**
     * Use this constructor to create a ServerSAP that listens on the given port using the default ServerSocketFactory.
     * 
     * @param port
     *            the TCP port that the server will listen on. IEC 60870-5-104 usually uses port 2404.
     * @param connectionListener
     *            the ServerConnectionListener that will be notified when remote clients are connecting or the server
     *            stopped listening.
     */
    public ServerSap(int port, ServerSapListener connectionListener) {
        this(port, 0, null, ServerSocketFactory.getDefault(), connectionListener);
    }

    /**
     * Use this constructor to create a ServerSAP that can listen on a port with a specified ServerSocketFactory.
     * 
     * @param port
     *            the TCP port that the server will listen on. IEC 60870-5-104 usually uses port 2404.
     * @param backlog
     *            is passed to the java.net.ServerSocket
     * @param bindAddr
     *            the IP address to bind to. It is passed to java.net.ServerSocket
     * @param serverSocketFactory
     *            The ServerSocketFactory to be used to create the ServerSocket
     * @param connectionListener
     *            the ServerConnectionListener that will be notified when remote clients are connecting or the server
     *            stopped listening.
     */
    public ServerSap(int port, int backlog, InetAddress bindAddr, ServerSocketFactory serverSocketFactory,
            ServerSapListener connectionListener) {

        this.port = port;
        this.backlog = backlog;
        this.bindAddr = bindAddr;
        this.connectionListener = connectionListener;
        this.serverSocketFactory = serverSocketFactory;
    }

    /**
     * Sets the message fragment timeout. This is the timeout that the socket timeout is set to after the first byte of
     * a message has been received. A command function (e.g.
     * {@link Connection#interrogation(int, CauseOfTransmission, IeQualifierOfInterrogation) interrogation}) will throw
     * an IOException if the socket throws this timeout. In addition any ASDU listener will be notified of the
     * IOException. Usually the connection cannot recover from this kind of error.
     *
     * @param timeout
     *            the timeout in milliseconds. The default is 5000.
     */
    public void setMessageFragmentTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("invalid message fragment timeout: " + timeout);
        }
        settings.messageFragmentTimeout = timeout;
    }

    /**
     * Sets the length of the Cause Of Transmission (COT) field of the ASDU. Allowed values are 1 or 2. The default is
     * 2.
     *
     * @param length
     *            the length of the Cause Of Transmission field
     */
    public void setCotFieldLength(int length) {
        if (length != 1 && length != 2) {
            throw new IllegalArgumentException("invalid length");
        }
        settings.cotFieldLength = length;
    }

    /**
     * Sets the length of the Common Address (CA) field of the ASDU. Allowed values are 1 or 2. The default is 2.
     *
     * @param length
     *            the length of the Common Address (CA) field
     */
    public void setCommonAddressFieldLength(int length) {
        if (length != 1 && length != 2) {
            throw new IllegalArgumentException("invalid length");
        }
        settings.cotFieldLength = length;
    }

    /**
     * Sets the length of the Information Object Address (IOA) field of the ASDU. Allowed values are 1, 2 or 3. The
     * default is 3.
     *
     * @param length
     *            the length of the Information Object Address field
     */
    public void setIoaFieldLength(int length) {
        if (length < 1 || length > 3) {
            throw new IllegalArgumentException("invalid length: " + length);
        }
        settings.ioaFieldLength = length;
    }

    /**
     * Sets the maximum time in ms that no acknowledgement has been received (for I-Frames or Test-Frames) before
     * actively closing the connection. This timeout is called t1 by the standard. Default is 15s, minimum is 1s,
     * maximum is 255s.
     *
     * @param time
     *            the maximum time in ms that no acknowledgement has been received before actively closing the
     *            connection.
     */
    public void setMaxTimeNoAckReceived(int time) {
        if (time < 1000 || time > 255000) {
            throw new IllegalArgumentException(
                    "invalid timeout: " + time + ", time must be between 1000ms and 255000ms");
        }
        settings.maxTimeNoAckReceived = time;
    }

    /**
     * Sets the maximum time in ms before confirming received messages that have not yet been acknowledged using an S
     * format APDU. This timeout is called t2 by the standard. Default is 10s, minimum is 1s, maximum is 255s.
     *
     * @param time
     *            the maximum time in ms before confirming received messages that have not yet been acknowledged using
     *            an S format APDU.
     */
    public void setMaxTimeNoAckSent(int time) {
        if (time < 1000 || time > 255000) {
            throw new IllegalArgumentException(
                    "invalid timeout: " + time + ", time must be between 1000ms and 255000ms");
        }
        settings.maxTimeNoAckSent = time;
    }

    /**
     * Sets the maximum time in ms that the connection may be idle before sending a test frame. This timeout is called
     * t3 by the standard. Default is 20s, minimum is 1s, maximum is 172800s (48h).
     *
     * @param time
     *            the maximum time in ms that the connection may be idle before sending a test frame.
     */
    public void setMaxIdleTime(int time) {
        if (time < 1000 || time > 172800000) {
            throw new IllegalArgumentException(
                    "invalid timeout: " + time + ", time must be between 1000ms and 172800000ms");
        }
        settings.maxIdleTime = time;
    }

    /**
     * Sets the number of unacknowledged I format APDUs received before the connection will automatically send an S
     * format APDU to confirm them. This parameter is called w by the standard. Default is 8, minimum is 1, maximum is
     * 32767.
     *
     * @param maxNum
     *            the number of unacknowledged I format APDUs received before the connection will automatically send an
     *            S format APDU to confirm them.
     */
    public void setMaxUnconfirmedIPdusReceived(int maxNum) {
        if (maxNum < 1 || maxNum > 32767) {
            throw new IllegalArgumentException("invalid maxNum: " + maxNum + ", must be a value between 1 and 32767");
        }
        settings.maxUnconfirmedIPdusReceived = maxNum;
    }

    /**
     * Set the maximum number of client connections that are allowed in parallel.
     * 
     * @param maxConnections
     *            the number of connections allowed (default is 100)
     */
    public void setMaxConnections(int maxConnections) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maxConnections is out of bound");
        }
        this.maxConnections = maxConnections;
    }

    /**
     * Starts a new thread that listens on the configured port. This method is non-blocking.
     * 
     * @throws IOException
     *             if any kind of error occures while creating the server socket.
     */
    public void startListening() throws IOException {
        serverThread = new ServerThread(serverSocketFactory.createServerSocket(port, backlog, bindAddr),
                settings.getCopy(), maxConnections, connectionListener);
        serverThread.start();
    }

}
