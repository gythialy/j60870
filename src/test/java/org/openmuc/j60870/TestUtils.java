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

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;
import org.openmuc.j60870.internal.HexUtils;

public class TestUtils {
    private static final int MIN_PORT_NUMBER = 2024;
    private static final int MAX_PORT_NUMBER = 65535;
    private static final Random RANDOM = new Random();
    private static final int MAX_TRIES = 50;

    public static byte[] STARTDT_ACT_BYTES = HexUtils.hexToBytes("680407000000");
    public static byte[] STARTDT_CON_BYTES = HexUtils.hexToBytes("68040B000000");
    public static byte[] STOPDT_ACT_BYTES = HexUtils.hexToBytes("680413000000");
    public static byte[] STOPDT_CON_BYTES = HexUtils.hexToBytes("680423000000");

    public static int getAvailablePort() {
        int port = MIN_PORT_NUMBER;
        boolean isAvailable = false;
        int tries = 0;

        while (!isAvailable && tries < MAX_TRIES) {
            port = RANDOM.nextInt((MAX_PORT_NUMBER - MIN_PORT_NUMBER) + 1) + MIN_PORT_NUMBER;
            try (ServerSocket ss = new ServerSocket(port); ) {
                ss.setReuseAddress(true);
                isAvailable = ss.isBound();
            } catch (IOException e) {
                // port is not available
            }
            tries++;
        }
        if (!isAvailable) {
            throw new RuntimeException("No available port found after " + MAX_TRIES + " tries");
        }

        return port;
    }
}
