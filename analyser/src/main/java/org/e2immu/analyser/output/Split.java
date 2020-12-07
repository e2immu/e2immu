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

public enum Split {
    NEVER(0),
    EASY_L(1),
    EASY_R(2),
    EASY(3),
    ALWAYS(4);

    private final int rank;

    Split(int rank) {
        this.rank = rank;
    }

    public Split easiest(Split split) {
        return split.rank > this.rank ? split : this;
    }
}
