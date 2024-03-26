package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.HiddenContentSelector;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestWeightedGraph_8 extends CommonWG {


    Variable map, keys, values;
    WeightedGraph wg;
    ShortestPath shortestPath;

    // Map<K,V> map = ...; Set<K> keys = map.keySet(); Collection<V> values = map.values()

    @BeforeEach
    public void beforeEach() {
        keys = makeVariable("keys");
        map = makeVariable("map");
        values = makeVariable("values");

        wg = new WeightedGraphImpl();

        LV map_4_keys = LV.createHC(HiddenContentSelector.CsSet.selectTypeParameter(0), HiddenContentSelector.CsSet.selectTypeParameter(0));
        LV map_4_values = LV.createHC(HiddenContentSelector.CsSet.selectTypeParameter(1), HiddenContentSelector.CsSet.selectTypeParameter(0));
        assertEquals("<1>-4-<0>", map_4_values.toString());
        assertEquals("<0>-4-<0>", map_4_keys.reverse().toString());

        wg.addNode(map, Map.of(keys, map_4_keys, values, map_4_values));
        wg.addNode(keys, Map.of(map, map_4_keys.reverse()));
        wg.addNode(values, Map.of(map, map_4_values.reverse()));

        shortestPath = wg.shortestPath();
    }

    @Test
    @DisplayName("start in keys")
    public void testK() {
        Map<Variable, LV> startAt = shortestPath.links(keys, null);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(keys));
        assertEquals(v4, startAt.get(map));
        assertNull(startAt.get(values));
    }

    @Test
    @DisplayName("start in values")
    public void testV() {
        Map<Variable, LV> startAt = shortestPath.links(values, null);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(values));
        assertEquals(v4, startAt.get(map));
        assertNull(startAt.get(keys));
    }

    @Test
    @DisplayName("start in map")
    public void testM() {
        Map<Variable, LV> startAt = shortestPath.links(map, null);
        assertEquals(3, startAt.size());
        assertEquals(v0, startAt.get(map));
        assertEquals(v4, startAt.get(keys));
        assertEquals(v4, startAt.get(values));
    }
}
