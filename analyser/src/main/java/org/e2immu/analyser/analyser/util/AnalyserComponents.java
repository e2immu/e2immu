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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.model.InfoObject;
import org.e2immu.analyser.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;

/**
 * @param <T> typically String, as the label of the component. In case of the primary type analyser, T is Analyser.
 * @param <S> shared state
 */
public class AnalyserComponents<T, S> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyserComponents.class);

    public static class Info {
        private int cnt;
        private final Map<CauseOfDelay.Cause, Integer> causes = new HashMap<>();

        public Info(CauseOfDelay c) {
            add(c);
        }

        public int getCnt() {
            return cnt;
        }

        @Override
        public String toString() {
            return cnt + " =  " + causes.entrySet().stream().sorted((e1, e2) -> e2.getValue() - e1.getValue()).map(e -> e.getKey().label + "=" + e.getValue()).collect(Collectors.joining(", "));
        }

        private Info add(CauseOfDelay c) {
            cnt++;
            causes.merge(c.cause(), 1, Integer::sum);
            return this;
        }
    }

    private final LinkedHashMap<T, AnalysisResultSupplier<S>> suppliers;
    private final AnalysisStatus[] state;
    private final boolean limitCausesOfDelay;
    private final Map<InfoObject, Info> delayHistogram;
    private final Function<S, S> updateUponProgress;
    private final BiPredicate<T, S> executeConditionally;

    private AnalyserComponents(boolean limitCausesOfDelay,
                               LinkedHashMap<T, AnalysisResultSupplier<S>> suppliers,
                               Function<S, S> updateUponProgress,
                               BiPredicate<T, S> executeConditionally) {
        this.suppliers = suppliers;
        state = new AnalysisStatus[suppliers.size()];
        Arrays.fill(state, AnalysisStatus.NOT_YET_EXECUTED);
        this.limitCausesOfDelay = limitCausesOfDelay;
        if (limitCausesOfDelay) {
            delayHistogram = new HashMap<>();
        } else {
            delayHistogram = null;
        }
        this.updateUponProgress = updateUponProgress;
        this.executeConditionally = executeConditionally;
    }

    public AnalysisStatus getStatus(String t) {
        return getStatusesAsMap().get(t);
    }

    public void resetDelayHistogram() {
        if (delayHistogram != null) delayHistogram.clear();
    }

    public static class Builder<T, S> {
        private final LinkedHashMap<T, AnalysisStatus.AnalysisResultSupplier<S>> suppliers = new LinkedHashMap<>();
        private boolean limitCausesOfDelay;
        private Function<S, S> updateUponProgress;
        private BiPredicate<T, S> executeConditionally;


        public Builder<T, S> setLimitCausesOfDelay(boolean limitCausesOfDelay) {
            this.limitCausesOfDelay = limitCausesOfDelay;
            return this;
        }

        public Builder<T, S> add(T t, AnalysisResultSupplier<S> supplier) {
            if (suppliers.put(t, supplier) != null) throw new UnsupportedOperationException();
            return this;
        }

        public Builder<T, S> setUpdateUponProgress(Function<S, S> updateUponProgress) {
            this.updateUponProgress = updateUponProgress;
            return this;
        }

        public Builder<T, S> setExecuteConditionally(BiPredicate<T, S> executeConditionally) {
            this.executeConditionally = executeConditionally;
            return this;
        }

        public AnalyserComponents<T, S> build() {
            return new AnalyserComponents<>(limitCausesOfDelay, suppliers, updateUponProgress, executeConditionally);
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
    public AnalysisStatus run(S sIn) {
        int i = 0;
        AnalysisStatus combined = DONE;
        boolean progress = false;
        S s = sIn;
        for (Map.Entry<T, AnalysisStatus.AnalysisResultSupplier<S>> entry : suppliers.entrySet()) {
            AnalysisStatus.AnalysisResultSupplier<S> supplier = entry.getValue();
            AnalysisStatus initialState = state[i];
            if (initialState != DONE) {
                if (executeConditionally == null || executeConditionally.test(entry.getKey(), s)) {
                    // execute
                    AnalysisStatus afterExec = supplier.apply(s);
                    assert afterExec != NOT_YET_EXECUTED;
                    if (afterExec == DONE || afterExec == DONE_ALL || afterExec.isProgress()) {
                        if (!progress) {
                            LOGGER.debug("First progress in {}", entry.getKey());
                        }
                        progress = true;
                        if (updateUponProgress != null) {
                            s = updateUponProgress.apply(s);
                        }
                    }
                    if (afterExec == DONE_ALL) {
                        while (i < state.length) {
                            state[i++] = DONE;
                        }
                        break; // out of the for loop!
                    }
                    if (afterExec.isDelayed() && delayHistogram != null) {
                        afterExec.causesOfDelay().causesStream().forEach(c ->
                                delayHistogram.merge(c.location().getInfo(), new Info(c), (i1, i2) -> i1.add(c)));
                    }
                    state[i] = afterExec;
                    if (afterExec != RUN_AGAIN) {
                        assert afterExec.isDelayed() || afterExec == DONE;
                        combined = combined.combine(afterExec, limitCausesOfDelay);
                    }
                } else {
                    // we're skipping analysers which are delayed, so we can't have a DONE...
                    if (combined.isDone()) {
                        combined = AnalysisStatus.of(CausesOfDelay.MIN_INT_DV);
                    }
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

    public Map<InfoObject, Info> getDelayHistogram() {
        return delayHistogram;
    }
}
