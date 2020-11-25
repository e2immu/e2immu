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

import org.e2immu.analyser.inspector.FieldInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.inspector.TypeContext;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;

import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG;
import static org.e2immu.analyser.util.Logger.log;
import static org.objectweb.asm.Opcodes.ASM7;

public class MyFieldVisitor extends FieldVisitor {
    private final FieldInfo fieldInfo;
    private final TypeContext typeContext;
    private final FieldInspectionImpl.Builder fieldInspectionBuilder;
    private final TypeInspectionImpl.Builder typeInspectionBuilder;

    public MyFieldVisitor(TypeContext typeContext,
                          FieldInfo fieldInfo,
                          FieldInspectionImpl.Builder fieldInspectionBuilder,
                          TypeInspectionImpl.Builder typeInspectionBuilder) {
        super(ASM7);
        this.typeContext = typeContext;
        this.fieldInfo = fieldInfo;
        this.fieldInspectionBuilder = fieldInspectionBuilder;
        this.typeInspectionBuilder = typeInspectionBuilder;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        log(BYTECODE_INSPECTOR_DEBUG, "Have field annotation {} {}", descriptor, visible);
        return new MyAnnotationVisitor<>(typeContext, descriptor, fieldInspectionBuilder);
    }

    @Override
    public void visitEnd() {
        typeInspectionBuilder.addField(fieldInfo);
    }
}
