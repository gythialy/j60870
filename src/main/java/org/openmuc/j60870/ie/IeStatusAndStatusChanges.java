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
 * Represents a status and status change detection (SCD) information element.
 */
public class IeStatusAndStatusChanges extends InformationElement {

    private final int value;

    /**
     * Creates a SCD (status and status change detection) information element.
     *
     * @param value the bits of value represent the status and status changed bits. Bit1 (the least significant bit) of
     *              value represents the first status changed detection bit. Bit17 of value represents the first status
     *              bit.
     */
    public IeStatusAndStatusChanges(int value) {

        this.value = value;
    }

    IeStatusAndStatusChanges(DataInputStream is) throws IOException {
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
     * Returns true if the status at the given position is ON(1) and false otherwise.
     *
     * @param position the position in the status bitstring. Range: 1-16. Status 1 is bit 17 and status 16 is bit 32 of the
     *                 value returned by <code>getValue()</code>.
     * @return true if the status at the given position is ON(1) and false otherwise.
     */
    public boolean getStatus(int position) {
        if (position < 1 || position > 16) {
            throw new IllegalArgumentException("Position out of bound. Should be between 1 and 16.");
        }
        return (((value >> (position - 17)) & 0x01) == 0x01);
    }

    /**
     * Returns true if the status at the given position has changed and false otherwise.
     *
     * @param position the position in the status changed bitstring. Range: 1-16. Status changed 1 is bit 1 and status 16 is
     *                 bit 16 of the value returned by <code>getValue()</code>.
     * @return true if the status at the given position has changed and false otherwise.
     */
    public boolean hasStatusChanged(int position) {
        if (position < 1 || position > 16) {
            throw new IllegalArgumentException("Position out of bound. Should be between 1 and 16.");
        }
        return (((value >> (position - 1)) & 0x01) == 0x01);
    }

    @Override
    public String toString() {
        StringBuilder sb1 = new StringBuilder();
        sb1.append(Integer.toHexString(value >>> 16));
        while (sb1.length() < 4) {
            sb1.insert(0, '0'); // pad with leading zero if needed
        }

        StringBuilder sb2 = new StringBuilder();
        sb2.append(Integer.toHexString(value & 0xffff));
        while (sb2.length() < 4) {
            sb2.insert(0, '0'); // pad with leading zero if needed
        }

        return "Status and status changes (first bit = LSB), states: " + sb1.toString() + ", state changes: "
                + sb2.toString();
    }

}
