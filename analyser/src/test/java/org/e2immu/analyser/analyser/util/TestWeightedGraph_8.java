package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.LINK_COMMON_HC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestWeightedGraph_8 extends CommonWG {


    Variable map, keys, values;
    WeightedGraph wg;
    ShortestPath shortestPath;

    @BeforeEach
    public void beforeEach() {
        keys = makeVariable("keys");
        map = makeVariable("map");
        values = makeVariable("values");

        wg = new WeightedGraphImpl();

        LV map_4_keys = LV.createHC(LV.typeParameter(null, 0),
                LV.typeParameter(null, 0));
        LV map_4_values = LV.createHC(LV.typeParameter(null, 1),
                LV.typeParameter(null, 0));
        assertEquals("<1>-4-<0>", map_4_values.toString());
        assertEquals("<0>-4-<0>", map_4_keys.reverse().toString());

        wg.addNode(map, Map.of(keys, map_4_keys, values, map_4_values));
        wg.addNode(keys, Map.of(map, map_4_keys.reverse()));
        wg.addNode(values, Map.of(map, map_4_values.reverse()));

        shortestPath = wg.shortestPath();
    }

    @Test
    @DisplayName("start in keys")
    public void test() {
        Map<Variable, LV> startAt = shortestPath.links(keys, null);
        assertEquals(2, startAt.size());
        assertEquals(v0, startAt.get(keys));
        assertEquals(v4, startAt.get(map));
        assertNull(startAt.get(values));
    }
}
