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

import org.e2immu.analyser.analyser.*;
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
import java.util.TreeMap;

import static org.e2immu.analyser.analyser.LV.LINK_DEPENDENT;
import static org.e2immu.analyser.analyser.LinkedVariables.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestWeightedGraph_2 extends CommonWG {

    Variable first, next, sa, rv;
    WeightedGraph wg;

    @BeforeEach
    public void beforeEach() {
        first = makeVariable("first");
        next = makeVariable("next");
        sa = makeVariable("sa");
        rv = makeVariable("rv");

        wg = new WeightedGraphImpl();
        wg.addNode(first, Map.of(sa, v0, next, delay));
        wg.addNode(next, Map.of(sa, v4, first, delay, rv, v4));
        wg.addNode(sa, Map.of(next, v4, first, v0, rv, v0));
        wg.addNode(rv, Map.of(sa, v0, next, v4));
    }

    @Test
    public void test1() {
        Map<Variable, LV> startAtFirst = wg.shortestPath().links(first, LINK_DEPENDENT);
        assertEquals(4, startAtFirst.size());
        assertEquals(v0, startAtFirst.get(first));
        assertEquals(v0, startAtFirst.get(sa));
        assertEquals(delay, startAtFirst.get(next));
        assertEquals(v0, startAtFirst.get(rv));
    }
}
