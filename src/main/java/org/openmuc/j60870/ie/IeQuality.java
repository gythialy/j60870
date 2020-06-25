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
 * Represents a quality descriptor (QDS) information element.
 */
public class IeQuality extends IeAbstractQuality {

    public IeQuality(boolean overflow, boolean blocked, boolean substituted, boolean notTopical, boolean invalid) {
        super(blocked, substituted, notTopical, invalid);

        if (overflow) {
            value |= 0x01;
        }
    }

    IeQuality(DataInputStream is) throws IOException {
        super(is);
    }

    public boolean isOverflow() {
        return (value & 0x01) == 0x01;
    }

    @Override
    public String toString() {
        return "Quality, overflow: " + isOverflow() + ", " + super.toString();
    }
}
