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

import org.e2immu.analyser.annotationxml.AnnotationStore;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.Inspector;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Source;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.inspector.InspectionState.STARTING_BYTECODE;
import static org.e2immu.analyser.inspector.InspectionState.TRIGGER_BYTECODE_INSPECTION;

public class ByteCodeInspectorImpl implements ByteCodeInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ByteCodeInspectorImpl.class);

    private final Resources classPath;
    private final TypeContext typeContext;
    private final AnnotationStore annotationStore;

    public ByteCodeInspectorImpl(Resources classPath, AnnotationStore annotationStore, TypeContext typeContext) {
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
    public List<TypeMap.InspectionAndState> inspectFromPath(Source source) {
        String path = source.path();
        LocalTypeMapImpl localTypeMap = new LocalTypeMapImpl();
        localTypeMap.inspectFromPath(source, typeContext, true);
        return localTypeMap.loaded();
    }

    private void logTypesInProcess(String path) {
        LOGGER.debug("Parsing {}, in process [{}]", path,
                typeContext.typeMap.streamTypesStartingByteCode()
                        .map(typeInfo -> typeInfo.fullyQualifiedName)
                        .collect(Collectors.joining(", ")));
    }

    // for testing only!!
    public LocalTypeMap localTypeMap() {
        return new LocalTypeMapImpl();
    }

    private class LocalTypeMapImpl implements LocalTypeMap {

        static class TypeData {
            final TypeInspection.Builder typeInspection;
            InspectionState inspectionState;

            TypeData(TypeInspection.Builder typeInspection) {
                this.typeInspection = typeInspection;
            }

            TypeMap.InspectionAndState toInspectionAndState() {
                return new TypeMap.InspectionAndState(typeInspection, inspectionState);
            }
        }

        private final Map<String, TypeData> loadedByFQN = new LinkedHashMap<>();

        @Override
        public TypeMap.InspectionAndState get(String fqn) {
            TypeData local = loadedByFQN.get(fqn);
            if (local != null) {
                return local.toInspectionAndState();
            }
            return typeContext.typeMap.typeInspectionSituation(fqn);
        }

        @Override
        public TypeInspection getOrCreate(String fqn) {
            if (!Input.acceptFQN(fqn)) return null;
            TypeData typeData = loadedByFQN.get(fqn);
            if (typeData != null) {
                return typeData.typeInspection;
            }
            TypeMap.InspectionAndState remote = typeContext.typeMap.typeInspectionSituation(fqn);
            if (remote != null) {
                return remote.typeInspection();
            }
            Source source = classPath.fqnToPath(fqn, ".class");
            return inspectFromPath(source, typeContext, false);
        }

        @Override
        public TypeInspection getOrCreate(TypeInfo subType) {
            TypeData typeData = loadedByFQN.get(subType.fullyQualifiedName);
            if (typeData != null) {
                return typeData.typeInspection;
            }
            TypeMap.InspectionAndState remote = typeContext.typeMap.typeInspectionSituation(subType.fullyQualifiedName);
            if (remote != null) {
                return remote.typeInspection();
            }
            TypeInspection.Builder typeInspection = new TypeInspectionImpl.Builder(subType, Inspector.BYTE_CODE_INSPECTION);
            TypeData newTypeData = new TypeData(typeInspection);
            loadedByFQN.put(subType.fullyQualifiedName, newTypeData);
            return typeInspection;
        }

        @Override
        public List<TypeMap.InspectionAndState> loaded() {
            return loadedByFQN.values().stream().map(TypeData::toInspectionAndState).toList();
        }

        @Override
        public TypeInspection.Builder inspectFromPath(Source path, TypeContext parentTypeContext, boolean start) {
            assert path != null && !path.path().endsWith(".java");
            if (LOGGER.isDebugEnabled()) {
                logTypesInProcess(path.path());
            }
            String fqn = MyClassVisitor.pathToFqn(path.stripDotClass());
            TypeData typeDataInMap = loadedByFQN.get(fqn);
            TypeData typeData;
            if (typeDataInMap == null) {
                // create, but ensure that all enclosing are present FIRST: potential recursion
                TypeInfo typeInfo = createTypeInfo(path, fqn, start);
                TypeInspection.Builder builder = new TypeInspectionImpl.Builder(typeInfo,
                        Inspector.BYTE_CODE_INSPECTION);
                typeData = new TypeData(builder);
            } else {
                typeData = typeDataInMap;
            }
            if (typeData.inspectionState.isDone()) {
                return typeData.typeInspection;
            }
            if (start) {
                return continueLoadByteCodeAndStartASM(path, parentTypeContext, fqn, typeData);
            }
            typeData.inspectionState = TRIGGER_BYTECODE_INSPECTION;
            return typeData.typeInspection;
        }

        private TypeInfo createTypeInfo(Source source, String fqn, boolean start) {
            String path = source.stripDotClass();
            int dollar = path.lastIndexOf('$');
            if (dollar >= 0) {
                String simpleName = path.substring(dollar + 1);
                Source newPath = new Source(path.substring(0, dollar), source.uri());
                // before we do the recursion, we must check!
                String fqnOfEnclosing = MyClassVisitor.pathToFqn(newPath.path());
                TypeData typeData = loadedByFQN.get(fqnOfEnclosing);
                if (typeData == null || typeData.inspectionState == InspectionState.TRIGGER_BYTECODE_INSPECTION) {
                    TypeInspection.Builder enclosedInspection = inspectFromPath(newPath, new TypeContext(typeContext), start);
                    return new TypeInfo(enclosedInspection.typeInfo(), simpleName);
                }
                assert typeData.inspectionState == InspectionState.STARTING_BYTECODE
                        || typeData.inspectionState.isDone();
                return typeData.typeInspection.typeInfo();
            }
            int lastDot = fqn.lastIndexOf(".");
            String packageName = fqn.substring(0, lastDot);
            String simpleName = fqn.substring(lastDot + 1);
            return new TypeInfo(Identifier.from(source.uri()), packageName, simpleName);
        }

        private TypeInspection.Builder continueLoadByteCodeAndStartASM(Source path,
                                                                       TypeContext parentTypeContext,
                                                                       String fqn,
                                                                       TypeData typeData) {
            typeData.inspectionState = InspectionState.STARTING_BYTECODE;

            byte[] classBytes = classPath.loadBytes(path.path());
            if (classBytes == null) {
                return null;
            }

            ClassReader classReader = new ClassReader(classBytes);
            LOGGER.debug("Constructed class reader with {} bytes", classBytes.length);

            MyClassVisitor myClassVisitor = new MyClassVisitor(this, annotationStore,
                    new TypeContext(parentTypeContext), path);
            loadedByFQN.put(fqn, typeData);
            classReader.accept(myClassVisitor, 0);
            typeData.inspectionState = InspectionState.FINISHED_BYTECODE;
            return typeData.typeInspection;
        }


        @Override
        public Source fqnToPath(String fullyQualifiedName) {
            return classPath.fqnToPath(fullyQualifiedName, ".class");
        }
    }
}
