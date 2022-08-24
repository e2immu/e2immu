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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.e2immu.analyser.analyser.LinkedVariables.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestWeightedGraph_2 {

    Variable first, next, sa, rv;
    CausesOfDelay delay;
    final DV v0 = LINK_STATICALLY_ASSIGNED;
    final DV v3 = LINK_INDEPENDENT_HC;
    WeightedGraph wg;

    @BeforeEach
    public void beforeEach() {
        first = makeVariable("first");
        next = makeVariable("next");
        sa = makeVariable("sa");
        rv = makeVariable("rv");
        delay = DelayFactory.createDelay(new SimpleCause(Location.NOT_YET_SET, CauseOfDelay.Cause.ECI));

        wg = new WeightedGraph();
        wg.addNode(first, Map.of(sa, v0, next, delay));
        wg.addNode(next, Map.of(sa, v3, first, delay, rv, v3));
        wg.addNode(sa, Map.of(next, v3, first, v0, rv, v0));
        wg.addNode(rv, Map.of(sa, v0, next, v3));
    }

    @Test
    public void test1() {
        Map<Variable, DV> startAtFirst = wg.links(first, LINK_DEPENDENT, true);
        assertEquals(4, startAtFirst.size());
        assertEquals(v0, startAtFirst.get(first));
        assertEquals(v0, startAtFirst.get(sa));
        assertEquals(delay, startAtFirst.get(next));
        assertEquals(v0, startAtFirst.get(rv));
    }

    private Variable makeVariable(String name) {
        TypeInfo t = new TypeInfo("a.b.c", "T");
        return new LocalVariableReference(new LocalVariable(name, new ParameterizedType(t, 0)));
    }
}
