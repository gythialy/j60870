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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.openmuc.j60870.internal.ConnectionSettings;

final class ServerThread extends Thread {

    private final ServerSocket serverSocket;
    private final ConnectionSettings settings;
    private final int maxConnections;
    private final ServerEventListener serverSapListener;

    private boolean stopServer = false;
    private int numConnections = 0;

    ServerThread(ServerSocket serverSocket, ConnectionSettings settings, int maxConnections,
            ServerEventListener serverSapListener) {
        this.serverSocket = serverSocket;
        this.settings = settings;
        this.maxConnections = maxConnections;
        this.serverSapListener = serverSapListener;
    }

    private class ConnectionHandler extends Thread {

        private final Socket socket;
        private final ServerThread serverThread;

        public ConnectionHandler(Socket socket, ServerThread serverThread) {
            this.socket = socket;
            this.serverThread = serverThread;
        }

        @Override
        public void run() {
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
            serverSapListener.connectionIndication(serverConnection);
        }
    }

    @Override
    public void run() {

        ExecutorService executor = Executors.newFixedThreadPool(maxConnections);
        try {

            Socket clientSocket = null;

            while (true) {
                try {
                    clientSocket = serverSocket.accept();
                } catch (IOException e) {
                    if (stopServer == false) {
                        serverSapListener.serverStoppedListeningIndication(e);
                    }
                    return;
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
                }
                else {
                    serverSapListener.connectionAttemptFailed(new IOException(
                            "Maximum number of connections reached. Ignoring connection request. Maximum number of connections: "
                                    + maxConnections));
                }

            }
        } finally

        {
            executor.shutdown();
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
            }
        }
    }

}
