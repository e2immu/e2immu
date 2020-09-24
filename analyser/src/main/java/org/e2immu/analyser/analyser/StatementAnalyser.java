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

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.annotation.Container;

import java.util.Map;
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
    private final TypeAnalyser myTypeAnalyser;
    private final MethodAnalyser myMethodAnalyser;

    public StatementAnalyser(AnalyserContext analyserContext) {
        super(analyserContext);
        statementAnalysis = new StatementAnalysis(enclosingMethod, statement, parent, index);
    }

    public void apply(EvaluationResult evaluationResult, StatementAnalysis previous) {
        if (evaluationResult.value != UnknownValue.NO_VALUE && !statementAnalysis.valueOfExpression.isSet()) {
            statementAnalysis.valueOfExpression.set(evaluationResult.value);
        }

        // all modifications get applied
        evaluationResult.getModificationStream().forEach(statementAnalysis::apply);

        // state changes get composed into one big operation, applied, and the result set
        if (!statementAnalysis.state.isSet()) {
            Value state = previous == null ? UnknownValue.EMPTY : previous.state.get();
            Function<Value, Value> composite = evaluationResult.getStateChangeStream()
                    .reduce(v -> v, (f1, f2) -> v -> f2.apply(f1.apply(v)));
            Value reduced = composite.apply(state);
            if (reduced != UnknownValue.NO_VALUE) {
                statementAnalysis.state.set(reduced);
            }
        }
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
            return myTypeAnalyser;
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
            if (variable instanceof FieldReference) {
                FieldReference fieldReference = (FieldReference) variable;
                TransferValue tv;
                if (!myMethodAnalyser.methodAnalysis.fieldSummaries.isSet(fieldReference.fieldInfo)) {
                    tv = new TransferValue();
                    myMethodAnalyser.methodAnalysis.fieldSummaries.put(fieldReference.fieldInfo, tv);
                } else {
                    tv = myMethodAnalyser.methodAnalysis.fieldSummaries.get(fieldReference.fieldInfo);
                }
                if (tv.value.isSet()) {
                    return tv.value.get();
                }
                // no value (yet)
                return UnknownValue.NO_VALUE;
            }
            if (variable instanceof This) {
                This thisVariable = (This) variable;
                TypeAnalyser typeAnalyser = getAnalyserContext().getTypeAnalysers().get(thisVariable.typeInfo);
                return typeAnalyser.thisVariableValue;
            }
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
