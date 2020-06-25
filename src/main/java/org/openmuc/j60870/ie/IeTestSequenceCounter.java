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
 * Represents a test sequence Counter (TSC) information element.
 */
public class IeTestSequenceCounter extends InformationElement {

    private static final int LOWER_BOUND = 0;
    private static final int UPPER_BOUND = 65535; // 2^16 - 1

    private final int value;

    public IeTestSequenceCounter(int value) {
        if (value < LOWER_BOUND || value > UPPER_BOUND) {
            throw new IllegalArgumentException("Value has to be in the range 0..65535");
        }

        this.value = value;
    }

    static IeTestSequenceCounter decode(DataInputStream is) throws IOException {
        return new IeTestSequenceCounter(is.readUnsignedByte() | (is.readUnsignedByte() << 8));
    }

    @Override
    int encode(byte[] buffer, int i) {

        buffer[i++] = (byte) value;
        buffer[i] = (byte) (value >> 8);

        return 2;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Test sequence counter: " + getValue();
    }

}
