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

import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analyser.util.AnalyserComponents;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;

public interface Analyser extends Comparable<Analyser> {


    record SharedState(int iteration, BreakDelayLevel breakDelayLevel, EvaluationContext closure) {
        private static final Logger LOGGER = LoggerFactory.getLogger(Analyser.class);

        public SharedState removeAllowBreakDelay() {
            if (breakDelayLevel.isActive()) LOGGER.debug("**** Removing allow break delay ****");
            return new SharedState(iteration, BreakDelayLevel.NONE, closure);
        }

        public SharedState setBreakDelayLevel(BreakDelayLevel breakDelayLevel) {
            if (breakDelayLevel == this.breakDelayLevel) return this;
            return new SharedState(iteration, breakDelayLevel, closure);
        }
    }

    enum AnalyserIdentification {
        TYPE(IMMUTABLE, CONTAINER, null, null, false), // type does not have notNull
        ABSTRACT_TYPE(IMMUTABLE, CONTAINER, null, null, true), // type does not have notNull
        FIELD(EXTERNAL_IMMUTABLE, CONTAINER_RESTRICTION, EXTERNAL_NOT_NULL, EXTERNAL_IGNORE_MODIFICATIONS, false),
        PARAMETER(IMMUTABLE, CONTAINER_RESTRICTION, NOT_NULL_PARAMETER, IGNORE_MODIFICATIONS, false),
        METHOD(IMMUTABLE, CONTAINER, NOT_NULL_EXPRESSION, null, false);

        public final Property immutable;
        public final Property container;
        public final Property notNull;
        public final Property ignoreMods;
        public final boolean isAbstract;

        AnalyserIdentification(Property immutable, Property container, Property notNull, Property ignoreMods, boolean isAbstract) {
            this.notNull = notNull;
            this.container = container;
            this.immutable = immutable;
            this.ignoreMods = ignoreMods;
            this.isAbstract = isAbstract;
        }
    }

    // four stages

    @Modified
    void initialize();

    /*
    closure is null when called from primary type analyser, is not null when a type/method/... is being
    analysed from the statement analyser
     */
    @Modified
    @NotNull
    AnalyserResult analyse(SharedState sharedState);

    @Modified
    void write();

    @Modified
    void check();

    @Modified
    boolean makeUnreachable();

    @NotModified
    boolean isUnreachable();
    // other methods

    @NotNull
    WithInspectionAndAnalysis getMember();

    Analysis getAnalysis();

    @NotNull
    String getName();

    AnalyserComponents<String, ?> getAnalyserComponents();

    void receiveAdditionalTypeAnalysers(Collection<PrimaryTypeAnalyser> typeAnalysers);

    void makeImmutable();

    Stream<Message> getMessageStream();

    String fullyQualifiedAnalyserName();

    @Override
    default int compareTo(Analyser o) {
        return fullyQualifiedAnalyserName().compareTo(o.fullyQualifiedAnalyserName());
    }
}
