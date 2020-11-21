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
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.stream.Collectors;

public class LocalClassDeclaration extends StatementWithStructure {
    public final TypeInfo typeInfo;

    public LocalClassDeclaration(TypeInfo typeInfo) {
        this.typeInfo = typeInfo;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return this; // TODO we will need something more complicated here.
    }

    @Override
    public String statementString(int indent, StatementAnalysis statementAnalysis) {
        return typeInfo.stream(indent);
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return typeInfo.typesReferenced();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return SideEffect.LOCAL;
    }

    @Override
    public List<? extends Element> subElements() {
        return typeInfo.typeInspection.get().methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                .map(methodInfo -> methodInfo.methodInspection.get().getMethodBody()).collect(Collectors.toList());
    }
}
