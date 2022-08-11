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

public class TestWeightedGraph_3 {

    Variable d, n, node, nodeDependsOn, t, dependsOn, thisVar;
    CausesOfDelay delay;
    final DV v0 = LINK_STATICALLY_ASSIGNED;
    final DV v2 = LINK_DEPENDENT;
    final DV v3 = LINK_INDEPENDENT1;
    WeightedGraph wg;

    @BeforeEach
    public void beforeEach() {
        d = makeVariable("d");
        n = makeVariable("n");
        node = makeVariable("node");
        nodeDependsOn = makeVariable("nodeDependsOn");
        t = makeVariable("t");
        dependsOn = makeVariable("dependsOn");
        thisVar = makeVariable("thisVar");
        delay = DelayFactory.createDelay(new SimpleCause(Location.NOT_YET_SET, CauseOfDelay.Cause.ECI));

        wg = new WeightedGraph();
        wg.addNode(d, Map.of(node, v3, nodeDependsOn, v3, dependsOn, v3, thisVar, delay));
        wg.addNode(n, Map.of());
        wg.addNode(node, Map.of(d, v3, nodeDependsOn, v2, dependsOn, v3));
        wg.addNode(nodeDependsOn, Map.of(d, v3, node, v2, dependsOn, v3));
        wg.addNode(t, Map.of(thisVar, delay));
        wg.addNode(dependsOn, Map.of(d, v3, node, v3, nodeDependsOn, v3));
        wg.addNode(thisVar, Map.of(d, delay, t, delay));
    }

    @Test
    public void test1() {
        Map<Variable, DV> thisLinks = wg.links(thisVar, v2, true);
        assertEquals(3, thisLinks.size());
    }

    private Variable makeVariable(String name) {
        TypeInfo t = new TypeInfo("a.b.c", "T");
        return new LocalVariableReference(new LocalVariable(name, new ParameterizedType(t, 0)));
    }
}
