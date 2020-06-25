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
 * Represents a qualifier of counter interrogation (QCC) information element.
 */
public class IeQualifierOfCounterInterrogation extends InformationElement {

    private final int request;
    private final int freeze;

    public IeQualifierOfCounterInterrogation(int request, int freeze) {
        this.request = request;
        this.freeze = freeze;
    }

    IeQualifierOfCounterInterrogation(DataInputStream is) throws IOException {
        int b1 = (is.readByte() & 0xff);
        request = b1 & 0x3f;
        freeze = (b1 >> 6) & 0x03;
    }

    @Override
    int encode(byte[] buffer, int i) {
        buffer[i] = (byte) (request | (freeze << 6));
        return 1;
    }

    public int getRequest() {
        return request;
    }

    public int getFreeze() {
        return freeze;
    }

    @Override
    public String toString() {
        return "Qualifier of counter interrogation, request: " + request + ", freeze: " + freeze;
    }
}
