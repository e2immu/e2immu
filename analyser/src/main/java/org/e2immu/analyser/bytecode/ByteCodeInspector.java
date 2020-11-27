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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.annotationxml.AnnotationStore;
import org.e2immu.analyser.bytecode.asm.MyClassVisitor;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.StringUtil;
import org.objectweb.asm.ClassReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.STARTING_BYTECODE;
import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.TRIGGER_BYTECODE_INSPECTION;
import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR;
import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG;
import static org.e2immu.analyser.util.Logger.log;

public class ByteCodeInspector implements OnDemandInspection {
    private final Resources classPath;
    private final TypeContext typeContext;
    private final AnnotationStore annotationStore;

    public ByteCodeInspector(Resources classPath, AnnotationStore annotationStore, TypeContext typeContext) {
        this.classPath = classPath;
        this.typeContext = typeContext;
        this.annotationStore = annotationStore;
    }

    /**
     * Given a path pointing to a .class file, load the bytes, and inspect the byte code
     *
     * @param path important: path must be split by /, not by .  It may or may not end in .class.
     * @return one or more types; the first one is the main type of the .class file
     */
    @Override
    public List<TypeInfo> inspectFromPath(String path) {
        if (classPath == null) {
            throw new UnsupportedOperationException("No on-demand parsing if no classpath! Failed to load " + path);
        }
        int dollar = path.indexOf('$');
        if (dollar > 0) {
            String pathOfPrimaryType = path.substring(0, dollar);
            String fqnPrimaryType = pathOfPrimaryType.replace('/', '.');
            TypeInfo primaryType = typeContext.typeMapBuilder.get(fqnPrimaryType);
            TypeInspection primaryTypeInspection = primaryType == null ? null : typeContext.getTypeInspection(primaryType);
            if (primaryTypeInspection == null || primaryTypeInspection.getInspectionState().lt(STARTING_BYTECODE)) {
                inspectFromPath(pathOfPrimaryType);
            }
            String pathWithoutClass = StringUtil.stripDotClass(path);
            return List.of(typeContext.typeMapBuilder.getOrCreateFromPath(pathWithoutClass, TRIGGER_BYTECODE_INSPECTION));
            // NOTE that is is quite possible that even after the inspectFromPath, the type has not been created
            // yet... cycles are allowed in the use of sub-types as interface or parent
        }
        if (Logger.isLogEnabled(BYTECODE_INSPECTOR)) {
            logTypesInProcess(path);
        }
        String pathWithDotClass = path.endsWith(".class") ? path : path + ".class";
        byte[] classBytes = classPath.loadBytes(pathWithDotClass);
        if (classBytes == null) return List.of();
        return inspectByteArray(classBytes, new Stack<>(), typeContext);
    }

    private void logTypesInProcess(String path) {
        log(BYTECODE_INSPECTOR, "Parsing {}, in process [{}]", path,
                typeContext.typeMapBuilder.streamTypes()
                        .filter(e -> e.getValue().getInspectionState() == STARTING_BYTECODE)
                        .map(e -> e.getKey().fullyQualifiedName).collect(Collectors.joining(", ")));
    }

    @Override
    public TypeInfo inspectFromPath(String path,
                                    Stack<TypeInfo> enclosingTypes,
                                    TypeContext parentTypeContext) {
        if (classPath == null) {
            throw new UnsupportedOperationException("No on-demand parsing if no classpath! Failed to load " + path);
        }
        if (Logger.isLogEnabled(BYTECODE_INSPECTOR)) {
            logTypesInProcess(path);
            log(BYTECODE_INSPECTOR, enclosingTypes.stream().map(ti -> ti.fullyQualifiedName)
                    .collect(Collectors.joining(" -> ")));
        }
        byte[] classBytes = classPath.loadBytes(path + ".class");
        if (classBytes == null) return null;
        List<TypeInfo> result = inspectByteArray(classBytes, enclosingTypes, parentTypeContext);
        if (result.isEmpty()) return null;
        return result.get(0);
    }

    public List<TypeInfo> inspectByteArray(byte[] classBytes,
                                           Stack<TypeInfo> enclosingTypes,
                                           TypeContext parentTypeContext) {
        ClassReader classReader = new ClassReader(classBytes);
        log(BYTECODE_INSPECTOR_DEBUG, "Constructed class reader with {} bytes", classBytes.length);

        List<TypeInfo> types = new ArrayList<>();
        MyClassVisitor myClassVisitor = new MyClassVisitor(this,
                annotationStore,
                new TypeContext(parentTypeContext),
                types,
                enclosingTypes);
        classReader.accept(myClassVisitor, 0);
        return types;
    }

    public Resources getClassPath() {
        return classPath;
    }
}
