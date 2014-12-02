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

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * The Client Service Access Point is used to connect to IEC 60870-5-104 servers. A client application that wants to
 * connect to a server should first create an instance of ClientSap. Next all the necessary configuration parameters can
 * be set. Finally the {@link ClientSap#connect(InetAddress) connect} function is called to connect to the server. An
 * instance of ClientSap can be used to create an unlimited number of connections. Changing the parameters of a
 * ClientSap has no affect on connections that have already been created.
 * <p/>
 * Note that the configured lengths of the fields COT, CA and IOA have to be the same for all communicating nodes in a
 * network. The default values used by ClientSap are those most commonly used in IEC 60870-5-104 communication.
 *
 * @author Stefan Feuerhahn
 */
public class ClientSap {

    private final SocketFactory socketFactory;

    private final ConnectionSettings settings = new ConnectionSettings();

    /**
     * Use this constructor to create a default client SAP that uses <code>SocketFactory.getDefault()</code> as its
     * SocketFactory.
     */
    public ClientSap() {
        socketFactory = SocketFactory.getDefault();
    }

    /**
     * Use this constructor to create a client SAP that uses the given <code>SocketFactory</code> to connect to servers.
     * You could pass an SSLSocketFactory to enable SSL.
     *
     * @param socketFactory the socket factory
     */
    public ClientSap(SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
    }

    /**
     * Sets the message fragment timeout. This is the timeout that the socket timeout is set to after the first byte of
     * a message has been received. A command function (e.g.
     * {@link Connection#interrogation(int, CauseOfTransmission, IeQualifierOfInterrogation) interrogation}) will throw
     * an IOException if the socket throws this timeout. In addition any ASDU listener will be notified of the
     * IOException. Usually the connection cannot recover from this kind of error.
     *
     * @param timeout the timeout in milliseconds. The default is 5000.
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
     * @param length the length of the Cause Of Transmission field
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
     * @param length the length of the Common Address (CA) field
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
     * @param length the length of the Information Object Address field
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
     * @param time the maximum time in ms that no acknowledgement has been received before actively closing the
     *             connection.
     */
    public void setMaxTimeNoAckReceived(int time) {
        if (time < 1000 || time > 255000) {
            throw new IllegalArgumentException("invalid timeout: " + time
                                               + ", time must be between 1000ms and 255000ms");
        }
        settings.maxTimeNoAckReceived = time;
    }

    /**
     * Sets the maximum time in ms before confirming received messages that have not yet been acknowledged using an S
     * format APDU. This timeout is called t2 by the standard. Default is 10s, minimum is 1s, maximum is 255s.
     *
     * @param time the maximum time in ms before confirming received messages that have not yet been acknowledged using
     *             an S format APDU.
     */
    public void setMaxTimeNoAckSent(int time) {
        if (time < 1000 || time > 255000) {
            throw new IllegalArgumentException("invalid timeout: " + time
                                               + ", time must be between 1000ms and 255000ms");
        }
        settings.maxTimeNoAckSent = time;
    }

    /**
     * Sets the maximum time in ms that the connection may be idle before sending a test frame. This timeout is called
     * t3 by the standard. Default is 20s, minimum is 1s, maximum is 172800s (48h).
     *
     * @param time the maximum time in ms that the connection may be idle before sending a test frame.
     */
    public void setMaxIdleTime(int time) {
        if (time < 1000 || time > 172800000) {
            throw new IllegalArgumentException("invalid timeout: " + time
                                               + ", time must be between 1000ms and 172800000ms");
        }
        settings.maxIdleTime = time;
    }

    /**
     * Sets the number of unacknowledged I format APDUs received before the connection will automatically send an S
     * format APDU to confirm them. This parameter is called w by the standard. Default is 8, minimum is 1, maximum is
     * 32767.
     *
     * @param maxNum the number of unacknowledged I format APDUs received before the connection will automatically send an
     *               S format APDU to confirm them.
     */
    public void setMaxUnconfirmedIPdusReceived(int maxNum) {
        if (maxNum < 1 || maxNum > 32767) {
            throw new IllegalArgumentException("invalid maxNum: "
                                               + maxNum
                                               + ", must be a value between 1 and 32767");
        }
        settings.maxUnconfirmedIPdusReceived = maxNum;
    }

    /**
     * Connects to the given address on port 2404. The TCP/IP connection is build up and a Connection object is returned
     * that can be used to communicate with the server.
     *
     * @param address the address to connect to
     * @return the ClientConnection object that can be used to communicate with the server.
     * @throws IOException if any kind of error occurs during connection build up.
     */
    public Connection connect(InetAddress address) throws IOException {
        return connect(address, 2404, null, 0);
    }

    /**
     * Connects to the given address and port. The TCP/IP connection is build up and a Connection object is returned
     * that can be used to communicate with the server.
     *
     * @param address the address to connect to
     * @param port    the port to connect to. The IEC 60870-5-104 standard specifies the use of port 2404.
     * @return the ClientConnection object that can be used to communicate with the server.
     * @throws IOException if any kind of error occurs during connection build up.
     */
    public Connection connect(InetAddress address, int port) throws IOException {

        return connect(address, port, null, 0);
    }

    /**
     * Connects to the given address and port. The TCP/IP connection is build up and a Connection object is returned
     * that can be used to communicate with the server.
     *
     * @param address   the address to connect to
     * @param port      the port to connect to. The IEC 60870-5-104 standard specifies the use of port 2404.
     * @param localAddr the local address the socket is bound to, or null for anyLocal address.
     * @param localPort the local port the socket is bound to or zero for a system selected free port.
     * @return the Connection object that can be used to communicate with the server.
     * @throws IOException if any kind of error occurs during connection build up.
     */
    public Connection connect(InetAddress address, int port, InetAddress localAddr, int localPort)
            throws IOException {
        Socket socket;
        if (localAddr == null) {
            socket = socketFactory.createSocket(address, port);
        } else {
            socket = socketFactory.createSocket(address, port, localAddr, localPort);
        }

        Connection clientConnection = new Connection(socket, null, settings);

        return clientConnection;
    }
}
