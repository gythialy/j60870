/*
 * Copyright 2014-16 Fraunhofer ISE
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

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Represents a seven octet binary time (CP56Time2a) information element.
 * 
 * @author Stefan Feuerhahn
 * 
 */
public class IeTime56 extends InformationElement {

    private final byte[] value = new byte[7];

    /**
     * Creates a Time56 instance using the given timestamp and time zone.
     * 
     * @param timestamp
     *            the timestamp that shall be used to calculate Time56
     * @param timeZone
     *            the time zone to use
     * @param invalid
     *            true if the time shall be marked as invalid
     */
    public IeTime56(long timestamp, TimeZone timeZone, boolean invalid) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(timestamp);

        int ms = calendar.get(Calendar.MILLISECOND) + 1000 * calendar.get(Calendar.SECOND);

        value[0] = (byte) ms;
        value[1] = (byte) (ms >> 8);
        value[2] = (byte) calendar.get(Calendar.MINUTE);
        if (invalid) {
            value[2] |= 0x80;
        }
        value[3] = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        if (calendar.getTimeZone().inDaylightTime(calendar.getTime())) {
            value[3] |= 0x80;
        }
        value[4] = (byte) (calendar.get(Calendar.DAY_OF_MONTH)
                + ((((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1) << 5));
        value[5] = (byte) (calendar.get(Calendar.MONTH) + 1);
        value[6] = (byte) (calendar.get(Calendar.YEAR) % 100);
    }

    /**
     * Creates a valid Time56 instance using the given timestamp and the default time zone.
     * 
     * @param timestamp
     *            the timestamp that shall be used to calculate Time56
     */
    public IeTime56(long timestamp) {
        this(timestamp, TimeZone.getDefault(), false);
    }

    public IeTime56(byte[] value) {
        for (int i = 0; i < 7; i++) {
            this.value[i] = value[i];
        }
    }

    IeTime56(DataInputStream is) throws IOException {
        is.readFully(value);
    }

    @Override
    int encode(byte[] buffer, int i) {
        System.arraycopy(value, 0, buffer, i, 7);
        return 7;
    }

    /**
     * Returns the timestamp in ms equivalent to this Time56 instance.
     * 
     * Note that Time56 does not store the century of the date. Therefore you have to pass the earliest possible year of
     * the Time56 instance. Say the year stored by Time56 is 10. From this information alone it is not possible to tell
     * whether the real year is 1910 or 2010 or 2110. If you pass 1970 as the start of century, then this function will
     * know that the year of the given date lies between 1970 and 2069 and can therefore calculate that the correct date
     * is 2010.
     * 
     * @param startOfCentury
     *            The timestamp will
     * @param timeZone
     *            the timezone that shall be used to calculate the timestamp.
     * @return the timestamp in ms equivalent to this Time56 instance
     */
    public long getTimestamp(int startOfCentury, TimeZone timeZone) {

        int century = startOfCentury / 100 * 100;
        if (value[6] < (startOfCentury % 100)) {
            century += 100;
        }

        Calendar calendar = Calendar.getInstance(timeZone);

        calendar.set(getYear() + century, getMonth() - 1, getDayOfMonth(), getHour(), getMinute(), getSecond());
        calendar.set(Calendar.MILLISECOND, getMillisecond());

        return calendar.getTimeInMillis();
    }

    /**
     * Returns the timestamp in ms equivalent to this Time56 instance. The default time zone is used.
     * 
     * Note that Time56 does not store the century of the date. Therefore you have to pass the earliest possible year of
     * the Time56 instance. Say the year stored by Time56 is 10. From this information alone it is not possible to tell
     * whether the real year is 1910 or 2010 or 2110. If you pass 1970 as the start of century, then this function will
     * know that the year of the given date lies between 1970 and 2069 and can therefore calculate that the correct date
     * is 2010.
     * 
     * @param startOfCentury
     *            The timestamp will
     * @return the timestamp in ms equivalent to this Time56 instance
     */
    public long getTimestamp(int startOfCentury) {
        return getTimestamp(startOfCentury, TimeZone.getDefault());
    }

    /**
     * Returns the timestamp in ms equivalent to this Time56 instance. Assumes that the given date is between 1970 and
     * 2070. The default time zone is used.
     * 
     * @return the timestamp in ms equivalent to this Time56 instance
     */
    public long getTimestamp() {
        return getTimestamp(1970, TimeZone.getDefault());
    }

    /**
     * Returns the millisecond of the second. Returned values can range from 0 to 999.
     * 
     * @return the millisecond of the second
     */
    public int getMillisecond() {
        return (((value[0] & 0xff) + ((value[1] & 0xff) << 8))) % 1000;
    }

    /**
     * Returns the second of the minute. Returned values can range from 0 to 59.
     * 
     * @return the second of the minute
     */
    public int getSecond() {
        return (((value[0] & 0xff) + ((value[1] & 0xff) << 8))) / 1000;
    }

    /**
     * Returns the minute of the hour. Returned values can range from 0 to 59.
     * 
     * @return the minute of the hour
     */
    public int getMinute() {
        return value[2] & 0x3f;
    }

    /**
     * Returns the hour of the day. Returned values can range from 0 to 23.
     * 
     * @return the hour of the day
     */
    public int getHour() {
        return value[3] & 0x1f;
    }

    /**
     * Returns the day of the week. Returned values can range from 1 (Monday) to 7 (Sunday).
     * 
     * @return the day of the week
     */
    public int getDayOfWeek() {
        return (value[4] & 0xe0) >> 5;
    }

    /**
     * Returns the day of the month. Returned values can range from 1 to 31.
     * 
     * @return the day of the month
     */
    public int getDayOfMonth() {
        return value[4] & 0x1f;
    }

    /**
     * Returns the month of the year. Returned values can range from 1 (January) to 12 (December).
     * 
     * @return the month of the year
     */
    public int getMonth() {
        return value[5] & 0x0f;
    }

    /**
     * Returns the year in the century. Returned values can range from 0 to 99. Note that the century is not stored by
     * Time56.
     * 
     * @return the number of years in the century
     */
    public int getYear() {
        return value[6] & 0x7f;
    }

    /**
     * Returns true if summer time (i.e. Daylight Saving Time (DST)) is active.
     * 
     * @return true if summer time (i.e. Daylight Saving Time (DST)) is active
     */
    public boolean isSummerTime() {
        return (value[3] & 0x80) == 0x80;
    }

    /**
     * Return true if time value is invalid.
     * 
     * @return true if time value is invalid
     */
    public boolean isInvalid() {
        return (value[2] & 0x80) == 0x80;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Time56: ");
        builder.append(String.format("%02d", getDayOfMonth()));
        builder.append("-");
        builder.append(String.format("%02d", getMonth()));
        builder.append("-");
        builder.append(String.format("%02d", getYear()));
        builder.append(" ");
        builder.append(String.format("%02d", getHour()));
        builder.append(":");
        builder.append(String.format("%02d", getMinute()));
        builder.append(":");
        builder.append(String.format("%02d", getSecond()));
        builder.append(":");
        builder.append(String.format("%03d", getMillisecond()));

        if (isSummerTime()) {
            builder.append(" DST");
        }

        if (isInvalid()) {
            builder.append(", invalid");
        }

        return builder.toString();
    }
}
