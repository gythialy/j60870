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
package org.openmuc.j60870;

import java.io.IOException;

abstract class CommonBuilder<T extends CommonBuilder<T, C>, C> {

    final ConnectionSettings settings = new ConnectionSettings();

    /**
     * Access the casted this reference.
     *
     * @return the reference of the object.
     */
    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }

    /**
     * Sets the length of the Cause Of Transmission (COT) field of the ASDU. Allowed values are 1 or 2. The default is
     * 2.
     *
     * @param length the length of the Cause Of Transmission field
     * @return this builder
     */
    public T setCotFieldLength(int length) {
        if (length != 1 && length != 2) {
            throw new IllegalArgumentException("invalid length");
        }
        settings.setCotFieldLength(length);
        return self();
    }

    /**
     * Sets the length of the Common Address (CA) field of the ASDU. Allowed values are 1 or 2. The default is 2.
     *
     * @param length the length of the Common Address (CA) field
     * @return this builder
     */
    public T setCommonAddressFieldLength(int length) {
        if (length != 1 && length != 2) {
            throw new IllegalArgumentException("invalid length");
        }
        settings.setCommonAddressFieldLength(length);
        return self();
    }

    /**
     * Sets the length of the Information Object Address (IOA) field of the ASDU. Allowed values are 1, 2 or 3. The
     * default is 3.
     *
     * @param length the length of the Information Object Address field
     * @return this builder
     */
    public T setIoaFieldLength(int length) {
        if (length < 1 || length > 3) {
            throw new IllegalArgumentException("invalid length: " + length);
        }
        settings.setIoaFieldLength(length);
        return self();
    }

    /**
     * Sets the maximum time in ms that no acknowledgement has been received (for I-Frames or Test-Frames) before
     * actively closing the connection. This timeout is called t1 by the standard. Default is 15s, minimum is 1s,
     * maximum is 255s.
     *
     * @param time the maximum time in ms that no acknowledgement has been received before actively closing the
     *             connection.
     * @return this builder
     */
    public T setMaxTimeNoAckReceived(int time) {
        checkTimeRange(time);
        settings.setMaxTimeNoAckReceived(time);
        return self();
    }

    /**
     * Sets the maximum time in ms before confirming received messages that have not yet been acknowledged using an S
     * format APDU. This timeout is called t2 by the standard. Default is 10s, minimum is 1s, maximum is 255s.
     *
     * @param time the maximum time in ms before confirming received messages that have not yet been acknowledged using
     *             an S format APDU.
     * @return this builder
     */
    public T setMaxTimeNoAckSent(int time) {
        checkTimeRange(time);
        settings.setMaxTimeNoAckSent(time);
        return self();
    }

    private void checkTimeRange(int time) {
        if (time < 1000 || time > 255000) {
            throw new IllegalArgumentException(
                    "invalid timeout: " + time + ", time must be between 1000ms and 255000ms");
        }
    }

    /**
     * Sets the maximum time in ms that the connection may be idle before sending a test frame. This timeout is called
     * t3 by the standard. Default is 20s, minimum is 1s, maximum is 172800s (48h).
     *
     * @param time the maximum time in ms that the connection may be idle before sending a test frame.
     * @return this builder
     */
    public T setMaxIdleTime(int time) {
        if (time < 1000 || time > 172800000) {
            throw new IllegalArgumentException(
                    "invalid timeout: " + time + ", time must be between 1000ms and 172800000ms");
        }
        settings.setMaxIdleTime(time);
        return self();
    }

    /**
     * Sets the number of maximum difference send sequence number to send acknowledge variable before the connection
     * will automatically closed. This parameter is called k by the standard. Default is 12, minimum is 1, maximum is
     * 32767.
     *
     * @param maxNum the maximum number of sequentially numbered I format APDUs that the DTE may have outstanding
     * @return this builder
     */
    public T setMaxNumOfOutstandingIPdus(int maxNum) {
        if (maxNum < 1 || maxNum > 32767) {
            throw new IllegalArgumentException("invalid maxNum: " + maxNum + ", must be a value between 1 and 32767");
        }
        settings.setMaxNumOfOutstandingIPdus(maxNum);
        return self();
    }

    /**
     * Sets the number of unacknowledged I format APDUs received before the connection will automatically send an S
     * format APDU to confirm them. This parameter is called w by the standard. Default is 8, minimum is 1, maximum is
     * 32767.
     *
     * @param maxNum the number of unacknowledged I format APDUs received before the connection will automatically send an
     *               S format APDU to confirm them.
     * @return this builder
     */
    public T setMaxUnconfirmedIPdusReceived(int maxNum) {
        if (maxNum < 1 || maxNum > 32767) {
            throw new IllegalArgumentException("invalid maxNum: " + maxNum + ", must be a value between 1 and 32767");
        }
        settings.setMaxUnconfirmedIPdusReceived(maxNum);
        return self();
    }

    /**
     * Sets SO_TIMEOUT with the specified timeout, in milliseconds.
     *
     * @param time the timeout in milliseconds. Default is 5 s, minimum 100 ms.
     * @return this builder
     */
    public T setMessageFragmentTimeout(int time) {
        if (time < 100) {
            throw new IllegalArgumentException("invalid timeout: " + time + ", time must be bigger then 100ms");
        }
        settings.setMessageFragmentTimeout(time);
        return self();
    }

    public T useSharedThreadPool() {
        settings.setUseSharedThreadPool(true);
        return self();
    }

    public abstract C build() throws IOException;

}
