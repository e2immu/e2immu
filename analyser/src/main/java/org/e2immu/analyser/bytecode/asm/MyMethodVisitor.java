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

import org.e2immu.analyser.annotationxml.model.MethodItem;
import org.e2immu.analyser.annotationxml.model.ParameterItem;
import org.e2immu.analyser.bytecode.JetBrainsAnnotationTranslator;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.inspector.TypeContext;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG;
import static org.e2immu.analyser.util.Logger.log;
import static org.objectweb.asm.Opcodes.ASM9;

public class MyMethodVisitor extends MethodVisitor {
    private final TypeInspectionImpl.Builder typeInspectionBuilder;
    private final TypeContext typeContext;
    private final MethodInspectionImpl.Builder methodInspectionBuilder;
    private final List<ParameterizedType> types;
    private final ParameterInspectionImpl.Builder[] parameterInspectionBuilders;
    private final int numberOfParameters;
    private final JetBrainsAnnotationTranslator jetBrainsAnnotationTranslator;
    private final MethodItem methodItem;
    private int countLocalVars;
    private final boolean[] hasNameFromLocalVar;
    private final boolean lastParameterIsVarargs;

    public MyMethodVisitor(TypeContext typeContext,
                           MethodInspectionImpl.Builder methodInspectionBuilder,
                           TypeInspectionImpl.Builder typeInspectionBuilder,
                           List<ParameterizedType> types,
                           boolean lastParameterIsVarargs,
                           MethodItem methodItem,
                           JetBrainsAnnotationTranslator jetBrainsAnnotationTranslator) {
        super(ASM9);
        this.typeContext = typeContext;
        this.methodInspectionBuilder = methodInspectionBuilder;
        this.typeInspectionBuilder = typeInspectionBuilder;
        this.types = types;
        this.jetBrainsAnnotationTranslator = jetBrainsAnnotationTranslator;
        this.methodItem = methodItem;
        numberOfParameters = types.size() - 1;
        hasNameFromLocalVar = new boolean[numberOfParameters];
        parameterInspectionBuilders = new ParameterInspectionImpl.Builder[numberOfParameters];
        for (int i = 0; i < numberOfParameters; i++) {
            parameterInspectionBuilders[i] = methodInspectionBuilder.newParameterInspectionBuilder(i);
        }
        this.lastParameterIsVarargs = lastParameterIsVarargs;
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
        int i = countLocalVars - (methodInspectionBuilder.isStatic() ? 0 : 1);
        if (i >= 0 && i < numberOfParameters) {
            ParameterizedType parameterizedType = types.get(i);
            ParameterInspectionImpl.Builder pib = parameterInspectionBuilders[i];
            pib.setName(name);
            pib.setParameterizedType(parameterizedType);
            if (lastParameterIsVarargs && i == numberOfParameters - 1) {
                parameterInspectionBuilders[i].setVarArgs(true);
            }
            hasNameFromLocalVar[i] = true;
        }
        countLocalVars++;
    }

    @Override
    public void visitEnd() {
        ParameterNameFactory factory = new ParameterNameFactory();
        for (int i = 0; i < numberOfParameters; i++) {
            if (!hasNameFromLocalVar[i]) {
                ParameterInspectionImpl.Builder pib = parameterInspectionBuilders[i];
                ParameterizedType type = types.get(i);
                pib.setParameterizedType(type);
                pib.setName(factory.next(type));
                if (lastParameterIsVarargs && i == numberOfParameters - 1) {
                    pib.setVarArgs(true);
                }
                log(BYTECODE_INSPECTOR_DEBUG, "Set parameterInspection {}", i);
            }
        }

        methodInspectionBuilder.readyToComputeFQN(typeContext);

        if (methodItem != null) {
            for (ParameterItem parameterItem : methodItem.getParameterItems()) {
                if (parameterItem.index < parameterInspectionBuilders.length) {
                    if (!parameterItem.getAnnotations().isEmpty()) {
                        jetBrainsAnnotationTranslator.mapAnnotations(parameterItem.getAnnotations(),
                                parameterInspectionBuilders[parameterItem.index]);
                    }
                } else {
                    log(BYTECODE_INSPECTOR_DEBUG, "Ignoring parameter with index {} on method {}",
                            parameterItem.index, methodInspectionBuilder.getFullyQualifiedName());
                }
            }
        }
        MethodInfo methodInfo = methodInspectionBuilder.getMethodInfo();
        if (methodInfo.isConstructor) {
            typeInspectionBuilder.addConstructor(methodInfo);
        } else {
            typeInspectionBuilder.addMethod(methodInfo);
        }
        // note that we do NOT YET execute methodInfo.methodInspection.set(methodInspectionBuilder.build())
        // this will take place after potential AnnotatedAPI inspection.
        // can't do this too early: we need all parameters parsed properly
        typeContext.typeMapBuilder.registerMethodInspection(methodInspectionBuilder);

    }
}
