/*
 * Copyright 2014-2026 Fraunhofer ISE
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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmuc.j60870.Server.Builder;

public class CommonBuilderTest {
    private Builder builder;

    @BeforeEach
    public void init() {
        builder = Server.builder();
    }

    public void setTime(int t1, int t2, int t3) {
        System.out.println("t1=" + t1 + ", t2=" + t2 + ", t3=" + t3);
        try {
            builder.setMaxTimeNoAckReceived(t1).setMaxTimeNoAckSent(t2).setMaxIdleTime(t3);

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw e;
        }
    }

    @Test()
    public void testT2BiggerThenT1() {
        assertThrows(IllegalArgumentException.class, () -> setTime(15000, 16000, 20000));
    }

    @Test()
    public void testT3SmallerThenT1() {
        assertThrows(IllegalArgumentException.class, () -> setTime(15000, 10000, 14000));
    }

    @Test()
    public void testT1toSmall() {
        assertThrows(IllegalArgumentException.class, () -> setTime(Integer.MIN_VALUE, 10000, 20000));
    }

    @Test()
    public void testT1toBig() {
        assertThrows(IllegalArgumentException.class, () -> setTime(Integer.MAX_VALUE, 10000, 20000));
    }

    @Test()
    public void testT2toSmall() {
        assertThrows(IllegalArgumentException.class, () -> setTime(15000, Integer.MIN_VALUE, 20000));
    }

    @Test()
    public void testT2toBig() {
        assertThrows(IllegalArgumentException.class, () -> setTime(15000, Integer.MAX_VALUE, 20000));
    }

    @Test()
    public void testT3toSmall() {
        assertThrows(IllegalArgumentException.class, () -> setTime(15000, 10000, Integer.MIN_VALUE));
    }

    @Test()
    public void testT3toBig() {
        assertThrows(IllegalArgumentException.class, () -> setTime(15000, 10000, Integer.MAX_VALUE));
    }

    @Test
    public void testTimeOK() {
        setTime(15000, 10000, 20000);
    }
}
