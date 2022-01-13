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

import org.e2immu.annotation.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/*
 small variant on GS_4: position().msg instead of position.
 causes/caused a number of crashes
 */

public record GuideSimplified_5(int index, Position position) {
    private static final AtomicInteger generator = new AtomicInteger();

    public GuideSimplified_5 {
        assert position != null;
    }

    public enum Position {
        START("S"), MID(""), END("E");

        @Nullable
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

        public GuideSimplified_5 start() {
            return new GuideSimplified_5(index, Position.START);
        }
    }

    public String trace() {
        return "/*" + position().msg + "*/";
    }
}
