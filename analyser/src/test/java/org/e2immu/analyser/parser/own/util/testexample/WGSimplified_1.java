package org.e2immu.analyser.parser.own.util.testexample;


import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;

public class WGSimplified_1 {

    public interface Variable extends Comparable<Variable> {
    }

    interface DV {
        DV min(DV other);

        DV max(DV other);

        boolean le(DV other);

        boolean lt(DV other);

        boolean isDelayed();

        default boolean isDone() {
            return !isDelayed();
        }
    }

    record DVI(int k) implements DV {
        @Override
        public DV min(DV other) {
            return new DVI(Math.min(k, ((DVI) other).k));
        }

        @Override
        public DV max(DV other) {
            return new DVI(Math.max(k, ((DVI) other).k));
        }

        @Override
        public boolean le(DV other) {
            return k <= ((DVI) other).k;
        }

        @Override
        public boolean lt(DV other) {
            return k < ((DVI) other).k;
        }

        @Override
        public boolean isDelayed() {
            return k < 0;
        }
    }

    private static class Node {
        Map<Variable, DV> dependsOn;
        final Variable variable;

        private Node(Variable v) {
            variable = v;
        }
    }

    @Modified
    private final Map<Variable, Node> nodeMap = new TreeMap<>();

    private Node getOrCreate(@NotNull Variable v) {
        Objects.requireNonNull(v);
        Node node = nodeMap.get(v);
        if (node == null) {
            node = new Node(v);
            nodeMap.put(v, node);
        }
        return node;
    }

    private static final DV LINK_STATICALLY_ASSIGNED = new DVI(0);
    private static final DV LINK_IS_HC_OF = new DVI(3);
    private static final DV LINK_COMMON_HC = new DVI(4);

    private static DV min(DV d1, DV d2) {
        if (d1.equals(LINK_STATICALLY_ASSIGNED) || d2.equals(LINK_STATICALLY_ASSIGNED)) {
            return LINK_STATICALLY_ASSIGNED;
        }
        return d1.min(d2);
    }

    private static DV max(DV d1, DV d2) {
        return d1.max(d2);
    }

    public Map<Variable, DV> links(@NotNull Variable v, DV maxWeight, boolean followDelayed) {
        Map<Variable, DV> result = new TreeMap<>();
        result.put(v, LINK_STATICALLY_ASSIGNED);
        recursivelyComputeLinks(v, result, maxWeight, followDelayed);
        return result;
    }

    @NotModified
    private void recursivelyComputeLinks(@NotNull Variable v,
                                         @NotNull Map<Variable, DV> distanceToStartingPoint,
                                         DV maxValueIncl,
                                         boolean followDelayed) {
        Objects.requireNonNull(v);
        Node node = nodeMap.get(v);

        // must be already present!
        DV currentDistanceToV = distanceToStartingPoint.get(v);

        // do I have outgoing arrows?
        if (node != null && node.dependsOn != null) {

            // yes, opportunity (1) to improve distance computations, (2) to visit them
            node.dependsOn.forEach((n, d) -> {
                if (d.isDelayed() && followDelayed || d.isDone() && (maxValueIncl == null || d.le(maxValueIncl))) {
                    DV distanceToN = max(currentDistanceToV, d);
                    DV currentDistanceToN = distanceToStartingPoint.get(n);
                    if (currentDistanceToN == null) {
                        // we've not been at N before
                        if (d.isDelayed() || maxValueIncl == null || d.le(maxValueIncl)) {
                            distanceToStartingPoint.put(n, distanceToN);
                            DV newMax = LINK_IS_HC_OF.equals(d) ? LINK_COMMON_HC : maxValueIncl;
                            recursivelyComputeLinks(n, distanceToStartingPoint, newMax, followDelayed);
                        } else {
                            distanceToStartingPoint.put(n, LINK_COMMON_HC); // beyond max value
                        }
                    } else {
                        DV newDistanceToN = min(distanceToN, currentDistanceToN);
                        distanceToStartingPoint.put(n, newDistanceToN);
                        if (newDistanceToN.lt(currentDistanceToN)) {
                            DV newMax = LINK_IS_HC_OF.equals(d) ? LINK_COMMON_HC : maxValueIncl;
                            recursivelyComputeLinks(n, distanceToStartingPoint, newMax, followDelayed);
                        }
                    }
                } // else: ignore delayed links!
            });
        }
    }

    @NotModified(contract = true)
    public void visit(@NotNull BiConsumer<Variable, Map<Variable, DV>> consumer) {
        nodeMap.values().forEach(n -> consumer.accept(n.variable, n.dependsOn));
    }

    @Modified
    public void addNode(@NotNull Variable v,
                        @NotNull Map<Variable, DV> dependsOn) {
        addNode(v, dependsOn, false, (o, n) -> o);
    }

    @Modified
    public void addNode(@NotNull Variable v,
                        @NotNull(contract = true) Map<Variable, DV> dependsOn,
                        boolean bidirectional,
                        BinaryOperator<DV> merger) {
        Node node = getOrCreate(v);
        for (Map.Entry<Variable, DV> e : dependsOn.entrySet()) {
            if (node.dependsOn == null) node.dependsOn = new TreeMap<>();
            DV linkLevel = e.getValue();

            node.dependsOn.merge(e.getKey(), linkLevel, merger);
            if (bidirectional) {
                Node n = getOrCreate(e.getKey());
                if (n.dependsOn == null) n.dependsOn = new TreeMap<>();
                n.dependsOn.merge(v, linkLevel, merger);
            }
        }
    }

    public static Variable variable(Node node) {
        return node.variable;
    }
}
