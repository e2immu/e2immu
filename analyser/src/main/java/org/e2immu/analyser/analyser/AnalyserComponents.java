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

import java.util.Arrays;
import java.util.List;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;

public class AnalyserComponents {

    private final List<AnalysisStatus.AnalysisResultSupplier> suppliers;
    private final AnalysisStatus[] state;

    public AnalyserComponents(List<AnalysisStatus.AnalysisResultSupplier> suppliers) {
        this.suppliers = suppliers;
        state = new AnalysisStatus[suppliers.size()];
        Arrays.fill(state, DELAYS);
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
        for (AnalysisStatus.AnalysisResultSupplier supplier : suppliers) {
            AnalysisStatus initialState = state[i];
            if (initialState != DONE) {
                AnalysisStatus afterExec = supplier.apply(iteration);
                if (afterExec == PROGRESS) changes = true;
                if (afterExec != DONE) allDone = false;
                if (afterExec != initialState) changes = true;
            }
            i++;
        }
        return allDone ? DONE : changes ? PROGRESS : DELAYS;
    }
}
