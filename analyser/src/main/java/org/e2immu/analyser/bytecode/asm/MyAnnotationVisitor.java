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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.bytecode.ExpressionFactory;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.parser.TypeContext;
import org.objectweb.asm.AnnotationVisitor;

import java.util.Objects;

import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG;
import static org.e2immu.analyser.util.Logger.log;
import static org.objectweb.asm.Opcodes.ASM7;


public class MyAnnotationVisitor<T> extends AnnotationVisitor {
    private final TypeContext typeContext;
    private final AbstractInspectionBuilder<T> inspectionBuilder;
    private final AnnotationExpressionImpl.Builder expressionBuilder;

    public MyAnnotationVisitor(TypeContext typeContext, String descriptor, AbstractInspectionBuilder<T> inspectionBuilder) {
        super(ASM7);
        this.typeContext = typeContext;
        this.inspectionBuilder = Objects.requireNonNull(inspectionBuilder);
        log(BYTECODE_INSPECTOR_DEBUG, "My annotation visitor: {}", descriptor);
        ParameterizedType type = ParameterizedTypeFactory.from(typeContext, descriptor).parameterizedType;
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
        if ("value".equals(name)) {
            expressionBuilder.addExpression(expression);
        } else {
            MemberValuePair mvp = new MemberValuePair(name, expression);
            expressionBuilder.addExpression(mvp);
        }
    }

    @Override
    public void visitEnd() {
        inspectionBuilder.addAnnotation(expressionBuilder.build());
    }
}
