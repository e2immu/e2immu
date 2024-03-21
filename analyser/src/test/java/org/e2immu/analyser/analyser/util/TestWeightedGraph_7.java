package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.LINK_COMMON_HC;
import static org.e2immu.analyser.analyser.LV.LINK_DEPENDENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestWeightedGraph_7 extends CommonWG {

    /*
    IMPORTANT: in this example, we're assuming that map is immutable, as is Map.Entry
     */
    static class X<T> {
        final Map<Long, T> map = Map.of();

        void method() {
            Set<Map.Entry<Long, T>> entries = map.entrySet();
            for (Map.Entry<Long, T> entry : entries) {
                Long l = entry.getKey();
                T t = entry.getValue();
                System.out.println(l + " -> " + t);
            }
        }
    }

    /*
    this <0> ------4----- <1> map             Type parameter 0 corresponds to type parameter 1 in Map<K, V>
    map <0,1> -----4----- <0-0,0-1> entries   All hidden content of Map (type parameters 0 and 1) correspond to
                                              type parameter 0 in Set<E>, which consists of the type parameters 0
                                              and 1 of Map.Entry
    entries <0-0,0-1> -- 4 -- <0,1> entry     ~ get() method, is part of HC
    l * ------------ 4 ------ <0> entry       ~ get() method, is part of HC, parameter 0
    t * ------------ 4 ------ <1> entry       ~ get() method, is part of HC, parameter 1
     */

    Variable thisVar, map, entries, entry, l, t;
    WeightedGraph wg;
    ShortestPath shortestPath;

    @BeforeEach
    public void beforeEach() {
        thisVar = makeVariable("thisVar");
        map = makeVariable("map");
        entries = makeVariable("entries");
        entry = makeVariable("entry");
        l = makeVariable("l");
        t = makeVariable("t");

        wg = new WeightedGraphImpl();

        LV thisVar_4_map = LV.createHC(LV.typeParameter(null, 0),
                LV.typeParameter(null, 1));
        assertEquals("<0>-4-<1>", thisVar_4_map.toString());
        assertEquals("<1>-4-<0>", thisVar_4_map.reverse().toString());

        wg.addNode(thisVar, Map.of(map, thisVar_4_map));

        LV map_4_entries = LV.createHC(LV.typeParameters(null, List.of(0), null, List.of(1)),
                LV.typeParameters(null, List.of(0, 0), null, List.of(0, 1)));
        assertEquals("<0,1>-4-<0-0,0-1>", map_4_entries.toString());
        wg.addNode(map, Map.of(thisVar, thisVar_4_map.reverse(), entries, map_4_entries));

        LV entries_4_entry = LV.createHC(LV.typeParameters(null, List.of(0, 0), null, List.of(0, 1)),
                LV.typeParameters(null, List.of(0), null, List.of(1)));
        assertEquals("<0-0,0-1>-4-<0,1>", entries_4_entry.toString());
        wg.addNode(entries, Map.of(map, map_4_entries.reverse(), entry, entries_4_entry));

        LV entry_4_l = LV.createHC(LV.typeParameter(null, 0), LV.wholeType(null));
        LV entry_4_t = LV.createHC(LV.typeParameter(null, 1), LV.wholeType(null));
        assertEquals("<0>-4-<>", entry_4_l.toString());
        assertEquals("<1>-4-<>", entry_4_t.toString());
        wg.addNode(entry, Map.of(entries, entries_4_entry.reverse(), l, entry_4_l, t, entry_4_t));

        wg.addNode(l, Map.of(entry, entry_4_l.reverse()));
        wg.addNode(t, Map.of(entry, entry_4_t.reverse()));

        shortestPath = wg.shortestPath();
    }

    // fully connected
    @Test
    public void test() {
        Variable[] variables = new Variable[]{thisVar, map, entries, entry, l, t};
        for (int i = 0; i < variables.length; i++) {
            Map<Variable, LV> start = shortestPath.links(variables[i], LINK_COMMON_HC);
            for (int j = 0; j < variables.length; j++) {
                LV expect = i == j ? v0 : v4;
                assertEquals(expect, start.get(variables[j]), "Goes wrong: "+i+", "+j);
            }
        }
    }
}
