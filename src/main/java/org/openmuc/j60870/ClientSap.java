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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.SocketFactory;

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
     */
    public ClientSap(SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
    }

    /**
     * Sets the default response timeout of the {@link Connection connections} created using this ClientSap. The
     * response timeout is the maximum time that the client will wait for the confirmation message (CON) after sending a
     * command. If such a timeout occurs the corresponding command function (e.g.
     * {@link Connection#interrogation(int, CauseOfTransmission, IeQualifierOfInterrogation) interrogation}) will throw
     * a TimeoutException.
     *
     * @param timeout the response timeout in milliseconds. The default is 10000 (10s).
     */
    public void setResponseTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("invalid response timeout");
        }
        settings.responseTimeout = timeout;
    }

    /**
     * Sets the message fragment timeout. This is the timeout that the socket timeout is set to after the first byte of
     * a message has been received. A command function (e.g.
     * {@link Connection#interrogation(int, CauseOfTransmission, IeQualifierOfInterrogation) interrogation}) will throw
     * an IOException if the socket throws this timeout. In addition any ASDU listener will be notified of the
     * IOException. Usually the connection cannot recover from this kind of error.
     *
     * @param timeout the timeout in milliseconds. The default is 10000.
     */
    public void setMessageFragmentTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("invalid message fragment timeout");
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
            throw new IllegalArgumentException("invalid length");
        }
        settings.ioaFieldLength = length;
    }

    /**
     * Sets the maximum time in ms before confirming received messages using an S format APDU.
     *
     * @param time the maximum time in ms before confirming received messages using an S format APDU. Default is 10000.
     */
    public void setMaxTimeWithoutAck(int time) {
        if (time < 1) {
            throw new IllegalArgumentException("invalid time");
        }
        settings.maxTimeWithoutAck = time;
    }

    /**
     * Sets the maximum number of I format APDUs frames after which the client connection will automatically send an S
     * format APDU to confirm them.
     *
     * @param maxNum the maximum number of I format APDUs frames after which the client connection will automatically send
     *               an S format APDU to confirm them. Default is 10.
     */
    public void setMaxIPdusReceivedWithoutAck(int maxNum) {
        if (maxNum < 1) {
            throw new IllegalArgumentException("invalid maxNum");
        }
        settings.maxIPdusReceivedWithoutAck = maxNum;
    }

    /**
     * Sets whether the different command methods of {@link Connection} shall wait for confirmation messages or not
     * before returning. If set to true the command message will only return after a confirmation message was received
     * or a timeout is thrown. If set to false the command functions will return immediately after the command message
     * was sent.
     *
     * @param wait whether the different command methods of <code>ClientSap</code> shall wait for confirmation messages
     *             or not. Default is true.
     */
    public void setWaitForConfirmation(boolean wait) {
        settings.waitForConfirmation = wait;
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
