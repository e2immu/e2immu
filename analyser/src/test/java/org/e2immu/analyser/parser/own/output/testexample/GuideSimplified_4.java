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

package org.e2immu.analyser.parser.own.output.testexample;

import java.util.concurrent.atomic.AtomicInteger;

/*
 second variant on GS_2

 - remove either start() or trace(), and all is fine.
  (so removing start with position.msg is still OK)
 - remove .msg in trace, and all is fine as well
 */

public record GuideSimplified_4(int index, Position position) {
    private static final AtomicInteger generator = new AtomicInteger();

    public enum Position {
        START("S"), MID(""), END("E");

        private final String msg;

        Position(String msg) {
            this.msg = msg;
        }
    }

    public static class GuideGenerator {
        public final int index;

        private GuideGenerator() {
            index = generator.incrementAndGet();
        }

        public GuideSimplified_4 start() {
            return new GuideSimplified_4(index, Position.START);
        }
    }

    public String trace() {
        return "/*" + position.msg + "*/";
    }
}
