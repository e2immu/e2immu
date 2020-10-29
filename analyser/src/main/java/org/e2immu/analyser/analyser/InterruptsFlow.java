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

package org.e2immu.analyser.analyser;

import java.util.Objects;

public class InterruptsFlow {
    public static final String NO_LABEL = "";
    public static final InterruptsFlow NO = new InterruptsFlow("no interrupt", 0, NO_LABEL);
    public static final InterruptsFlow RETURN = new InterruptsFlow("return", 3, NO_LABEL);
    public static final InterruptsFlow ESCAPE = new InterruptsFlow("escape", 4, NO_LABEL);

    public final int level;
    public final String label;
    public final String name;

    private InterruptsFlow(String name, int level, String label) {
        this.level = level;
        this.name = name;
        this.label = Objects.requireNonNull(label);
    }

    public static InterruptsFlow createBreak(String label) {
        return new InterruptsFlow("break", 1, label == null ? "" : label);
    }

    public static InterruptsFlow createContinue(String label) {
        return new InterruptsFlow("continue", 2, label == null ? "" : label);
    }

    InterruptsFlow best(InterruptsFlow other) {
        if (other.level > level) return other;
        return this;
    }

    @Override
    public String toString() {
        return name + (label.equals(NO_LABEL) ? "" : ":" + label);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InterruptsFlow that = (InterruptsFlow) o;
        return level == that.level &&
                label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, label);
    }

    public boolean isBreak() {
        return level == 1;
    }

    public boolean isContinue() {
        return level == 2;
    }
}
