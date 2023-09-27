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

package org.e2immu.analyser.analysis;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.AnalyserComponents;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;

/**
 * IMPORTANT:
 * Method level data is incrementally copied from one statement to the next.
 * The method analyser will only investigate the data from the last statement in the method!
 */
public interface MethodLevelData {

    CausesOfDelay combinedPreconditionIsDelayedSet();

    CausesOfDelay linksHaveNotYetBeenEstablished();

    Set<PostCondition> getPostConditions();

    boolean arePostConditionsDelayed();

    boolean combinedPreconditionIsFinal();

    Precondition combinedPreconditionGet();

    void internalAllDoneCheck();

    void makeUnreachable(Primitives primitives);


    AnalysisStatus analyse(StatementAnalyserSharedState sharedState,
                           StatementAnalysis statementAnalysis,
                           MethodLevelData previous,
                           String previousIndex,
                           StateData stateData);

    boolean linksHaveBeenEstablished();

    CausesOfDelay getLinksHaveBeenEstablished();

    Set<String> getIndicesOfEscapesNotInPreOrPostConditions();

    boolean staticSideEffectsHaveBeenFound();

    StaticSideEffects staticSideEffects();

    Stream<MethodInfo> copyModificationStatusFromKeyStream();

    void warnAboutAnalyserComponents(String statementIndex, String methodFqn);
}
