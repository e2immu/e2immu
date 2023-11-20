package org.e2immu.analyser.resolver.testexample;

import java.util.Set;

public class Constructor_17 {

    static class G<T> {

    }

    static class V<T> {

    }

    static class BreakCycles<T> {
        public BreakCycles(ActionComputer<T> actionComputer) {

        }
    }

    interface ActionComputer<T> {
        Action<T> compute(G<T> g, Set<V<T>> cycle);
    }

    interface Action<T> {
        G<T> apply();
        ActionInfo<T> info();
    }

    public interface ActionInfo<T> {

    }

    public void method(V<String> v6, V<String> v9) {
        BreakCycles<String> bc2 = new BreakCycles<>((g1, cycle) -> {
            if (cycle.contains(v6)) {
                return new Action<String>() {
                    @Override
                    public G<String> apply() {
                        return g1;
                    }

                    @Override
                    public ActionInfo<String> info() {
                        return null;
                    }
                };
            }
            if (cycle.contains(v9)) {
                return null; // we cannot break it
            }
            throw new UnsupportedOperationException();
        });
    }
}
