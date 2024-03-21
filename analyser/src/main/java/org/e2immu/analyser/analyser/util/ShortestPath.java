package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.annotation.NotNull;

import java.util.Map;

public interface ShortestPath {
    Map<Variable, LV> links(@NotNull Variable v, LV maxWeight);
}
