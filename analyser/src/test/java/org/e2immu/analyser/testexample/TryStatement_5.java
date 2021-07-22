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



import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

// as part of Formatter and the testing of Test_Output_03_Formatter
// in this example, 'e' being used 2x is not an issue; in FormatterSimplified_6, it is/was
public class TryStatement_5 {

    interface Writer {
        void write(int i) throws IOException;
    }

    static void writeLine(List<String> list, Writer writer, int start, int end) throws IOException {
        try {
            forward(list, forwardInfo -> {
                try {
                    if (forwardInfo == null) {
                        writer.write(start);
                        return start == end;
                    }
                    return forwardInfo.length() == end;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw new IOException(e);
        }
    }

    static void forward(List<String> list, Predicate<String> predicate) {

    }
}
