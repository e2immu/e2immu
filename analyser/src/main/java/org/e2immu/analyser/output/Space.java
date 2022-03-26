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

package org.e2immu.analyser.output;

import static org.e2immu.analyser.output.Split.*;

public enum Space implements OutputElement {
    // no space, do not split
    NONE(ElementarySpace.NONE, ElementarySpace.NONE, NEVER),

    // exactly one space needed, never split here (e.g. between class and class name); two ONEs collapse into one
    ONE(ElementarySpace.ONE, ElementarySpace.ONE, NEVER),

    // end of annotation; needs minimally one, but can be newline
    ONE_REQUIRED_EASY_SPLIT(ElementarySpace.ONE, ElementarySpace.ONE, EASY),

    // no space needed, split to make things nicer
    NO_SPACE_SPLIT_ALLOWED(ElementarySpace.NONE, ElementarySpace.NONE, EASY),

    RELAXED_NO_SPACE_SPLIT_ALLOWED(ElementarySpace.RELAXED_NONE, ElementarySpace.RELAXED_NONE, EASY),

    ONE_IS_NICE_EASY_SPLIT(ElementarySpace.RELAXED_NONE, ElementarySpace.NICE, EASY),  // no space needed but one in nice, split to make things nicer

    ONE_IS_NICE_SPLIT_BEGIN_END(ElementarySpace.RELAXED_NONE, ElementarySpace.NICE, BEGIN_END),  // no space needed but one in nice, split to make things nicer

    NEWLINE(ElementarySpace.NEWLINE, ElementarySpace.NEWLINE, ALWAYS), // enforce a newline

    // easy either left or right, but consistently according to preferences
    // e.g. && either at beginning of line in sequence, or always at end
    // in nice formatting, one space is used
    ONE_IS_NICE_EASY_L(ElementarySpace.RELAXED_NONE, ElementarySpace.NICE, EASY_L),
    ONE_IS_NICE_EASY_R(ElementarySpace.RELAXED_NONE, ElementarySpace.NICE, EASY_R);

    private final ElementarySpace minimal;
    private final ElementarySpace nice;
    public final Split split;

    Space(ElementarySpace minimal, ElementarySpace nice, Split split) {
        this.minimal = minimal;
        this.nice = nice;
        this.split = split;
    }

    @Override
    public String minimal() {
        return minimal.write();
    }

    public ElementarySpace elementarySpace(FormattingOptions options) {
        return options.compact() ? minimal : nice;
    }

    @Override
    public String write(FormattingOptions options) {
        return options.compact() ? minimal.write() : nice.write();
    }

    @Override
    public String generateJavaForDebugging() {
        return ".add(Space." + this.name() + ")";
    }
}
