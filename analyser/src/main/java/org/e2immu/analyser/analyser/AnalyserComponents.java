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

import org.e2immu.analyser.util.Pair;

import java.util.*;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;

public class AnalyserComponents<T> {

    private final LinkedHashMap<T, AnalysisStatus.AnalysisResultSupplier> suppliers;
    private final AnalysisStatus[] state;

    private AnalyserComponents(LinkedHashMap<T, AnalysisResultSupplier> suppliers) {
        this.suppliers = suppliers;
        state = new AnalysisStatus[suppliers.size()];
        Arrays.fill(state, DELAYS);
    }

    static class Builder<T> {
        private final LinkedHashMap<T, AnalysisStatus.AnalysisResultSupplier> suppliers = new LinkedHashMap<>();

        public Builder<T> add(T t, AnalysisResultSupplier supplier) {
            if (suppliers.put(t, supplier) != null) throw new UnsupportedOperationException();
            return this;
        }

        public AnalyserComponents<T> build() {
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


    public AnalysisStatus run(int iteration) {
        int i = 0;
        boolean allDone = true;
        boolean changes = false;
        for (AnalysisStatus.AnalysisResultSupplier supplier : suppliers.values()) {
            AnalysisStatus initialState = state[i];
            if (initialState != DONE) {
                AnalysisStatus afterExec = supplier.apply(iteration);
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

    public List<Pair<T, AnalysisStatus>> getStatuses() {
        List<Pair<T, AnalysisStatus>> res = new LinkedList<>();
        int i = 0;
        for (T t : suppliers.keySet()) {
            res.add(new Pair<>(t, state[i++]));
        }
        return res;
    }
}
