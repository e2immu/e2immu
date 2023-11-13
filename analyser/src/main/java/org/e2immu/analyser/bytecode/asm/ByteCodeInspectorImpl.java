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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.parser.impl.TypeMapImpl;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Source;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
    public Data inspectFromPath(Source source) {
        LocalTypeMapImpl localTypeMap = new LocalTypeMapImpl();
        localTypeMap.inspectFromPath(source, typeContext, true);
        return new Data(localTypeMap.loaded(), localTypeMap.methodInspections.values(), localTypeMap.fieldInspections);
    }

    // for testing only!!
    public LocalTypeMap localTypeMap() {
        return new LocalTypeMapImpl();
    }

    private class LocalTypeMapImpl implements LocalTypeMap {

        @Override
        public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
            FieldInspection local = fieldInspections.get(fieldInfo);
            if (local != null) return local;
            return typeContext.getFieldInspection(fieldInfo);
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            // avoid going outside!!
            return getOrCreate(typeInfo.fullyQualifiedName, true);
        }

        @Override
        public MethodInspection getMethodInspection(MethodInfo methodInfo) {
            MethodInspection local = methodInspections.get(methodInfo);
            if (local != null) return local;
            return typeContext.getMethodInspection(methodInfo);
        }

        @Override
        public Primitives getPrimitives() {
            return typeContext.getPrimitives();
        }

        static class TypeData {
            final TypeInspection.Builder typeInspection;
            InspectionState inspectionState;

            TypeData(TypeInspection.Builder typeInspection) {
                this.typeInspection = typeInspection;
                inspectionState = TRIGGER_BYTECODE_INSPECTION;
            }

            TypeMap.InspectionAndState toInspectionAndState() {
                return new TypeMap.InspectionAndState(typeInspection, inspectionState);
            }
        }

        private final Map<String, TypeData> localTypeMap = new LinkedHashMap<>();
        private final Map<MethodInfo, MethodInspection.Builder> methodInspections = new HashMap<>();
        private final Map<FieldInfo, FieldInspection.Builder> fieldInspections = new HashMap<>();

        @Override
        public TypeMap.InspectionAndState typeInspectionSituation(String fqn) {
            TypeData local = localTypeMap.get(fqn);
            if (local != null) {
                return local.toInspectionAndState();
            }
            return typeContext.typeMap.typeInspectionSituation(fqn);
        }

        @Override
        public TypeInspection getOrCreate(String fqn, boolean start) {
            if (!Input.acceptFQN(fqn)) return null;
            TypeData typeData = localTypeMap.get(fqn);
            if (typeData != null) {
                if (!start || typeData.inspectionState.isDone()) return typeData.typeInspection;
                // START!
            }
            TypeMap.InspectionAndState remote = typeContext.typeMap.typeInspectionSituation(fqn);
            if (remote != null) {
                if (!start || remote.state().isDone()) return remote.typeInspection();
                if (typeData == null) {
                    localTypeMap.put(fqn, new TypeData((TypeInspection.Builder) remote.typeInspection()));
                }
            }
            Source source = classPath.fqnToPath(fqn, ".class");
            if (source == null) return null;
            return inspectFromPath(source, typeContext, start);
        }

        @Override
        public TypeInspection getOrCreate(TypeInfo subType) {
            TypeData typeData = localTypeMap.get(subType.fullyQualifiedName);
            if (typeData != null) {
                return typeData.typeInspection;
            }
            TypeMap.InspectionAndState remote = typeContext.typeMap.typeInspectionSituation(subType.fullyQualifiedName);
            if (remote != null) {
                return remote.typeInspection();
            }
            TypeInspection.Builder typeInspection = new TypeInspectionImpl.Builder(subType, Inspector.BYTE_CODE_INSPECTION);
            TypeData newTypeData = new TypeData(typeInspection);
            localTypeMap.put(subType.fullyQualifiedName, newTypeData);
            return typeInspection;
        }

        @Override
        public List<TypeMap.InspectionAndState> loaded() {
            return localTypeMap.values().stream().map(TypeData::toInspectionAndState).toList();
        }

        @Override
        public TypeInspection.Builder inspectFromPath(Source path, TypeContext parentTypeContext, boolean start) {
            assert path != null && path.path().endsWith(".class");

            String fqn = MyClassVisitor.pathToFqn(path.stripDotClass());
            TypeData typeDataInMap = localTypeMap.get(fqn);
            TypeData typeData;
            if (typeDataInMap == null) {
                // create, but ensure that all enclosing are present FIRST: potential recursion
                TypeInfo typeInfo = createTypeInfo(path, fqn, start);
                TypeMap.InspectionAndState situation = typeInspectionSituation(fqn);
                TypeInspection.Builder builder;
                if (situation == null) {
                    builder = new TypeInspectionImpl.Builder(typeInfo, Inspector.BYTE_CODE_INSPECTION);
                } else {
                    builder = (TypeInspection.Builder) situation.typeInspection();
                }
                typeData = new TypeData(builder);
                localTypeMap.put(fqn, typeData);
            } else {
                typeData = typeDataInMap;
            }
            if (typeData.inspectionState.ge(STARTING_BYTECODE)) {
                return typeData.typeInspection;
            }
            if (start) {
                return continueLoadByteCodeAndStartASM(path, parentTypeContext, fqn, typeData);
            }
            LOGGER.debug("Stored type data for {}, state {}", fqn, typeData.inspectionState);
            return typeData.typeInspection;
        }

        private TypeInfo createTypeInfo(Source source, String fqn, boolean start) {
            String path = source.stripDotClass();
            int dollar = path.lastIndexOf('$');
            if (dollar >= 0) {
                String simpleName = path.substring(dollar + 1);
                String newPath = Source.ensureDotClass(path.substring(0, dollar));
                Source newSource = new Source(newPath, source.uri());
                // before we do the recursion, we must check!
                String fqnOfEnclosing = MyClassVisitor.pathToFqn(newSource.path());
                TypeData typeData = localTypeMap.get(fqnOfEnclosing);
                if (typeData == null || typeData.inspectionState == InspectionState.TRIGGER_BYTECODE_INSPECTION) {
                    TypeInspection.Builder enclosedInspection = inspectFromPath(newSource, new TypeContext(typeContext), start);
                    if (enclosedInspection == null) {
                        throw new UnsupportedOperationException("Cannot load enclosed type " + fqnOfEnclosing);
                    }
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
            classReader.accept(myClassVisitor, 0);
            typeData.inspectionState = InspectionState.FINISHED_BYTECODE;
            LOGGER.debug("Finished bytecode inspection of {}", fqn);
            return typeData.typeInspection;
        }


        @Override
        public Source fqnToPath(String fullyQualifiedName) {
            return classPath.fqnToPath(fullyQualifiedName, ".class");
        }

        @Override
        public void registerFieldInspection(FieldInfo fieldInfo, FieldInspection.Builder fieldInspectionBuilder) {
            fieldInspections.put(fieldInfo, fieldInspectionBuilder);
        }

        @Override
        public void registerMethodInspection(MethodInspection.Builder methodInspectionBuilder) {
            methodInspections.put(methodInspectionBuilder.methodInfo(), methodInspectionBuilder);
        }
    }
}
