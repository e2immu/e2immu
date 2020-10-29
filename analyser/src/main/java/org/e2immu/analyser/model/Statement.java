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

package org.e2immu.analyser.model;

import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;

import java.util.List;

@E2Container
public interface Statement extends Element {

    Structure getStructure();

    @Override
    default Statement translate(TranslationMap translationMap) {
        return this;
    }

    String statementString(int indent, StatementAnalysis statementAnalysis);

    // IfElse and Switch statements will implement this
    default Value addToStateAfterStatement(EvaluationContext evaluationContext, List<Value> conditions, List<Boolean> escapes) {
        return UnknownValue.EMPTY;
    }
}
