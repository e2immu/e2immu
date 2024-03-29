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

package org.e2immu.analyser.resolver.testexample;

import java.util.*;
import java.util.stream.Stream;

import static org.e2immu.analyser.resolver.testexample.importhelper.MultiLevel.Effective.*;

public class FieldAccess_1 {

    interface Analyser {}

    abstract static class AbstractAnalyser implements Analyser {
        public final String k = "3";
        protected final List<String> messages = new ArrayList<>();

        public List<String> getMessages() {
            return messages;
        }
    }

    abstract static class ParameterAnalyser extends AbstractAnalyser {
        public final String s = "3";

        public Stream<String> streamMessages() {
            return messages.stream();
        }
    }

    public static class ComputedParameterAnalyser extends ParameterAnalyser {
        public final String t = "3";

        public void method() {
            messages.add("3: "+E1);
        }
    }
}
