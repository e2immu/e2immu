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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.util.Pair;

import java.util.*;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;

/**
 * @param <T> typically String, as the label of the component. In case of the primary type analyser, T is Analyser.
 * @param <S> shared state
 */
public class AnalyserComponents<T, S> {

    private final LinkedHashMap<T, AnalysisStatus.AnalysisResultSupplier<S>> suppliers;
    private final AnalysisStatus[] state;

    private AnalyserComponents(LinkedHashMap<T, AnalysisResultSupplier<S>> suppliers) {
        this.suppliers = suppliers;
        state = new AnalysisStatus[suppliers.size()];
        Arrays.fill(state, AnalysisStatus.NOT_YET_EXECUTED);
    }

    public AnalysisStatus getStatus(String t) {
        return getStatusesAsMap().get(t);
    }

    static class Builder<T, S> {
        private final LinkedHashMap<T, AnalysisStatus.AnalysisResultSupplier<S>> suppliers = new LinkedHashMap<>();

        public Builder<T, S> add(T t, AnalysisResultSupplier<S> supplier) {
            if (suppliers.put(t, supplier) != null) throw new UnsupportedOperationException();
            return this;
        }

        public AnalyserComponents<T, S> build() {
            return new AnalyserComponents<>(suppliers);
        }
    }

    // delay, delay -> delay
    // delay, progress -> progress
    // delay, done -> progress
    // progress, delay NOT ALLOWED
    // progress, progress -> progress
    // progress, done -> done
    // done  -> done

    // all done -> mark done for this and all subsequent steps, don't execute them
    // run-again -> will be run again, but does not delay
    public AnalysisStatus run(S s) {
        int i = 0;
        AnalysisStatus combined = DONE;
        boolean progress = false;
        for (AnalysisStatus.AnalysisResultSupplier<S> supplier : suppliers.values()) {
            AnalysisStatus initialState = state[i];
            if (initialState != DONE) {
                // execute
                AnalysisStatus afterExec = supplier.apply(s);
                assert afterExec != NOT_YET_EXECUTED;
                if(afterExec == DONE || afterExec == DONE_ALL) {
                    progress = true;
                }
                if (afterExec == DONE_ALL) {
                    while (i < state.length) {
                        state[i++] = DONE;
                    }
                    break; // out of the for loop!
                }
                state[i] = afterExec;
                if (afterExec != RUN_AGAIN) {
                    assert afterExec.isDelayed() || afterExec == DONE;
                    combined = combined.combine(afterExec);
                }
            }
            i++;
        }
        return combined.addProgress(progress);
    }

    public String details() {
        StringBuilder sb = new StringBuilder();
        getStatuses().forEach(p -> sb.append(p.k).append(": ").append(p.v).append("\n"));
        return sb.toString();
    }

    public Map<String, AnalysisStatus> getStatusesAsMap() {
        Map<String, AnalysisStatus> builder = new HashMap<>();
        int i = 0;
        for (T t : suppliers.keySet()) {
            builder.put(t.toString(), state[i++]);
        }
        return Map.copyOf(builder);
    }

    public List<Pair<T, AnalysisStatus>> getStatuses() {
        List<Pair<T, AnalysisStatus>> res = new LinkedList<>();
        int i = 0;
        for (T t : suppliers.keySet()) {
            res.add(new Pair<>(t, state[i++]));
        }
        return res;
    }
}
