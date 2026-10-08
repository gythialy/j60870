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

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.openmuc.j60870.internal.HexUtils;

public final class PlainLogger implements LoggerInterface {

    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yy HH:mm:ss.SSS", Locale.ROOT);

    // System property: -Dorg.openmuc.j60870.log.level=INFO|DEBUG|TRACE|WARN|ERROR|RAW
    private static final String LEVEL_PROPERTY = "org.openmuc.j60870.log.level";

    enum LogLevel {
        RAW,
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    // Global log level for this console logger implementation
    private static volatile LogLevel globalLevel = initGlobalLevel();

    private final String name;

    public PlainLogger(String name) {
        this.name = name;
    }

    // Called from factory or tests to change level programmatically
    static void setGlobalLevel(LogLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        globalLevel = level;
    }

    // Convenience for string-based configuration
    static synchronized void setGlobalLevel(String levelName) {
        globalLevel = parseLevel(levelName, globalLevel);
    }

    private static LogLevel initGlobalLevel() {
        String v = System.getProperty(LEVEL_PROPERTY);
        if (v == null || v.isEmpty()) {
            return LogLevel.INFO; // default if nothing configured
        }
        return parseLevel(v, LogLevel.INFO);
    }

    private static LogLevel parseLevel(String v, LogLevel fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            return LogLevel.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // do not throw, just fall back and optionally inform on stderr
            System.err.println(
                    "Invalid value for " + LEVEL_PROPERTY + ": \"" + v + "\". Using " + fallback + " instead.");

            return fallback;
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return isEnabled(LogLevel.TRACE);
    }

    @Override
    public boolean isDebugEnabled() {
        return isEnabled(LogLevel.DEBUG);
    }

    @Override
    public void trace(String msg) {
        log(LogLevel.TRACE, msg);
    }

    @Override
    public void trace(String msg, Object... args) {
        log(LogLevel.TRACE, msg, args);
    }

    @Override
    public void debug(String msg) {
        log(LogLevel.DEBUG, msg);
    }

    @Override
    public void debug(String msg, Object... args) {
        log(LogLevel.DEBUG, msg, args);
    }

    @Override
    public void info(String msg) {
        log(LogLevel.INFO, msg);
    }

    @Override
    public void info(String msg, Object... args) {
        log(LogLevel.INFO, msg, args);
    }

    @Override
    public void warn(String msg) {
        log(LogLevel.WARN, msg);
    }

    @Override
    public void warn(String msg, Object... args) {
        log(LogLevel.WARN, msg, args);
    }

    @Override
    public void error(String msg) {
        log(LogLevel.ERROR, msg);
    }

    @Override
    public void error(String msg, Object... args) {
        log(LogLevel.ERROR, msg, args);
    }

    @Override
    public void raw(byte[] rawData) {
        String hexRawData = HexUtils.bytesToHex(rawData);
        log(LogLevel.RAW, hexRawData);
    }

    @Override
    public void raw(String msg, byte[] rawData) {
        String hexRawData = HexUtils.bytesToHex(rawData);
        log(LogLevel.RAW, msg, hexRawData);
    }

    private boolean isEnabled(LogLevel check) {
        return check.ordinal() >= globalLevel.ordinal();
    }

    private void log(LogLevel lvl, String msg) {
        log(lvl, msg, (Object[]) null);
    }

    private void log(LogLevel lvl, String msg, Object... args) {
        if (!isEnabled(lvl)) {
            return;
        }

        String timestamp = LocalDateTime.now().format(TS_FORMATTER);
        String levelStr = padRight(lvl.name(), 5);
        String text = (args == null || args.length == 0) ? msg : format(msg, args);

        PrintStream out = (lvl == LogLevel.ERROR || lvl == LogLevel.WARN) ? System.err : System.out;
        out.println(timestamp + "  " + levelStr + " " + name + " - " + text);
        out.flush();
    }

    private String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        StringBuilder sb = new StringBuilder(width);
        sb.append(value);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private String format(String msg, Object... args) {
        if (msg == null || args == null || args.length == 0) {
            return msg;
        }
        StringBuilder sb = new StringBuilder();
        int start = 0;
        int argIndex = 0;
        int brace;
        while ((brace = msg.indexOf("{}", start)) != -1 && argIndex < args.length) {
            sb.append(msg, start, brace);
            sb.append(String.valueOf(args[argIndex++]));
            start = brace + 2;
        }
        sb.append(msg.substring(start));
        return sb.toString();
    }
}
