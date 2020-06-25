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
 * Represents a normalized value (NVA) information element.
 */
public class IeNormalizedValue extends InformationElement {

    final int value;

    /**
     * Normalized value is a value in the range from -1 to (1-1/(2^15)). The normalized value is encoded as a 16 bit
     * integer ranging from -32768 to 32767. In order to get the normalized value the integer value is divided by 32768.
     * Use this constructor to initialize the value exactly using the integer value in the range from -32768 to 32767.
     *
     * @param value non-normalized value in the range -32768 to 32767
     */
    public IeNormalizedValue(int value) {
        if (value < -32768 || value > 32767) {
            throw new IllegalArgumentException("Value has to be in the range -32768..32767");
        }
        this.value = value;
    }

    /**
     * Normalized value is a value in the range from -1 to (1-1/(2^15)). Use this constructor to initialize the value
     * using a double value ranging from -1 to (1-1/(2^15)).
     *
     * @param value normalized value in the range -1 to (1-1/(2^15))
     */
    public IeNormalizedValue(double value) {
        this.value = (int) (value * 32768.0);
        if (this.value < -32768 || this.value > 32767) {
            throw new IllegalArgumentException(
                    "The value multiplied by 32768 has to be an integer in the range -32768..32767, but it is: "
                            + this.value);
        }
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

    /**
     * Get the value as a normalized double value ranging from -1 to (1-1/(2^15))
     *
     * @return the value as a normalized double.
     */
    public double getNormalizedValue() {
        return ((double) value) / 32768;
    }

    /**
     * Get the value as a non-normalized integer value ranging from -32768..32767. In order to get the normalized value
     * the returned integer value has to be devided by 32768. The normalized value can also be retrieved using
     * {@link #getNormalizedValue()}
     *
     * @return the value as a non-normalized integer value
     */
    public int getUnnormalizedValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Normalized value: " + ((double) value / 32768);
    }
}
