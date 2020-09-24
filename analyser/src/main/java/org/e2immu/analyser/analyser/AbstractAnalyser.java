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

import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.EvaluationResult;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.ValueWithVariable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.AddOnceSet;
import org.e2immu.analyser.util.SetOnceMap;

import java.util.stream.Stream;

public abstract class AbstractAnalyser implements Analyser {
    public final AnalyserContext analyserContext;

    protected AbstractAnalyser(AnalyserContext analyserContext) {
        this.analyserContext = analyserContext;
    }

    protected final Messages messages = new Messages();

    protected void apply(EvaluationResult evaluationResult) {

    }

    @Override
    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    protected Variable variableByName(String variableName) {
        throw new UnsupportedOperationException();
    }

    protected AddOnceSet<ObjectFlow> internalObjectFlows = new AddOnceSet<>();

    protected ValueWithVariable getVariableValue(Variable variable) {
        throw new UnsupportedOperationException();
    }
}
