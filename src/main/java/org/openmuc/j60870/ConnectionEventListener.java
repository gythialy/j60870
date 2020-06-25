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
import java.util.EventListener;

/**
 * The listener interface for receiving incoming ASDUs and connection closed events. The class that is interested in
 * incoming ASDUs implements this interface. The object of that class is registered as a listener through the
 * {@link Connection#startDataTransfer(ConnectionEventListener, int)} or
 * {@link Connection#waitForStartDT(ConnectionEventListener, int)} method. Incoming ASDUs are queued so that
 * {@link #newASdu(ASdu)} is never called simultaneously for the same connection.
 */
public interface ConnectionEventListener extends EventListener {

    /**
     * Invoked when a new ASDU arrives.
     *
     * @param aSdu the ASDU that arrived.
     */
    void newASdu(ASdu aSdu);

    /**
     * Invoked when an IOException occurred while listening for incoming ASDUs. An IOException implies that the
     * {@link Connection} that feeds this listener was automatically closed and can no longer be used to send commands
     * or receive ASDUs.
     *
     * @param cause the exception that occurred.
     */
    void connectionClosed(IOException cause);

}
