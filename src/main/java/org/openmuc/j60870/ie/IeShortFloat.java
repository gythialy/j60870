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
 * Represents a short floating point number (R32-IEEE STD 754) information element.
 */
public class IeShortFloat extends InformationElement {

    private final float value;

    public IeShortFloat(float value) {
        this.value = value;
    }

    IeShortFloat(DataInputStream is) throws IOException {
        value = Float.intBitsToFloat((is.readByte() & 0xff) | ((is.readByte() & 0xff) << 8)
                | ((is.readByte() & 0xff) << 16) | ((is.readByte() & 0xff) << 24));
    }

    @Override
    int encode(byte[] buffer, int i) {

        int tempVal = Float.floatToIntBits(value);
        buffer[i++] = (byte) tempVal;
        buffer[i++] = (byte) (tempVal >> 8);
        buffer[i++] = (byte) (tempVal >> 16);
        buffer[i] = (byte) (tempVal >> 24);

        return 4;
    }

    public float getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Short float value: " + value;
    }
}
