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

abstract class IeAbstractQualifierOfCommand extends InformationElement {

    protected int value;

    IeAbstractQualifierOfCommand(int qualifier, boolean select) {

        if (qualifier < 0 || qualifier > 31) {
            throw new IllegalArgumentException("Qualifier is out of bound: " + qualifier);
        }

        value = qualifier << 2;

        if (select) {
            value |= 0x80;
        }

    }

    IeAbstractQualifierOfCommand(DataInputStream is) throws IOException {
        value = (is.readByte() & 0xff);
    }

    @Override
    int encode(byte[] buffer, int i) {
        buffer[i] = (byte) value;
        return 1;
    }

    /**
     * Returns true if the command selects and false if the command executes.
     *
     * @return true if the command selects and false if the command executes.
     */
    public boolean isSelect() {
        return (value & 0x80) == 0x80;
    }

    public int getQualifier() {
        return (value >> 2) & 0x1f;
    }

    @Override
    public String toString() {
        return "selected: " + isSelect() + ", qualifier: " + getQualifier();
    }

}
