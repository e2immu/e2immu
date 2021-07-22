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


public class FormatterSimplified_8 {

    record ForwardInfo(int pos, int chars, String string, Guide guide, boolean symbol) {
        public boolean isGuide() {
            return string == null;
        }
    }

    record GuideOnStack(ForwardInfo forwardInfo) {
    }

    interface OutputElement {
    }

    interface Guide extends OutputElement {
        int index();
    }

    Boolean lookAhead(ForwardInfo forwardInfo) {
        if (!forwardInfo.symbol && !forwardInfo.isGuide()) {
            assert forwardInfo.guide == null;
            return true;
        }
        assert new Stack<GuideOnStack>().peek().forwardInfo.guide.index() == 9;
        return false;
    }
}
