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

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Represents a normalized value (NVA) information element.
 * 
 * @author Stefan Feuerhahn
 * 
 */
public class IeNormalizedValue extends InformationElement {

    final int value;

    /**
     * Normalized value is a value in the range from -1 to (1-1/(2^15)) This class represents value as an integer from
     * -32768 to 32767 instead. In order to get the real normalized value you need to divide value by 32768.
     * 
     * @param value
     *            value in the range -32768 to 32767
     */
    public IeNormalizedValue(int value) {
        if (value < -32768 || value > 32767) {
            throw new IllegalArgumentException("Value has to be in the range -32768..32767");
        }
        this.value = value;
    }

    IeNormalizedValue(DataInputStream is) throws IOException {
        value = (is.readByte() & 0xff) | (is.readByte() << 8);
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
        return "Normalized value: " + ((double) value / 32768);
    }
}
