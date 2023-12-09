package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.annotation.NotNull;

import java.util.Map;

public interface ShortestPath {
    Map<Variable, DV> links(@NotNull Variable v, DV maxWeight, boolean followDelayed);

    Map<Variable, DV> linksFollowIsHCOf(Variable v, boolean followDelayed);
}
