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
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a regulating step command (RCO) information element.
 */
public class IeRegulatingStepCommand extends IeAbstractQualifierOfCommand {

    /**
     * Create a Regulating Step Command Information Element.
     *
     * @param commandState the command state
     * @param qualifier    the qualifier
     * @param select       true if select, false if execute
     */
    public IeRegulatingStepCommand(StepCommandState commandState, int qualifier, boolean select) {
        super(qualifier, select);

        value |= commandState.getId();
    }

    IeRegulatingStepCommand(DataInputStream is) throws IOException {
        super(is);
    }

    public StepCommandState getCommandState() {
        return StepCommandState.getInstance(value & 0x03);
    }

    @Override
    public String toString() {
        return "Regulating step command state: " + getCommandState() + ", " + super.toString();
    }

    public enum StepCommandState {
        NOT_PERMITTED_A(0),
        NEXT_STEP_LOWER(1),
        NEXT_STEP_HIGHER(2),
        NOT_PERMITTED_B(3);

        private static final Map<Integer, StepCommandState> idMap = new HashMap<>();

        static {
            for (StepCommandState enumInstance : StepCommandState.values()) {
                if (idMap.put(enumInstance.getId(), enumInstance) != null) {
                    throw new IllegalArgumentException("duplicate ID: " + enumInstance.getId());
                }
            }
        }

        private final int id;

        private StepCommandState(int id) {
            this.id = id;
        }

        /**
         * Returns the StepCommandState that corresponds to the given ID. Returns <code>null</code> if no
         * StepCommandState with the given ID exists.
         *
         * @param id the ID
         * @return the StepCommandState that corresponds to the given ID
         */
        public static StepCommandState getInstance(int id) {
            return idMap.get(id);
        }

        /**
         * Returns the ID of this StepCommandState.
         *
         * @return the ID
         */
        public int getId() {
            return id;
        }

    }

}
