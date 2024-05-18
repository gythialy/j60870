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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class ByteStreamHelper {

    public static void readFully(InputStream is, byte[] buffer) throws IOException {
        readFully(is, buffer, 0, buffer.length);
    }

    public static void readFully(InputStream is, byte[] buffer, int off, int len) throws IOException {
        do {
            int bytesRead = is.read(buffer, off, len);
            if (bytesRead == -1) {
                throw new EOFException("End of input stream reached.");
            }
            len -= bytesRead;
            off += bytesRead;
        } while (len > 0);
    }
}
