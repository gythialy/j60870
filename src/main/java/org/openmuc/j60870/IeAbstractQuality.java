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

abstract class IeAbstractQuality extends InformationElement {

    protected int value;

    public IeAbstractQuality(boolean blocked, boolean substituted, boolean notTopical, boolean invalid) {

        value = 0;

        if (blocked) {
            value |= 0x10;
        }
        if (substituted) {
            value |= 0x20;
        }
        if (notTopical) {
            value |= 0x40;
        }
        if (invalid) {
            value |= 0x80;
        }

    }

    IeAbstractQuality(DataInputStream is) throws IOException {
        value = (is.readByte() & 0xff);
    }

    @Override
    int encode(byte[] buffer, int i) {
        buffer[i] = (byte) value;
        return 1;
    }

    public boolean isBlocked() {
        return (value & 0x10) == 0x10;
    }

    public boolean isSubstituted() {
        return (value & 0x20) == 0x20;
    }

    public boolean isNotTopical() {
        return (value & 0x40) == 0x40;
    }

    public boolean isInvalid() {
        return (value & 0x80) == 0x80;
    }

    @Override
    public String toString() {
        return "blocked: " + isBlocked() + ", substituted: " + isSubstituted() + ", not topical: " + isNotTopical()
                + ", invalid: " + isInvalid();
    }
}
