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

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.AddOnceSet;

import java.util.Objects;
import java.util.stream.Stream;

public abstract class AbstractAnalyser implements Analyser {
    public final AnalyserContext analyserContext;
    public final String name;

    protected AbstractAnalyser(String name, AnalyserContext analyserContext) {
        this.analyserContext = Objects.requireNonNull(analyserContext);
        this.name = name;
    }

    protected final Messages messages = new Messages();

    @Override
    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    protected AddOnceSet<ObjectFlow> internalObjectFlows = new AddOnceSet<>();

    protected Value getVariableValue(Variable variable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

}
