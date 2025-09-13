/*
 * Copyright 2014-2024 Fraunhofer ISE
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

import junitparams.JUnitParamsRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openmuc.j60870.Server.Builder;

@RunWith(JUnitParamsRunner.class)
public class CommonBuilderTest {
    private Builder builder;

    @Before
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

    @Test(expected = IllegalArgumentException.class)
    public void testT2BiggerThenT1() {
        setTime(15000, 16000, 20000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testT3SmallerThenT1() {
        setTime(15000, 10000, 14000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testT1toSmall() {
        setTime(Integer.MIN_VALUE, 10000, 20000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testT1toBig() {
        setTime(Integer.MAX_VALUE, 10000, 20000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testT2toSmall() {
        setTime(15000, Integer.MIN_VALUE, 20000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testT2toBig() {
        setTime(15000, Integer.MAX_VALUE, 20000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testT3toSmall() {
        setTime(15000, 10000, Integer.MIN_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testT3toBig() {
        setTime(15000, 10000, Integer.MAX_VALUE);
    }

    @Test
    public void testTimeOK() {
        setTime(15000, 10000, 20000);
    }
}
