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

import java.util.List;
import java.util.Stack;
import java.util.function.Function;


public class FormatterSimplified_6 {

    record ForwardInfo(int pos, int chars, String string, Split before, Guide guide, boolean symbol) {
        public boolean isGuide() {
            return string == null;
        }
    }
    record CurrentExceeds(ForwardInfo current, ForwardInfo exceeds) {
    }

    record GuideOnStack(ForwardInfo forwardInfo) {
    }

    interface OutputElement {
    }

    interface Split extends OutputElement {
    }

    interface Guide extends OutputElement {
        int index();
    }

    interface Space extends OutputElement {
    }

    /**
     * Tries to fit all the remaining output elements on a single line.
     * If that works, then the last element is returned.
     * If a newline is encountered, then the newline is returned.
     * If not everything fits on a line, and the guides allow it, we return a guide position,
     * which can be START, MID or END
     */

    CurrentExceeds lookAhead(List<OutputElement> list) {
        Stack<GuideOnStack> startOfGuides = new Stack<>();
        forward(list, forwardInfo -> {
            if (!forwardInfo.symbol && !forwardInfo.isGuide()) {
                assert forwardInfo.guide == null;
                return true;
            }
            assert startOfGuides.peek().forwardInfo.guide.index() == 9;
            return list.get(forwardInfo.pos) instanceof Space;
        });
        return null;
    }

    static void forward(List<OutputElement> list, Function<ForwardInfo, Boolean> writer) {
    }

}
