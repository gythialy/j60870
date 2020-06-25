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
import java.text.MessageFormat;

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
        value = is.readUnsignedByte();
    }

    @Override
    int encode(byte[] buffer, int i) {
        buffer[i] = (byte) value;
        return 1;
    }

    public boolean isBlocked() {
        return hasBitSet(0x10);
    }

    public boolean isSubstituted() {
        return hasBitSet(0x20);
    }

    public boolean isNotTopical() {
        return hasBitSet(0x40);
    }

    public boolean isInvalid() {
        return hasBitSet(0x80);
    }

    private boolean hasBitSet(int mask) {
        return (value & mask) == mask;
    }

    @Override
    public String toString() {
        return MessageFormat.format("blocked: {0}, substituted: {1}, not topical: {2}, invalid: {3}", isBlocked(),
                isSubstituted(), isNotTopical(), isInvalid());
    }
}
