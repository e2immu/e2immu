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

package org.e2immu.analyser.parser.failing.testexample;


import java.util.List;

// simpler version of 2, other problem in if sequence
public class FormatterSimplified_4 {

    interface OutputElement {
    }

    record Space() implements OutputElement {
        static final Space NEWLINE = new Space();

        ElementarySpace elementarySpace() {
            return null;
        }
    }

    static class ElementarySpace implements OutputElement {
        static final ElementarySpace NICE = new ElementarySpace();
    }

    record Symbol(String symbol, Space left, Space right, String constant) implements OutputElement {
    }

    boolean forward(List<OutputElement> list) {
        OutputElement outputElement;
        int pos = 0;
        int end = list.size();

        ElementarySpace lastOneWasSpace = ElementarySpace.NICE; // used to avoid writing double spaces
        while (pos < end && ((outputElement = list.get(pos)) != Space.NEWLINE)) {
            if (outputElement instanceof Symbol symbol) {
                lastOneWasSpace = combine(lastOneWasSpace, symbol.left().elementarySpace());
            } // $4$4.0.0:M
            if (outputElement instanceof Space space) {
                lastOneWasSpace = combine(lastOneWasSpace, space.elementarySpace()); // here we access $4$4.0.0:M
            }
            ++pos;
        }
        return false;
    }

    private ElementarySpace combine(ElementarySpace lastOneWasSpace, ElementarySpace elementarySpace) {
        return lastOneWasSpace == null ? elementarySpace: lastOneWasSpace;
    }
}
