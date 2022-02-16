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

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotModified;

import java.util.List;
import java.util.Stack;
import java.util.function.Function;

// causes an error with StaticallyAssignedVariables -> fixed
// cyclic dependency: fwdInfo has no LV because ForwardInfo not yet IMMUTABLE,
// ForwardInfo waiting for modification on guide in apply before any idea on IMMUTABLE

public class FormatterSimplified_9 {

    // no annotation -> Mutable
    interface OutputElement {

    }

    // no annotation -> Mutable
    interface Guide extends OutputElement {
        int index();
    }

    @E2Container // Guide is mutable, but also transparent in ForwardInfo
    record ForwardInfo(int pos, int chars, String string, Guide guide, boolean symbol) {
        public boolean isGuide() {
            return string == null;
        }
    }

    @E2Container // because ForwardInfo is @E2Container
    record GuideOnStack(ForwardInfo forwardInfo) {

    }

    static Boolean lookAhead(List<OutputElement> list) {
        forward(list, forwardInfo -> {
            ForwardInfo fwdInfo = new Stack<GuideOnStack>().peek().forwardInfo;
            assert fwdInfo != null && fwdInfo.guide.index() == 9;
            return list.get(forwardInfo.pos) instanceof Guide;
        });
        return null;
    }

    static void forward(List<OutputElement> list, Function<ForwardInfo, Boolean> writer) {
    }

}
