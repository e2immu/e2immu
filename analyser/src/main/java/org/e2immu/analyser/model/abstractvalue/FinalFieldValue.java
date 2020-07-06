/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;


public class FinalFieldValue extends ValueWithVariable {

    private final FieldAnalysis fieldAnalysis;

    public FinalFieldValue(Variable variable, ObjectFlow objectFlow) {
        super(variable, objectFlow);
        this.fieldAnalysis = ((FieldReference) variable).fieldInfo.fieldAnalysis.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FinalFieldValue that = (FinalFieldValue) o;
        return variable.equals(that.variable);
    }

    @Override
    public int getPropertyOutsideContext(VariableProperty variableProperty) {
        return fieldAnalysis.getProperty(variableProperty);
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return fieldAnalysis.getProperty(variableProperty);
    }

}
