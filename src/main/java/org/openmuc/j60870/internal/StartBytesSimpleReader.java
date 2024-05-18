/*
 * Copyright 2014-2024 Fraunhofer ISE
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
package org.openmuc.j60870.internal;

import java.io.DataInputStream;
import java.io.IOException;

public final class StartBytesSimpleReader {

    private final byte[] startBytes;
    private final DataInputStream is;

    /**
     * Creates a StartBytesSimpleReader.
     *
     * @param startBytes the start bytes to read
     * @param is         the input stream to read the start bytes from
     */
    public StartBytesSimpleReader(byte[] startBytes, DataInputStream is) {
        this.startBytes = startBytes;
        this.is = is;
    }

    /**
     * Reads from the input stream until the start bytes are received. The start bytes are put in the
     * {@code destBuffer}. Any bytes that do not match the start byte sequence are discarded. If reading from the input
     * stream causes an IOException it is propagated.
     *
     * @throws IOException if an IOException is thrown reading from the input stream
     */
    public void readStartBytes() throws IOException {
        byte b = is.readByte();
        whileLoop:
        while (true) {
            for (int i = 0; i < startBytes.length; i++) {
                if (b != startBytes[i]) {
                    if (i == 0) {
                        b = is.readByte();
                    }
                    continue whileLoop;
                }
                if (i != (startBytes.length - 1)) {
                    b = is.readByte();
                }
            }
            return;
        }
    }
}
