/**
 * Copyright 2014-19 Fraunhofer ISE
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
 */
package org.openmuc.j60870.internal.cli;

public abstract class CliParameter {

    final String name;
    final String description;
    final boolean optional;
    boolean selected;

    CliParameter(CliParameterBuilder builder) {
        name = builder.name;
        description = builder.description;
        optional = builder.optional;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the optional
     */
    public boolean isOptional() {
        return optional;
    }

    public boolean isSelected() {
        return selected;
    }

    abstract int parse(String[] args, int i) throws CliParseException;

    abstract int appendSynopsis(StringBuilder sb);

    abstract void appendDescription(StringBuilder sb);

}
