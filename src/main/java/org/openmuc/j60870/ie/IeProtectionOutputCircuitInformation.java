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
 * Represents an output circuit information of protection equipment (OCI) information element.
 */
public class IeProtectionOutputCircuitInformation extends InformationElement {

    private int value;

    public IeProtectionOutputCircuitInformation(boolean generalCommand, boolean commandToL1, boolean commandToL2,
                                                boolean commandToL3) {

        value = 0;

        if (generalCommand) {
            value |= 0x01;
        }
        if (commandToL1) {
            value |= 0x02;
        }
        if (commandToL2) {
            value |= 0x04;
        }
        if (commandToL3) {
            value |= 0x08;
        }

    }

    IeProtectionOutputCircuitInformation(DataInputStream is) throws IOException {
        value = (is.readByte() & 0xff);
    }

    @Override
    int encode(byte[] buffer, int i) {
        buffer[i] = (byte) value;
        return 1;
    }

    public boolean isGeneralCommand() {
        return (value & 0x01) == 0x01;
    }

    public boolean isCommandToL1() {
        return (value & 0x02) == 0x02;
    }

    public boolean isCommandToL2() {
        return (value & 0x04) == 0x04;
    }

    public boolean isCommandToL3() {
        return (value & 0x08) == 0x08;
    }

    @Override
    public String toString() {
        return "Protection output circuit information, general command: " + isGeneralCommand() + ", command to L1: "
                + isCommandToL1() + ", command to L2: " + isCommandToL2() + ", command to L3: " + isCommandToL3();
    }

}
