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

public class FlagCliParameter extends CliParameter {

    FlagCliParameter(CliParameterBuilder builder) {
        super(builder);
    }

    @Override
    int appendSynopsis(StringBuilder sb) {
        int length = 0;
        if (optional) {
            sb.append("[");
            length++;
        }
        sb.append(name);
        length += name.length();
        if (optional) {
            sb.append("]");
            length++;
        }
        return length;
    }

    @Override
    void appendDescription(StringBuilder sb) {
        sb.append("\t").append(name).append("\n\t    ").append(description);
    }

    @Override
    int parse(String[] args, int i) throws CliParseException {
        selected = true;
        return 1;
    }

}
