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

import java.util.concurrent.atomic.AtomicReference;

public final class LoggerFactory {

    public interface Provider {
        LoggerInterface getLogger(String name);
    }

    private static final AtomicReference<Provider> providerRef = new AtomicReference<>(new PlainLoggerProvider());

    private LoggerFactory() {}

    public static LoggerInterface getLogger(String name) {
        return providerRef.get().getLogger(name);
    }

    public static void setProvider(Provider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        providerRef.set(provider);
    }
}
