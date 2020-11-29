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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;

public class UnknownValue implements Value {

    // used if we don't know yet which value a variable will have
    public static final UnknownValue NO_VALUE = new UnknownValue("<no value>");

    // used as default value for condition and state in ConditionManager; as default in SWITCH statements
    public static final UnknownValue EMPTY = new UnknownValue("<empty>");

    public static final UnknownValue RETURN_VALUE = new UnknownValue("<return value>");
    public static final UnknownValue NO_RETURN_VALUE = new UnknownValue("<no return value>");

    private final String msg;

    private UnknownValue(String msg) {
        this.msg = msg;
    }

    @Override
    public boolean isUnknown() {
        return true;
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }

    @Override
    public int order() {
        return ORDER_NO_VALUE;
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return msg;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return this == NO_VALUE ? Level.DELAY : Level.FALSE;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NO_FLOW;
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return null;
    }
}