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
package org.openmuc.j60870.logging;

public interface LoggerInterface {

    boolean isTraceEnabled();

    boolean isDebugEnabled();

    void trace(String msg);

    void trace(String msg, Object... args);

    void debug(String msg);

    void debug(String msg, Object... args);

    void info(String msg);

    void info(String msg, Object... args);

    void warn(String msg);

    void warn(String msg, Object... args);

    void error(String msg);

    void error(String msg, Object... args);

    void raw(byte[] rawData);

    void raw(String msg, byte[] rawData);
}
