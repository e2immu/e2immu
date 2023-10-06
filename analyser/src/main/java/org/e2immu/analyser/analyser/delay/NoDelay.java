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

package org.e2immu.analyser.analyser.delay;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.DV;

import java.util.Objects;

public class NoDelay implements DV {

    private final int value;
    private final String label;
    public static final String COMPUTED = "computed";

    public NoDelay(int value) {
        this(value, COMPUTED);
    }

    public NoDelay(int value, String label) {
        this.value = value;
        this.label = label;
        assert value >= 0;
        assert label != null;
    }

    @Override
    public int value() {
        return value;
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public DV min(DV other) {
        if (other.isInitialDelay()) return this;
        if (other.value() > value) return this;
        // if other is a delay, its value is less than ours!
        return other;
    }

    @Override
    public DV minIgnoreNotInvolved(DV other) {
        if (other.isInitialDelay()) return this;

        // make sure that FALSE wins from NOT_INVOLVED
        if (other.value() == 0 && value > 0) {
            return this;
        }
        if (value == 0 && other.value() > 0) {
            return other;
        }

        // normal
        if (other.value() > value) return this;
        // if other is a delay, its value is less than ours!
        return other;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public DV max(DV other) {
        if (other.isInitialDelay()) return this;
        if (other.value() >= value || other.isDelayed()) return other;
        return this;
    }

    @Override
    public DV maxIgnoreDelay(DV other) {
        if (other.value() >= value) return other;
        return this;
    }

    @Override
    public DV replaceDelayBy(DV nonDelay) {
        assert nonDelay.isDone();
        return this;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public boolean isDelayed() {
        return false;
    }

    @Override
    public String toString() {
        return label + ":" + value;
    }

    @Override
    public int compareTo(DV o) {
        return value - o.value();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // explicitly allow for the subclass "Inconclusive"
        if (o == null || !NoDelay.class.isAssignableFrom(o.getClass())) return false;
        NoDelay noDelay = (NoDelay) o;
        return value == noDelay.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public boolean isInitialDelay() {
        return false;
    }
}
