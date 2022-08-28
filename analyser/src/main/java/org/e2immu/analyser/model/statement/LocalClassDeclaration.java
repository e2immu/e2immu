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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LocalClassDeclaration extends StatementWithStructure {
    public final TypeInfo typeInfo;
    private final List<MethodInspection> methodAndConstructorInspections;

    public LocalClassDeclaration(Identifier identifier,
                                 TypeInfo typeInfo,
                                 List<MethodInspection> methodAndConstructorInspections) {
        super(identifier);
        this.typeInfo = typeInfo;
        this.methodAndConstructorInspections = methodAndConstructorInspections;
        assert methodAndConstructorInspections.stream().noneMatch(Objects::isNull);
    }

    @Override
    public TypeInfo definesType() {
        return typeInfo;
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        return List.of(this); // TODO we will need something more complicated here.
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        return typeInfo.output(qualification, true);
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return typeInfo.typesReferenced();
    }

    @Override
    public List<? extends Element> subElements() {
        return methodAndConstructorInspections.stream()
                .map(mi -> Objects.requireNonNull(mi.getMethodBody(),
                        "No method body for " + mi.getMethodInfo().fullyQualifiedName))
                .collect(Collectors.toList());
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            methodAndConstructorInspections.forEach(i -> i.getMethodBody().visit(predicate));
        }
    }
}
