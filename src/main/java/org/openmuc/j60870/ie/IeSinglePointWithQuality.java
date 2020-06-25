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
 * Represents a single-point information with quality descriptor (SIQ) information element.
 */
public class IeSinglePointWithQuality extends IeAbstractQuality {

    public IeSinglePointWithQuality(boolean on, boolean blocked, boolean substituted, boolean notTopical,
                                    boolean invalid) {
        super(blocked, substituted, notTopical, invalid);

        if (on) {
            value |= 0x01;
        }
    }

    IeSinglePointWithQuality(DataInputStream is) throws IOException {
        super(is);
    }

    public boolean isOn() {
        return (value & 0x01) == 0x01;
    }

    @Override
    public String toString() {
        return "Single Point, is on: " + isOn() + ", " + super.toString();
    }
}
