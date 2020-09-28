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

package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.annotation.Container;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

@Container
public class StatementAnalysis extends Analysis implements Comparable<StatementAnalysis>, HasNavigationData<StatementAnalysis> {

    public final Statement statement;
    public final String index;
    public final StatementAnalysis parent;

    public final ErrorFlags errorFlags = new ErrorFlags();
    public final NavigationData<StatementAnalysis> navigationData = new NavigationData<>();
    public final SetOnce<VariableDataImpl> variableData = new SetOnce<VariableDataImpl>();
    public final MethodLevelData methodLevelData = new MethodLevelData();
    public final StateData stateData = new StateData();

    public final SetOnce<Boolean> done = new SetOnce<>(); // if not done, there have been delays

    public StatementAnalysis(Statement statement, StatementAnalysis parent, String index) {
        super(true, index);
        this.index = super.simpleName;
        this.statement = statement;
        this.parent = parent;
    }

    public String toString() {
        return index + ": " + statement.getClass().getSimpleName();
    }

    @Override
    public int compareTo(StatementAnalysis o) {
        return index.compareTo(o.index);
    }

    @Override
    public NavigationData<StatementAnalysis> getNavigationData() {
        return navigationData;
    }

    public boolean inErrorState() {
        boolean parentInErrorState = parent != null && parent.inErrorState();
        if (parentInErrorState) return true;
        return errorFlags.errorValue.isSet() && errorFlags.errorValue.get();
    }

    public static StatementAnalysis startOfBlock(StatementAnalysis sa, int block) {
        return sa == null ? null : sa.startOfBlock(block);
    }

    private StatementAnalysis startOfBlock(int i) {
        if (!navigationData.blocks.isSet()) return null;
        List<StatementAnalysis> list = navigationData.blocks.get();
        return i >= list.size() ? null : list.get(i);
    }

    @Override
    public StatementAnalysis followReplacements() {
        if (navigationData.replacement.isSet()) {
            return navigationData.replacement.get().followReplacements();
        }
        return this;
    }

    @Override
    public String index() {
        return index;
    }

    @Override
    public Statement statement() {
        return statement;
    }

    @Override
    public StatementAnalysis lastStatement() {
        return followReplacements().navigationData.next.get().map(StatementAnalysis::lastStatement).orElse(this);
    }

    @Override
    public StatementAnalysis parent() {
        return parent;
    }

    @Override
    public void wireNext(StatementAnalysis newStatement) {
        navigationData.next.set(Optional.ofNullable(newStatement));
    }

    @Override
    public BiFunction<List<Statement>, String, StatementAnalysis> generator(EvaluationContext evaluationContext) {
        return (statements, startIndex) -> recursivelyCreateAnalysisObjects(parent(), statements, startIndex, false);
    }

    public static StatementAnalysis recursivelyCreateAnalysisObjects(
            StatementAnalysis parent,
            List<Statement> statements,
            String indices,
            boolean setNextAtEnd) {
        int statementIndex;
        if (setNextAtEnd) {
            statementIndex = 0;
        } else {
            // we're in the replacement mode; replace the existing index value
            int pos = indices.lastIndexOf(".");
            statementIndex = Integer.parseInt(pos < 0 ? indices : indices.substring(pos + 1));
        }
        StatementAnalysis first = null;
        StatementAnalysis previous = null;
        for (Statement statement : statements) {
            String iPlusSt = indices + "." + statementIndex;
            StatementAnalysis statementAnalysis = new StatementAnalysis(statement, parent, iPlusSt);
            if (previous != null) {
                previous.navigationData.next.set(Optional.of(statementAnalysis));
            }
            previous = statementAnalysis;
            if (first == null) first = statementAnalysis;

            int blockIndex = 0;
            List<StatementAnalysis> analysisBlocks = new ArrayList<>();

            Structure structure = statement.getStructure();
            if (structure.haveStatements()) {
                StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(parent, statements, iPlusSt + "." + blockIndex, true);
                analysisBlocks.add(subStatementAnalysis);
                blockIndex++;
            }
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    StatementAnalysis subStatementAnalysis = recursivelyCreateAnalysisObjects(parent, statements, iPlusSt + "." + blockIndex, true);
                    analysisBlocks.add(subStatementAnalysis);
                    blockIndex++;
                }
            }
            statementAnalysis.navigationData.blocks.set(ImmutableList.copyOf(analysisBlocks));
            ++statementIndex;
        }
        if (previous != null && setNextAtEnd) {
            previous.navigationData.next.set(Optional.empty());
        }
        return first;
    }

    public interface StateChange extends Function<Value, Value> {

    }

    public interface StatementAnalysisModification extends Runnable {
        // nothing extra at the moment
    }

    public void apply(StatementAnalysisModification modification) {
        modification.run();
    }


    @Override
    public AnnotationMode annotationMode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Location location() {
        throw new UnsupportedOperationException();
    }
}
