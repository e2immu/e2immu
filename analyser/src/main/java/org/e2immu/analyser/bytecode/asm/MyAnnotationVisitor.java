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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.bytecode.ExpressionFactory;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Inspection;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.objectweb.asm.AnnotationVisitor;

import java.util.Objects;

import static org.e2immu.analyser.inspector.InspectionState.TRIGGER_BYTECODE_INSPECTION;
import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG;
import static org.e2immu.analyser.util.Logger.log;
import static org.objectweb.asm.Opcodes.ASM9;


public class MyAnnotationVisitor<T> extends AnnotationVisitor {
    private final TypeContext typeContext;
    private final Inspection.InspectionBuilder<T> inspectionBuilder;
    private final AnnotationExpressionImpl.Builder expressionBuilder;

    public MyAnnotationVisitor(TypeContext typeContext, String descriptor, Inspection.InspectionBuilder<T> inspectionBuilder) {
        super(ASM9);
        this.typeContext = typeContext;
        this.inspectionBuilder = Objects.requireNonNull(inspectionBuilder);
        log(BYTECODE_INSPECTOR_DEBUG, "My annotation visitor: {}", descriptor);
        FindType findType = (fqn, path) -> typeContext.typeMap.getOrCreateFromPath(path, TRIGGER_BYTECODE_INSPECTION);
        ParameterizedType type = ParameterizedTypeFactory.from(typeContext, findType, descriptor).parameterizedType;
        expressionBuilder = new AnnotationExpressionImpl.Builder().setTypeInfo(type.typeInfo);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        log(BYTECODE_INSPECTOR_DEBUG, "Annotation again: {}, {}", name, descriptor);
        return null;
    }

    @Override
    public void visit(String name, Object value) {
        log(BYTECODE_INSPECTOR_DEBUG, "Assignment: {} to {}", name, value);
        Expression expression = ExpressionFactory.from(typeContext, value);
        MemberValuePair mvp = new MemberValuePair(name, expression);
        expressionBuilder.addExpression(mvp);
    }

    @Override
    public void visitEnd() {
        inspectionBuilder.addAnnotation(expressionBuilder.build());
    }
}
