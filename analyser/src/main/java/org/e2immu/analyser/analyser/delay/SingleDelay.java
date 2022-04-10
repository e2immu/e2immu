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

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class SingleDelay extends AbstractDelay {
    //private static final Logger LOGGER = LoggerFactory.getLogger(SingleDelay.class);

    private final CauseOfDelay cause;

    SingleDelay(CauseOfDelay cause) {
        this.cause = cause;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SingleDelay that = (SingleDelay) o;
        return cause.equals(that.cause);
    }

    @Override
    public int hashCode() {
        return cause.hashCode();
    }

    @Override
    public int numberOfDelays() {
        return 1;
    }

    @Override
    public boolean contains(Variable variable) {
        return cause instanceof VariableCause vc && vc.variable().equals(variable);
    }

    @Override
    public int maxPriority() {
        return cause.cause().priority;
    }

    @Override
    public CausesOfDelay merge(CausesOfDelay other) {
        if (other.isDone()) return this;
        if (maxPriority() < other.maxPriority()) {
            //LOGGER.debug("Dropping {} in favour of {}", this, other);
            return other;
        }
        if (maxPriority() > other.maxPriority()) {
            //LOGGER.debug("Dropping {} in favour of {}", other, this);
            return this;
        }
        if (equals(other)) return this;
        if (maxPriority() == CauseOfDelay.LOW && other.maxPriority() == CauseOfDelay.LOW) {
            if (other instanceof SingleDelay sd) {
                if (cause.compareTo(sd.cause) < 0) {
                    //LOGGER.debug("Dropping {} in favour of {}", other, this);
                    return this;
                }
                //LOGGER.debug("Dropping {} in favour of {}", this, other);
                return other;
            } else {
                throw new UnsupportedOperationException();
            }
        }
        Map<String, CauseOfDelay> map = new HashMap<>();
        map.put(cause.withoutStatementIdentifier(), cause);
        return SimpleSet.mergeIntoMapAndReturn(other.causesStream(), map, maxPriority());
    }

    @Override
    public Stream<CauseOfDelay> causesStream() {
        return Stream.of(cause);
    }

    @Override
    public CausesOfDelay removeAll(Set<CauseOfDelay> breaks) {
        return breaks.contains(cause) ? CausesOfDelay.EMPTY : this;
    }

    @Override
    public String toString() {
        return cause.toString();
    }

    @Override
    public CausesOfDelay translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        CauseOfDelay translated = cause.translate(inspectionProvider, translationMap);
        if(translated != cause) return new SingleDelay(translated);
        return this;
    }
}
