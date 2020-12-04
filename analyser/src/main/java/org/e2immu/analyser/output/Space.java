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

public enum Space {

    // (almost) never split here
    // e.g. semicolon left
    NEVER,

    // almost always introduce a space, except when there's a (
    // && !a; b = !a; but (!a && !b)

    NOT_FOR_LEFT_PARENTHESIS,

    // easy either left or right, but consistently according to preferences
    // e.g. && either at beginning of line in sequence, or always at end
    EASY_LR,

    // normally add one space; if break, break consistently left or right
    ONE_LR,

    EASY,

    // must have at least white space
    MUST_HAVE_ONE,

}
