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
package org.openmuc.j60870;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.openmuc.j60870.logging.LoggerFactory;
import org.openmuc.j60870.logging.LoggerInterface;

class ServerThread implements Runnable {

    private final ServerSocket serverSocket;
    private final ConnectionSettings settings;
    private final int maxConnections;
    private final ServerEventListener serverSapListener;
    private final List<String> allowedClientIps;

    private volatile boolean stopServer = false;
    private int numConnections = 0;
    private final ExecutorService executor;

    private static final LoggerInterface log =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());

    ServerThread(
            ServerSocket serverSocket,
            ConnectionSettings settings,
            int maxConnections,
            ServerEventListener serverSapListener,
            ExecutorService exec,
            List<String> allowedClientIps) {
        this.serverSocket = serverSocket;
        this.settings = settings;
        this.maxConnections = maxConnections;
        this.serverSapListener = serverSapListener;
        this.executor = exec;
        this.allowedClientIps = allowedClientIps;
    }

    private class ConnectionHandler implements Runnable {

        private final Socket socket;
        private final ServerThread serverThread;

        public ConnectionHandler(Socket socket, ServerThread serverThread) {
            this.socket = socket;
            this.serverThread = serverThread;
        }

        @Override
        public void run() {
            Thread.currentThread().setName("ConnectionHandler");
            Connection serverConnection;
            try {
                serverConnection = new Connection(socket, serverThread, settings);
            } catch (IOException e) {
                synchronized (ServerThread.this) {
                    numConnections--;
                }
                serverSapListener.connectionAttemptFailed(e);
                return;
            }
            ConnectionEventListener listener = serverSapListener.connectionIndication(serverConnection);
            serverConnection.start(listener);
        }
    }

    @Override
    public void run() {
        Thread.currentThread().setName("ServerThread");
        Socket clientSocket = null;

        while (!stopServer) {
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                if (!stopServer) {
                    serverSapListener.serverStoppedListeningIndication(e);
                }
                return;
            }
            if (!isClientAllowed(clientSocket)) {
                log.trace(
                        "Not allowed IP tried to connect, closing socket. Client IP: " + clientSocket.getInetAddress());
                closeQuietly(clientSocket);
                continue;
            }

            boolean startConnection = false;

            synchronized (this) {
                if (numConnections < maxConnections) {
                    numConnections++;
                    startConnection = true;
                }
            }

            if (startConnection) {
                ConnectionHandler connectionHandler = new ConnectionHandler(clientSocket, this);
                executor.execute(connectionHandler);
            } else {
                serverSapListener.connectionAttemptFailed(new IOException(
                        "Maximum number of connections reached. Ignoring connection request. Maximum number of connections: "
                                + maxConnections));
                try {
                    clientSocket.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private boolean isClientAllowed(Socket clientSocket) {
        if (allowedClientIps == null) {
            return true;
        }
        InetAddress clientAddr = clientSocket.getInetAddress();
        for (String allowedIp : allowedClientIps) {
            try {
                InetAddress allowedAddr = InetAddress.getByName(allowedIp);
                if (allowedAddr.equals(clientAddr)) {
                    return true;
                }
            } catch (java.net.UnknownHostException e) {
                // Skip invalid entries
            }
        }
        return false;
    }

    private void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    void connectionClosedSignal() {
        synchronized (this) {
            numConnections--;
        }
    }

    /**
     * Stops listening for new connections. Existing connections are not touched.
     */
    void stopServer() {
        stopServer = true;
        if (serverSocket.isBound()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore any errors.
            }
        }
    }
}
