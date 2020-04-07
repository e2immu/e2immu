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

import org.e2immu.analyser.bytecode.JetBrainsAnnotationTranslator;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.annotationxml.model.MethodItem;
import org.e2immu.analyser.annotationxml.model.ParameterItem;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG;
import static org.e2immu.analyser.util.Logger.log;
import static org.objectweb.asm.Opcodes.ASM7;

public class MyMethodVisitor extends MethodVisitor {
    private final TypeInspection.TypeInspectionBuilder typeInspectionBuilder;
    private final TypeContext typeContext;
    private final MethodInfo methodInfo;
    private final MethodInspection.MethodInspectionBuilder methodInspectionBuilder;
    private final List<ParameterizedType> types;
    private final ParameterInspection.ParameterInspectionBuilder[] parameterInspectionBuilders;
    private final int numberOfParameters;
    private final JetBrainsAnnotationTranslator jetBrainsAnnotationTranslator;
    private final MethodItem methodItem;
    private int countLocalVars;
    private boolean[] hasNameFromLocalVar;

    public MyMethodVisitor(TypeContext typeContext,
                           MethodInfo methodInfo,
                           MethodInspection.MethodInspectionBuilder methodInspectionBuilder,
                           TypeInspection.TypeInspectionBuilder typeInspectionBuilder,
                           List<ParameterizedType> types,
                           boolean lastParameterIsVarargs,
                           MethodItem methodItem,
                           JetBrainsAnnotationTranslator jetBrainsAnnotationTranslator) {
        super(ASM7);
        this.typeContext = typeContext;
        this.methodInfo = methodInfo;
        this.methodInspectionBuilder = methodInspectionBuilder;
        this.typeInspectionBuilder = typeInspectionBuilder;
        this.types = types;
        this.jetBrainsAnnotationTranslator = jetBrainsAnnotationTranslator;
        this.methodItem = methodItem;
        numberOfParameters = types.size() - 1;
        hasNameFromLocalVar = new boolean[numberOfParameters];
        parameterInspectionBuilders = new ParameterInspection.ParameterInspectionBuilder[numberOfParameters];

        for (int i = 0; i < numberOfParameters; i++) {
            parameterInspectionBuilders[i] = new ParameterInspection.ParameterInspectionBuilder();
            if(lastParameterIsVarargs && i == numberOfParameters-1) {
                parameterInspectionBuilders[i].setVarArgs(true);
            }
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        log(BYTECODE_INSPECTOR_DEBUG, "Have method annotation {} {}", descriptor, visible);
        return new MyAnnotationVisitor<>(typeContext, descriptor, methodInspectionBuilder);
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
        log(BYTECODE_INSPECTOR_DEBUG, "Have parameter annotation {} on parameter {}", descriptor, parameter);
        return new MyAnnotationVisitor<>(typeContext, descriptor, parameterInspectionBuilders[parameter]);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        int parameterIndex = countLocalVars - (methodInfo.isStatic ? 0 : 1);
        if (parameterIndex >= 0 && parameterIndex < numberOfParameters) {
            ParameterizedType parameterizedType = types.get(parameterIndex);
            ParameterInfo parameterInfo = new ParameterInfo(parameterizedType, name, parameterIndex);
            parameterInfo.parameterInspection.set(parameterInspectionBuilders[parameterIndex].build(methodInfo));
            methodInspectionBuilder.addParameter(parameterInfo);
            hasNameFromLocalVar[parameterIndex] = true;
        }
        countLocalVars++;
    }

    @Override
    public void visitEnd() {
        ParameterNameFactory factory = new ParameterNameFactory();
        for (int i = 0; i < numberOfParameters; i++) {
            if (!hasNameFromLocalVar[i]) {
                ParameterizedType type = types.get(i);
                String parameterName = factory.next(type);
                ParameterInfo parameterInfo = new ParameterInfo(type, parameterName, i);
                methodInspectionBuilder.addParameter(parameterInfo);

                parameterInfo.parameterInspection.set(parameterInspectionBuilders[i].build(methodInfo));
                log(BYTECODE_INSPECTOR_DEBUG, "Set parameterInspection {}", i);
            }
        }
        if (methodItem != null) {
            for (ParameterItem parameterItem : methodItem.getParameterItems()) {
                if (parameterItem.index < parameterInspectionBuilders.length) {
                    if (!parameterItem.getAnnotations().isEmpty()) {
                        jetBrainsAnnotationTranslator.mapAnnotations(parameterItem.getAnnotations(),
                                parameterInspectionBuilders[parameterItem.index]);
                    }
                } else {
                    log(BYTECODE_INSPECTOR_DEBUG, "Ignoring parameter with index {} on method {}",
                            parameterItem.index, methodInfo.fullyQualifiedName());
                }
            }
        }
        methodInfo.methodInspection.set(methodInspectionBuilder.build(methodInfo));
        if (methodInfo.isConstructor) {
            typeInspectionBuilder.addConstructor(methodInfo);
        } else {
            typeInspectionBuilder.addMethod(methodInfo);
        }
    }
}
