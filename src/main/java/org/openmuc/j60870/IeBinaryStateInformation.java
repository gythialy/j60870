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
 * Represents a binary state information (BSI) information element.
 * 
 * @author Stefan Feuerhahn
 * 
 */
public class IeBinaryStateInformation extends InformationElement {

    private final int value;

    /**
     * Creates a BSI (binary state information) information element.
     * 
     * @param value
     *            the bits of value represent the binary states. The first binary state is the LSB (least significant
     *            bit) of value.
     */
    public IeBinaryStateInformation(int value) {

        this.value = value;
    }

    IeBinaryStateInformation(DataInputStream is) throws IOException {
        value = is.readInt();
    }

    @Override
    int encode(byte[] buffer, int i) {
        buffer[i++] = (byte) (value >> 24);
        buffer[i++] = (byte) (value >> 16);
        buffer[i++] = (byte) (value >> 8);
        buffer[i] = (byte) value;
        return 4;
    }

    public int getValue() {
        return value;
    }

    /**
     * Returns true if the bit at the given position is 1 and false otherwise.
     * 
     * @param position
     *            the position in the bit string. Range: 1-32. Bit1 is the LSB and bit32 the MSB of the value returned
     *            by <code>getValue()</code>.
     * @return true if the bit at the given position is 1 and false otherwise.
     */
    public boolean getBinaryState(int position) {
        if (position < 1 || position > 32) {
            throw new IllegalArgumentException("Position out of bound. Should be between 1 and 32.");
        }
        return (((value >> (position - 1)) & 0x01) == 0x01);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Integer.toHexString(value));
        while (sb.length() < 8) {
            sb.insert(0, '0'); // pad with leading zero if needed
        }
        return "BinaryStateInformation (first bit = LSB): " + sb.toString();
    }

}
