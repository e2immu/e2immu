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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.annotationxml.AnnotationStore;
import org.e2immu.analyser.bytecode.asm.MyClassVisitor;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Source;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.stream.Collectors;

import static org.e2immu.analyser.inspector.InspectionState.STARTING_BYTECODE;
import static org.e2immu.analyser.inspector.InspectionState.TRIGGER_BYTECODE_INSPECTION;

public class ByteCodeInspector implements OnDemandInspection {
    private static final Logger LOGGER = LoggerFactory.getLogger(ByteCodeInspector.class);

    private final Resources classPath;
    private final TypeContext typeContext;
    private final AnnotationStore annotationStore;

    public ByteCodeInspector(Resources classPath, AnnotationStore annotationStore, TypeContext typeContext) {
        this.classPath = Objects.requireNonNull(classPath);
        this.typeContext = Objects.requireNonNull(typeContext);
        this.annotationStore = annotationStore;
    }

    /**
     * Given a path pointing to a .class file, load the bytes, and inspect the byte code
     *
     * @param source important: path must be split by /, not by .  It may or may not end in .class.
     * @return one or more types; the first one is the main type of the .class file
     */
    @Override
    public List<TypeInfo> inspectFromPath(Source source) {
        String path = source.path();
        int dollar = path.indexOf('$');
        if (dollar > 0) {
            String pathOfPrimaryType = path.substring(0, dollar);
            String fqnPrimaryType = pathOfPrimaryType.replace('/', '.');
            TypeInfo primaryType = typeContext.typeMap.get(fqnPrimaryType);
            TypeInspection primaryTypeInspection = primaryType == null ? null
                    // this will trigger the inspection of the primary type, if it hasn't been inspected yet
                    : typeContext.getTypeInspection(primaryType);
            if (primaryTypeInspection == null || primaryTypeInspection.getInspectionState().lt(STARTING_BYTECODE)) {
                inspectFromPath(new Source(pathOfPrimaryType, source.uri()));
            }
            return List.of(typeContext.typeMap.getOrCreateFromPath(source, TRIGGER_BYTECODE_INSPECTION));
            // NOTE that it is quite possible that even after the inspectFromPath, the type has not been created
            // yet... cycles are allowed in the use of subtypes as interface or parent
        }
        if (LOGGER.isDebugEnabled()) {
            logTypesInProcess(path);
        }
        String pathWithDotClass = path.endsWith(".class") ? path : path + ".class";
        byte[] classBytes = classPath.loadBytes(pathWithDotClass);
        if (classBytes == null) return List.of();
        return inspectByteArray(source, classBytes, new Stack<>(), typeContext);
    }

    private void logTypesInProcess(String path) {
        LOGGER.debug("Parsing {}, in process [{}]", path,
                typeContext.typeMap.streamTypesStartingByteCode()
                        .map(typeInfo -> typeInfo.fullyQualifiedName)
                        .collect(Collectors.joining(", ")));
    }

    @Override
    public TypeInfo inspectFromPath(Source path,
                                    Stack<TypeInfo> enclosingTypes,
                                    TypeContext parentTypeContext) {
        assert path != null && path.path().endsWith(".class");
        if (LOGGER.isDebugEnabled()) {
            logTypesInProcess(path.path());
            LOGGER.debug(enclosingTypes.stream().map(ti -> ti.fullyQualifiedName)
                    .collect(Collectors.joining(" -> ")));
        }
        byte[] classBytes = classPath.loadBytes(path.path());
        if (classBytes == null) return null;
        List<TypeInfo> result = inspectByteArray(path, classBytes, enclosingTypes, parentTypeContext);
        if (result.isEmpty()) return null;
        return result.get(0);
    }

    public List<TypeInfo> inspectByteArray(Source pathAndURI,
                                           byte[] classBytes,
                                           Stack<TypeInfo> enclosingTypes,
                                           TypeContext parentTypeContext) {
        ClassReader classReader = new ClassReader(classBytes);
        LOGGER.debug("Constructed class reader with {} bytes", classBytes.length);

        List<TypeInfo> types = new ArrayList<>();
        MyClassVisitor myClassVisitor = new MyClassVisitor(this,
                annotationStore,
                new TypeContext(parentTypeContext),
                pathAndURI,
                types,
                enclosingTypes);
        classReader.accept(myClassVisitor, 0);
        return types;
    }

    @Override
    public Source fqnToPath(String fullyQualifiedName) {
        return classPath.fqnToPath(fullyQualifiedName, ".class");
    }
}
