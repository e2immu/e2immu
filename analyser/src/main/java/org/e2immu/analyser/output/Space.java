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

public enum Space implements OutputElement {

    NONE(""),       // no space, do not split

    ONE(" "),       // exactly one space needed, never split here (e.g. between class and class name); two ONEs collapse into one

    ONE_EASY(" "),  // end of annotation; needs minimally one, but can be newline

    HARD(""),       // no space needed, normally one present, do not split here unless no other option

    EASY(""),     // no space needed, split to make things nicer

    NEWLINE("\n"), // enforce a newline

    // almost always introduce a space to make things nicer, except when there's a (
    // && !a; b = !a; but (!a && !b)

    NOT_FOR_LEFT_PARENTHESIS(""),

    // easy either left or right, but consistently according to preferences
    // e.g. && either at beginning of line in sequence, or always at end
    EASY_LR("");

    private final String minimal;

    Space(String minimal) {
        this.minimal = minimal;
    }

    @Override
    public String minimal() {
        return minimal;
    }

    @Override
    public String debug() {
        return minimal;
    }
}
