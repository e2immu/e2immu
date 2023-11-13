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

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.FieldInspection;
import org.e2immu.analyser.model.TypeInspection;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.objectweb.asm.Opcodes.ASM9;

public class MyFieldVisitor extends FieldVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyFieldVisitor.class);

    private final FieldInfo fieldInfo;
    private final TypeContext typeContext;
    private final FieldInspection.Builder fieldInspectionBuilder;
    private final TypeInspection.Builder typeInspectionBuilder;
    private final LocalTypeMap localTypeMap;

    public MyFieldVisitor(TypeContext typeContext,
                          FieldInfo fieldInfo,
                          LocalTypeMap localTypeMap,
                          FieldInspection.Builder fieldInspectionBuilder,
                          TypeInspection.Builder typeInspectionBuilder) {
        super(ASM9);
        this.typeContext = typeContext;
        this.fieldInfo = fieldInfo;
        this.fieldInspectionBuilder = fieldInspectionBuilder;
        this.typeInspectionBuilder = typeInspectionBuilder;
        this.localTypeMap = localTypeMap;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        LOGGER.debug("Have field annotation {} {}", descriptor, visible);
        return new MyAnnotationVisitor<>(typeContext, localTypeMap, descriptor, fieldInspectionBuilder);
    }

    @Override
    public void visitEnd() {
        fieldInspectionBuilder.computeAccess(localTypeMap);
        typeInspectionBuilder.addField(fieldInfo);
        // do not build the type already!
    }
}
