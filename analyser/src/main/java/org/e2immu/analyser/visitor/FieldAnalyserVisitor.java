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

package org.e2immu.analyser.visitor;

import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.parser.Message;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface FieldAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration,
                EvaluationContext evaluationContext,
                FieldInfo fieldInfo,
                FieldAnalysis fieldAnalysis,
                Supplier<Stream<Message>> messageStream,
                Map<String, AnalysisStatus> statuses) {

        public int getProperty(Expression value, VariableProperty variableProperty) {
            return evaluationContext.getProperty(value, variableProperty, false);
        }

        public String haveError(String message) {
            return messageStream.get()
                    .filter(m -> m.message.contains(message))
                    .map(Message::toString)
                    .findFirst()
                    .orElse(null);
        }
    }
}
