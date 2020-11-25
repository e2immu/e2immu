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

import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.VariableValue;
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
                new LocalVariable(List.of(), name, primitives.intParameterizedType, List.of()),
                List.of());
    }

    protected Variable makeLocalBooleanVar(String name) {
        return new LocalVariableReference(inspectionProvider,
                new LocalVariable(List.of(), name, primitives.booleanParameterizedType, List.of()),
                List.of());
    }

    protected Variable makeReturnVariable() {
        return new ReturnVariable(primitives.orOperatorBool);
    }

    protected final IntValue two = new IntValue(primitives, 2, ObjectFlow.NO_FLOW);
    protected final IntValue three = new IntValue(primitives, 3, ObjectFlow.NO_FLOW);
    protected final IntValue four = new IntValue(primitives, 4, ObjectFlow.NO_FLOW);

    protected final AnalyserContext analyserContext = new AnalyserContext() {
    };

    protected final EvaluationContext minimalEvaluationContext = new EvaluationContext() {

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Value currentValue(Variable variable) {
            return new VariableValue(variable);
        }

        @Override
        public boolean isNotNull0(Value value) {
            return false; // no opinion
        }

        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public ConditionManager getConditionManager() {
            return ConditionManager.INITIAL;
        }
    };

}
