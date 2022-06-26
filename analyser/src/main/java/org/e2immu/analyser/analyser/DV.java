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

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.NoDelay;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.util.WeightedGraph;
import org.e2immu.annotation.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/*
Delayable Value
 */
public interface DV extends WeightedGraph.Weight {

    DV MAX_INT_DV = new NoDelay(Integer.MAX_VALUE, "max_int");

    // special value; see explanation at max()
    DV MIN_INT_DV = DelayFactory.createDelay(Location.NOT_YET_SET, CauseOfDelay.Cause.MIN_INT);

    DV FALSE_DV = new NoDelay(0, "false");
    DV TRUE_DV = new NoDelay(1, "true");

    static DV fromBoolDv(boolean b) {
        return b ? TRUE_DV : FALSE_DV;
    }

    int value();

    @NotNull
    CausesOfDelay causesOfDelay();

    boolean isDelayed();

    boolean isDone();

    @NotNull
    DV min(DV other);

    /*
    IMPORTANT: max does not treat MIN_INT_DV as a delay; it never returns it.

    reduce(MIN_INT_DV, DV::max) is the correct way to find the maximal value, taking
    delays into account, but having a value to check when there was nothing to reduce.
     */
    @NotNull
    DV max(DV other);

    @NotNull
    DV maxIgnoreDelay(DV other);

    @NotNull
    DV replaceDelayBy(DV nonDelay);

    DV minIgnoreNotInvolved(DV change);

    default boolean gt(DV other) {
        return value() > other.value();
    }

    default boolean lt(DV other) {
        return value() < other.value();
    }

    default boolean ge(DV other) {
        return value() >= other.value();
    }

    default boolean le(DV other) {
        return value() <= other.value();
    }

    default boolean valueIsTrue() {
        return value() == 1;
    }

    default boolean valueIsFalse() {
        return value() == 0;
    }

    default boolean containsCauseOfDelay(CauseOfDelay.Cause cause) {
        assert isHighPriority(cause);
        return causesOfDelay().causesStream().anyMatch(c -> c.cause() == cause);
    }

    default boolean containsCauseOfDelay(CauseOfDelay.Cause cause, Predicate<CauseOfDelay> predicate) {
        assert isHighPriority(cause);
        return causesOfDelay().causesStream().anyMatch(c -> c.cause() == cause && predicate.test(c));
    }

    default Optional<VariableCause> findVariableCause(CauseOfDelay.Cause cause, Predicate<VariableCause> predicate) {
        assert isHighPriority(cause);
        return causesOfDelay().causesStream().filter(c -> c.cause() == cause && c instanceof VariableCause vc && predicate.test(vc))
                .map(c -> (VariableCause) c)
                .findFirst();
    }

    private static boolean isHighPriority(CauseOfDelay.Cause cause) {
        assert cause.priority == CauseOfDelay.HIGH : "Low priority causes are potentially filtered out";
        return true;
    }

    @NotNull
    String label();

    default boolean isInconclusive() { return false; }
}
