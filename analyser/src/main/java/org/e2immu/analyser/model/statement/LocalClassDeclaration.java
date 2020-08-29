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

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;

import java.util.Map;
import java.util.Set;

public class LocalClassDeclaration implements Statement {
    public final TypeInfo typeInfo;

    public LocalClassDeclaration(TypeInfo typeInfo) {
        this.typeInfo = typeInfo;
    }

    @Override
    public Statement translate(Map<? extends Variable, ? extends Variable> translationMap) {
        return this; // TODO we will need something more complicated here.
    }

    @Override
    public String statementString(int indent) {
        return typeInfo.stream(indent);
    }

    @Override
    public Set<String> imports() {
        return typeInfo.imports();
    }

    @Override
    public Set<TypeInfo> typesReferenced() {
        return typeInfo.typesReferenced();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return SideEffect.LOCAL;
    }
}
