/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableMap;
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
        Arrays.fill(state, DELAYS);
    }

    public AnalysisStatus getStatus(T t) {
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


    public AnalysisStatus run(S s) {
        int i = 0;
        boolean allDone = true;
        boolean changes = false;
        for (AnalysisStatus.AnalysisResultSupplier<S> supplier : suppliers.values()) {
            AnalysisStatus initialState = state[i];
            if (initialState != DONE) {
                AnalysisStatus afterExec = supplier.apply(s);
                state[i] = afterExec;
                if (afterExec == PROGRESS) changes = true;
                if (afterExec != DONE) allDone = false;
                if (afterExec != initialState) changes = true;
            }
            i++;
        }
        return allDone ? DONE : changes ? PROGRESS : DELAYS;
    }

    public String details() {
        StringBuilder sb = new StringBuilder();
        getStatuses().forEach(p -> sb.append(p.k).append(": ").append(p.v).append("\n"));
        return sb.toString();
    }

    public Map<String, AnalysisStatus> getStatusesAsMap() {
        ImmutableMap.Builder<String, AnalysisStatus> builder = new ImmutableMap.Builder<>();
        int i = 0;
        for (T t : suppliers.keySet()) {
            builder.put(t.toString(), state[i++]);
        }
        return builder.build();
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
