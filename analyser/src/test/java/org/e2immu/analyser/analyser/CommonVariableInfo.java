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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;

public abstract class CommonVariableInfo {

    protected final Primitives primitives = new Primitives();
    protected final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);

    protected Variable makeLocalIntVar(String name) {
        return new LocalVariableReference(inspectionProvider,
                // owningType is completely arbitrary
                new LocalVariable.Builder()
                        .setName(name)
                        .setParameterizedType(primitives.intParameterizedType)
                        .setOwningType(primitives.stringTypeInfo)
                        .build(),
                List.of());
    }

    protected Variable makeLocalBooleanVar(String name) {
        return new LocalVariableReference(inspectionProvider,
                new LocalVariable.Builder()
                        .setName(name)
                        .setParameterizedType(primitives.booleanParameterizedType)
                        .setOwningType(primitives.stringTypeInfo)
                        .build(),
                List.of());
    }

    protected Variable makeReturnVariable() {
        return new ReturnVariable(primitives.orOperatorBool);
    }

    protected final IntConstant two = new IntConstant(primitives, 2, ObjectFlow.NO_FLOW);
    protected final IntConstant three = new IntConstant(primitives, 3, ObjectFlow.NO_FLOW);
    protected final IntConstant four = new IntConstant(primitives, 4, ObjectFlow.NO_FLOW);

    protected final BooleanConstant TRUE = new BooleanConstant(primitives, true);

    protected final AnalyserContext analyserContext = new AnalyserContext() {
    };

    protected final EvaluationContext minimalEvaluationContext = new EvaluationContext() {

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
            return new VariableExpression(variable);
        }

        @Override
        public boolean isNotNull0(Expression value, boolean useEnnInsteadOfCnn) {
            return !(value instanceof NullConstant); // no opinion
        }

        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public ConditionManager getConditionManager() {
            return ConditionManager.initialConditionManager(primitives);
        }

        @Override
        public String newObjectIdentifier() {
            return "-";
        }
    };

}
