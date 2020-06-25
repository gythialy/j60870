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

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ServerSocketFactory;

/**
 * The server is used to start listening for IEC 60870-5-104 client connections.
 */
public class Server {

    private final int port;
    private final InetAddress bindAddr;
    private final int backlog;
    private final ServerSocketFactory serverSocketFactory;
    private final int maxConnections;
    private final ConnectionSettings settings;
    private ServerThread serverThread;
    private ExecutorService exec;

    private Server(Builder builder) {
        port = builder.port;
        bindAddr = builder.bindAddr;
        backlog = builder.backlog;
        serverSocketFactory = builder.serverSocketFactory;
        maxConnections = builder.maxConnections;
        settings = new ConnectionSettings(builder.settings);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts a new thread that listens on the configured port. This method is non-blocking.
     *
     * @param listener the ServerConnectionListener that will be notified when remote clients are connecting or the server
     *                 stopped listening.
     * @throws IOException if any kind of error occures while creating the server socket.
     */
    public void start(ServerEventListener listener) throws IOException {
        ConnectionSettings.incremntConnectionsCounter();
        if (this.settings.useSharedThreadPool()) {
            this.exec = ConnectionSettings.getThreadPool();
        } else {
            this.exec = Executors.newCachedThreadPool();
        }
        serverThread = new ServerThread(serverSocketFactory.createServerSocket(port, backlog, bindAddr), settings,
                maxConnections, listener, exec);
        this.exec.execute(this.serverThread);
    }

    /**
     * Stop listening for new connections. Existing connections are not touched.
     */
    public void stop() {
        if (serverThread == null) {
            return;
        }

        serverThread.stopServer();

        if (this.settings.useSharedThreadPool()) {
            ConnectionSettings.decrementConnectionsCounter();
        } else {
            this.exec.shutdown();
        }

        serverThread = null;
    }

    /**
     * The server builder which builds a 60870 server instance.
     *
     * @see Server#builder()
     */
    public static class Builder extends CommonBuilder<Builder, Server> {

        private int port = 2404;
        private InetAddress bindAddr = null;
        private int backlog = 0;
        private ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();

        private int maxConnections = 100;

        private Builder() {
        }

        /**
         * Sets the TCP port that the server will listen on. IEC 60870-5-104 usually uses port 2404.
         *
         * @param port the port
         * @return this builder
         */
        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the backlog that is passed to the java.net.ServerSocket.
         *
         * @param backlog the backlog
         * @return this builder
         */
        public Builder setBacklog(int backlog) {
            this.backlog = backlog;
            return this;
        }

        /**
         * Sets the IP address to bind to. It is passed to java.net.ServerSocket
         *
         * @param bindAddr the IP address to bind to
         * @return this builder
         */
        public Builder setBindAddr(InetAddress bindAddr) {
            this.bindAddr = bindAddr;
            return this;
        }

        /**
         * Sets the ServerSocketFactory to be used to create the ServerSocket. Default is
         * ServerSocketFactory.getDefault().
         *
         * @param socketFactory the ServerSocketFactory to be used to create the ServerSocket
         * @return this builder
         */
        public Builder setSocketFactory(ServerSocketFactory socketFactory) {
            this.serverSocketFactory = socketFactory;
            return this;
        }

        /**
         * Set the maximum number of client connections that are allowed in parallel.
         *
         * @param maxConnections the number of connections allowed (default is 100) @ return this builder
         * @return this builder
         */
        public Builder setMaxConnections(int maxConnections) {
            if (maxConnections <= 0) {
                throw new IllegalArgumentException("maxConnections is out of bound");
            }
            this.maxConnections = maxConnections;
            return this;
        }

        /**
         * To start/activate the server call {@link Server#start(ServerEventListener)} on the returned server.
         */
        @Override
        public Server build() {
            return new Server(this);
        }

    }

}
