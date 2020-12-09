/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    ONE_IS_NICE_EASY_SPLIT(ElementarySpace.NONE, ElementarySpace.NICE, EASY),  // no space needed but one in nice, split to make things nicer

    ONE_IS_NICE_SPLIT_BEGIN_END(ElementarySpace.NONE, ElementarySpace.NICE, BEGIN_END),  // no space needed but one in nice, split to make things nicer

    NEWLINE(ElementarySpace.NEWLINE, ElementarySpace.NEWLINE, ALWAYS), // enforce a newline

    // easy either left or right, but consistently according to preferences
    // e.g. && either at beginning of line in sequence, or always at end
    // in nice formatting, one space is used
    ONE_IS_NICE_EASY_L(ElementarySpace.NONE, ElementarySpace.NICE, EASY_L),
    ONE_IS_NICE_EASY_R(ElementarySpace.NONE, ElementarySpace.NICE, EASY_R);

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

    @Override
    public String debug() {
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
