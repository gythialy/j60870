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
package org.openmuc.j60870.ie;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Represents a fixed test bit pattern (FBP) information element.
 */
public class IeFixedTestBitPattern extends InformationElement {

    public IeFixedTestBitPattern() {
    }

    IeFixedTestBitPattern(DataInputStream is) throws IOException {
        if ((is.readByte() & 0xff) != 0x55 || (is.readByte() & 0xff) != 0xaa) {
            throw new IOException("Incorrect bit pattern in Fixed Test Bit Pattern.");
        }
    }

    @Override
    int encode(byte[] buffer, int i) {

        buffer[i++] = 0x55;
        buffer[i] = (byte) 0xaa;
        return 2;
    }

    @Override
    public String toString() {
        return "Fixed test bit pattern";
    }
}
