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

    NONE("", "", NEVER),       // no space, do not split

    ONE(" ", " ", NEVER),       // exactly one space needed, never split here (e.g. between class and class name); two ONEs collapse into one

    ONE_REQUIRED_EASY_SPLIT(" ", " ", EASY),  // end of annotation; needs minimally one, but can be newline

    NO_SPACE_SPLIT_ALLOWED("", "", EASY),     // no space needed, split to make things nicer

    ONE_IS_NICE_EASY_SPLIT("", " ", EASY),  // no space needed but one in nice, split to make things nicer

    NEWLINE("\n", "\n", ALWAYS), // enforce a newline

    // easy either left or right, but consistently according to preferences
    // e.g. && either at beginning of line in sequence, or always at end
    // in nice formatting, one space is used
    ONE_IS_NICE_EASY_L("", " ", EASY_L),
    ONE_IS_NICE_EASY_R("", " ", EASY_R);

    private final String minimal;
    private final String nice;
    public final Split split;

    Space(String minimal, String nice, Split split) {
        this.minimal = minimal;
        this.nice = nice;
        this.split = split;
    }

    @Override
    public String minimal() {
        return minimal;
    }

    @Override
    public String debug() {
        return minimal;
    }

    @Override
    public String write(FormattingOptions options) {
        return options.compact() ? minimal : nice;
    }
}
