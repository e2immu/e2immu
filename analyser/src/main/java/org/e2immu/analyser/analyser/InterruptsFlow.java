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

package org.e2immu.analyser.analyser;

import java.util.Objects;

public record InterruptsFlow(String name, int level, String label) {
    public static final String NO_LABEL = "";

    public static final InterruptsFlow NO = new InterruptsFlow("no interrupt", 0, NO_LABEL);
    public static final InterruptsFlow RETURN = new InterruptsFlow("return", 3, NO_LABEL);
    public static final InterruptsFlow ESCAPE = new InterruptsFlow("escape", 4, NO_LABEL);

    public InterruptsFlow(String name, int level, String label) {
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

    public InterruptsFlow best(InterruptsFlow other) {
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

    public boolean isAtLeastBreak() {
        return level >= 1;
    }
}
