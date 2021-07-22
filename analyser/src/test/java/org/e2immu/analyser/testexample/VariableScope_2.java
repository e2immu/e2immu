/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.testexample;


import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class VariableScope_2 {
    // only difference between VS_4 and VS_2 is the redundant = null on ioe (statement 0)
    // similar test in TryStatement_6

    @Nullable
    static IOException writeLine(@NotNull List<String> list, @NotNull Writer writer) {
        IOException ioe;
        try {
            for (String outputElement : list) {
                writer.write(outputElement);
            }
            return null;
        } catch (IOException e) {
            ioe = e;
        }
        return ioe; // should not contain a reference to 'e'
    }
}
