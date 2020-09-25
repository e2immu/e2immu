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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.Container;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/*
block analyser: organises a list of statement analysers, organises the recursions


statement analyser: creates and modifies statement analysis
applies EvaluationResults to statement analysis


 */

@Container(builds = StatementAnalysis.class)
public class StatementAnalyser extends AbstractAnalyser {

    public final StatementAnalysis statementAnalysis;
    private final MethodAnalyser myMethodAnalyser;

    public SetOnce<List<StatementAnalyser>> blocks = new SetOnce<>();
    public SetOnce<Optional<StatementAnalyser>> next = new SetOnce<>();
    public final SetOnce<StatementAnalyser> replacement = new SetOnce<>();

    private StatementAnalyser(AnalyserContext analyserContext,
                              MethodAnalyser methodAnalyser,
                              Statement statement,
                              StatementAnalysis parent,
                              String index) {
        super(analyserContext);
        this.myMethodAnalyser = methodAnalyser;
        this.statementAnalysis = new StatementAnalysis(statement, parent, index);
    }

    public static StatementAnalyser recursivelyCreateAnalysisObjects(
            AnalyserContext analyserContext,
            MethodAnalyser myMethodAnalyser,
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
        StatementAnalyser first = null;
        StatementAnalyser previous = null;
        for (Statement statement : statements) {
            String iPlusSt = indices + "." + statementIndex;
            StatementAnalyser statementAnalyser = new StatementAnalyser(analyserContext, myMethodAnalyser, statement, parent, iPlusSt);
            if (previous != null) {
                previous.statementAnalysis.navigationData.next.set(Optional.of(statementAnalyser.statementAnalysis));
                previous.next.set(Optional.of(statementAnalyser));
            }
            previous = statementAnalyser;
            if (first == null) first = statementAnalyser;

            int blockIndex = 0;
            List<StatementAnalyser> blocks = new ArrayList<>();
            List<StatementAnalysis> analysisBlocks = new ArrayList<>();

            Structure structure = statement.getStructure();
            if (structure.haveStatements()) {
                StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser, parent, statements, iPlusSt + "." + blockIndex, true);
                blocks.add(subStatementAnalyser);
                analysisBlocks.add(subStatementAnalyser.statementAnalysis);
                blockIndex++;
            }
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    StatementAnalyser subStatementAnalyser = recursivelyCreateAnalysisObjects(analyserContext, myMethodAnalyser, parent, statements, iPlusSt + "." + blockIndex, true);
                    blocks.add(subStatementAnalyser);
                    analysisBlocks.add(subStatementAnalyser.statementAnalysis);
                    blockIndex++;
                }
            }
            statementAnalyser.statementAnalysis.navigationData.blocks.set(ImmutableList.copyOf(analysisBlocks));
            statementAnalyser.blocks.set(ImmutableList.copyOf(blocks));
            ++statementIndex;
        }
        if (previous != null && setNextAtEnd) {
            previous.statementAnalysis.navigationData.next.set(Optional.empty());
            previous.next.set(Optional.empty());
        }
        return first;

    }

    public void apply(EvaluationResult evaluationResult, StatementAnalysis previous) {
        statementAnalysis.stateData.apply(evaluationResult, previous == null ? null : previous.stateData);

        // all modifications get applied
        evaluationResult.getModificationStream().forEach(statementAnalysis::apply);
    }

    public StatementAnalyserResult update(int iteration) {
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration);
        StatementAnalyserResult result = statementAnalysis.methodLevelData.update(evaluationContext);

        return result;
    }

    @Override
    public void check() {

    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean analyse(int iteration) {
        return false;
    }

    @Override
    public void initialize() {

    }

    @Override
    public Analysis getAnalysis() {
        return statementAnalysis;
    }


    private class EvaluationContextImpl implements EvaluationContext {

        private final int iteration;

        private EvaluationContextImpl(int iteration) {
            this.iteration = iteration;
        }

        @Override
        public int getIteration() {
            return iteration;
        }

        @Override
        public TypeAnalyser getCurrentType() {
            return myMethodAnalyser.myTypeAnalyser;
        }

        @Override
        public MethodAnalyser getCurrentMethod() {
            return myMethodAnalyser;
        }

        @Override
        public MethodAnalysis getCurrentMethodAnalysis() {
            return myMethodAnalyser.methodAnalysis;
        }

        @Override
        public ObjectFlow getObjectFlow(Variable variable) {
            return null;
        }

        @Override
        public FieldAnalyser getCurrentField() {
            return null;
        }

        @Override
        public StatementAnalyser getCurrentStatement() {
            return StatementAnalyser.this;
        }

        @Override
        public Location getLocation() {
            return new Location(myMethodAnalyser.methodInfo, statementAnalysis.index);
        }

        @Override
        public EvaluationContext child(Value condition, Runnable uponUsingConditional, boolean guaranteedToBeReachedByParentStatement) {
            return null;
        }

        @Override
        public Value currentValue(Variable variable) {

            return null;
        }

        @Override
        public Value currentValue(String variableName) {
            return currentValue(variableByName(variableName));
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Stream<ObjectFlow> getInternalObjectFlows() {
            return null;
        }
    }
}
