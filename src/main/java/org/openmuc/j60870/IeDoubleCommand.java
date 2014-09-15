/*
 * Copyright 2014 Fraunhofer ISE
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

/**
 * Represents a double command (DCO) information element.
 *
 * @author Stefan Feuerhahn
 */
public class IeDoubleCommand extends IeAbstractQualifierOfCommand {

    public enum DoubleCommandState {
        NOT_PERMITTED_A(0), OFF(1), ON(2), NOT_PERMITTED_B(3);

        private final int code;

        private DoubleCommandState(int code) {
            this.code = code;
        }

        /**
         * Returns the code associated with this DoubleCommandState.
         *
         * @return the code associated with this DoubleCommandState.
         */
        public int getCode() {
            return code;
        }

        /**
         * Returns the DoubleCommandState for the given code. Returns null if the code is unknown.
         *
         * @param code the code of the DoubleCommandState
         * @return the DoubleCommandState. Returns null if the code is unknown.
         */
        public static DoubleCommandState createDoubleCommandState(int code) {
            switch (code) {
            case 0:
                return NOT_PERMITTED_A;
            case 1:
                return OFF;
            case 2:
                return ON;
            case 3:
                return NOT_PERMITTED_B;
            default:
                return null;
            }
        }

    }

    /**
     * Create the Double Command Information Element.
     *
     * @param commandState the command state
     * @param qualifier    the qualifier
     * @param select       true if select, false if execute
     */
    public IeDoubleCommand(DoubleCommandState commandState, int qualifier, boolean select) {
        super(qualifier, select);

        value |= commandState.getCode();
    }

    IeDoubleCommand(DataInputStream is) throws IOException {
        super(is);
    }

    public DoubleCommandState getCommandState() {
        return DoubleCommandState.createDoubleCommandState(value & 0x03);
    }

    @Override
    public String toString() {
        return "Double Command state: " + getCommandState() + ", " + super.toString();
    }

}
