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
 * Represents a start events of protection equipment (SPE) information element.
 */
public class IeProtectionStartEvent extends InformationElement {

    private int value;

    public IeProtectionStartEvent(boolean generalStart, boolean startOperationL1, boolean startOperationL2,
                                  boolean startOperationL3, boolean startOperationIe, boolean startReverseOperation) {

        value = 0;

        if (generalStart) {
            value |= 0x01;
        }
        if (startOperationL1) {
            value |= 0x02;
        }
        if (startOperationL2) {
            value |= 0x04;
        }
        if (startOperationL3) {
            value |= 0x08;
        }
        if (startOperationIe) {
            value |= 0x10;
        }
        if (startReverseOperation) {
            value |= 0x20;
        }
    }

    IeProtectionStartEvent(DataInputStream is) throws IOException {
        value = (is.readByte() & 0xff);
    }

    @Override
    int encode(byte[] buffer, int i) {
        buffer[i] = (byte) value;
        return 1;
    }

    public boolean isGeneralStart() {
        return (value & 0x01) == 0x01;
    }

    public boolean isStartOperationL1() {
        return (value & 0x02) == 0x02;
    }

    public boolean isStartOperationL2() {
        return (value & 0x04) == 0x04;
    }

    public boolean isStartOperationL3() {
        return (value & 0x08) == 0x08;
    }

    public boolean isStartOperationIe() {
        return (value & 0x10) == 0x10;
    }

    public boolean isStartReverseOperation() {
        return (value & 0x20) == 0x20;
    }

    @Override
    public String toString() {
        return "Protection start event, general start of operation: " + isGeneralStart() + ", start of operation L1: "
                + isStartOperationL1() + ", start of operation L2: " + isStartOperationL2()
                + ", start of operation L3: " + isStartOperationL3() + ", start of operation IE(earth current): "
                + isStartOperationIe() + ", start of operation in reverse direction: " + isStartReverseOperation();
    }

}
