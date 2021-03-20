/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.stream.Collectors;

public class LocalClassDeclaration extends StatementWithStructure {
    public final TypeInfo typeInfo;

    public LocalClassDeclaration(TypeInfo typeInfo) {
        this.typeInfo = typeInfo;
    }

    @Override
    public TypeInfo definesType() {
        return typeInfo;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return this; // TODO we will need something more complicated here.
    }

    @Override
    public OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis) {
        return typeInfo.output(qualification, true);
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
