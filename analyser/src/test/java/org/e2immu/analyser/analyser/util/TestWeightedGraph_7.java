package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.*;
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
    map <0,1> -----4----- <0,1> entries       All hidden content of Map (type parameters 0 and 1) correspond to
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

        LV.HiddenContentSelector select0 = selectTypeParameter(0);
        LV.HiddenContentSelector select1 = selectTypeParameter(1);
        LV thisVar_4_map = LV.createHC(select0, select1);
        assertEquals("<0>-4-<1>", thisVar_4_map.toString());
        assertEquals("<1>-4-<0>", thisVar_4_map.reverse().toString());

        wg.addNode(thisVar, Map.of(map, thisVar_4_map));
        LV.HiddenContentSelector select01 = LV.selectTypeParameters(0, 1);
        LV map_4_entries = LV.createHC(select01, select01);
        assertEquals("<0,1>-4-<0,1>", map_4_entries.toString());
        wg.addNode(map, Map.of(thisVar, thisVar_4_map.reverse(), entries, map_4_entries));
        wg.addNode(entries, Map.of(map, map_4_entries.reverse(), entry, map_4_entries));

        LV entry_4_l = LV.createHC(select0, CS_ALL);
        LV entry_4_t = LV.createHC(select1, CS_ALL);
        assertEquals("<0>-4-*", entry_4_l.toString());
        assertEquals("<1>-4-*", entry_4_t.toString());
        wg.addNode(entry, Map.of(entries, map_4_entries.reverse(), l, entry_4_l, t, entry_4_t));

        wg.addNode(l, Map.of(entry, entry_4_l.reverse()));
        wg.addNode(t, Map.of(entry, entry_4_t.reverse()));

        shortestPath = wg.shortestPath();
    }

    @Test
    @DisplayName("starting in thisVar")
    public void testTV() {
        Map<Variable, LV> links = shortestPath.links(thisVar, null);
        assertEquals(v0, links.get(thisVar)); // start all
        assertEquals(v4, links.get(map)); // then <0> of map
        assertEquals(v4, links.get(entries)); // <0> of entries
        assertEquals(v4, links.get(entry)); // <0> of entry
        assertEquals(v4, links.get(l)); // * of l
        assertEquals(v4, links.get(t)); // * of t
    }

    @Test
    @DisplayName("starting in l")
    public void testL() {
        Map<Variable, LV> links = shortestPath.links(l, null);
        assertEquals(v0, links.get(l)); // start all
        assertEquals(v4, links.get(entry)); // then <0> of entry
        assertNull(links.get(t)); // not reachable
        assertEquals(v4, links.get(entries)); // <0> of entries
        assertEquals(v4, links.get(map)); // <0> of map
        assertEquals(v4, links.get(thisVar)); // <0> of thisVar
    }

    @Test
    @DisplayName("starting in t")
    public void testT() {
        Map<Variable, LV> links = shortestPath.links(t, null);
        assertEquals(v0, links.get(t)); // start all
        assertEquals(v4, links.get(entry)); // then <0> of entry
        assertNull(links.get(l)); // not reachable
        assertEquals(v4, links.get(entries)); // <0> of entries
    }

}
