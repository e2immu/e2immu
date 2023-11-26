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
import org.e2immu.analyser.bytecode.TypeData;
import org.e2immu.analyser.bytecode.TypeDataImpl;
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Source;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.e2immu.analyser.inspector.InspectionState.STARTING_BYTECODE;
import static org.e2immu.analyser.inspector.InspectionState.TRIGGER_BYTECODE_INSPECTION;

/*
The ByteCodeInspectorImpl is used as a singleton.
Concurrent calls to 'inspectFromPath' each create a new LocalTypeMapImpl.
This method must only be called from TypeMapImpl.Builder, which handles all threading and synchronization.
*/
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
    public List<TypeData> inspectFromPath(Source source) {
        LocalTypeMapImpl localTypeMap = new LocalTypeMapImpl();
        localTypeMap.inspectFromPath(source, typeContext, LocalTypeMap.LoadMode.NOW);
        return localTypeMap.loaded();
    }

    // for testing only!!
    public LocalTypeMap localTypeMap() {
        return new LocalTypeMapImpl();
    }


    private class LocalTypeMapImpl implements LocalTypeMap {

        @Override
        public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
            TypeData td = localTypeMap.get(fieldInfo.owner.fullyQualifiedName);
            if (td != null) {
                return td.fieldInspectionsGet(fieldInfo);
            }
            return typeContext.getFieldInspection(fieldInfo);
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            // avoid going outside!!
            return getOrCreate(typeInfo.fullyQualifiedName, LoadMode.NOW);
        }

        @Override
        public MethodInspection getMethodInspection(MethodInfo methodInfo) {
            TypeData td = localTypeMap.get(methodInfo.typeInfo.fullyQualifiedName);
            if (td != null) {
                return td.methodInspectionsGet(methodInfo.distinguishingName);
            }
            return typeContext.getMethodInspection(methodInfo);
        }

        @Override
        public Primitives getPrimitives() {
            return typeContext.getPrimitives();
        }


        private final Map<String, TypeData> localTypeMap = new LinkedHashMap<>();

        @Override
        public TypeMap.InspectionAndState typeInspectionSituation(String fqn) {
            TypeData local = localTypeMap.get(fqn);
            if (local != null) {
                return local.toInspectionAndState();
            }
            return typeContext.typeMap.typeInspectionSituation(fqn);
        }

        @Override
        public TypeInspection getOrCreate(String fqn, LoadMode loadMode) {
            if (!Input.acceptFQN(fqn)) {
                return null;
            }
            TypeData typeData = localTypeMap.get(fqn);
            if (typeData != null) {
                if (LoadMode.NOW != loadMode || typeData.getInspectionState().ge(STARTING_BYTECODE)) {
                    if (loadMode == LoadMode.QUEUE) {
                        typeContext.typeMap.addToByteCodeQueue(fqn);
                    }
                    return typeData.getTypeInspectionBuilder();
                }
                // START!
            }
            TypeMap.InspectionAndState remote = typeContext.typeMap.typeInspectionSituation(fqn);
            if (remote != null) {
                if (LoadMode.NOW != loadMode || remote.state().ge(STARTING_BYTECODE)) {
                    if (loadMode == LoadMode.QUEUE) {
                        typeContext.typeMap.addToByteCodeQueue(fqn);
                    }
                    return remote.typeInspection();
                }
                if (typeData == null) {
                    TypeInspection.Builder typeInspection = (TypeInspection.Builder) remote.typeInspection();
                    TypeInspection.Builder copy = new TypeInspectionImpl.Builder(typeInspection.typeInfo(),
                            Inspector.BYTE_CODE_INSPECTION);
                    localTypeMapPut(fqn, new TypeDataImpl(copy, TRIGGER_BYTECODE_INSPECTION));
                }
            }
            Source source = classPath.fqnToPath(fqn, ".class");
            if (source == null) {
                return null;
            }
            return inspectFromPath(source, typeContext, loadMode);
        }

        @Override
        public TypeInspection getOrCreate(TypeInfo subType) {
            TypeData typeData = localTypeMap.get(subType.fullyQualifiedName);
            if (typeData != null) {
                return typeData.getTypeInspectionBuilder();
            }
            TypeMap.InspectionAndState remote = typeContext.typeMap.typeInspectionSituation(subType.fullyQualifiedName);
            if (remote != null) {
                return remote.typeInspection();
            }
            TypeInfo typeInfoInMap = typeContext.typeMap.addToTrie(subType);
            TypeInspection.Builder typeInspection = new TypeInspectionImpl.Builder(typeInfoInMap, Inspector.BYTE_CODE_INSPECTION);
            TypeData newTypeData = new TypeDataImpl(typeInspection, TRIGGER_BYTECODE_INSPECTION);
            localTypeMapPut(typeInfoInMap.fullyQualifiedName, newTypeData);
            return typeInspection;
        }

        private void localTypeMapPut(String fullyQualifiedName, TypeData newTypeData) {
            assert fullyQualifiedName.equals(newTypeData.getTypeInspectionBuilder().typeInfo().fullyQualifiedName);
            localTypeMap.put(fullyQualifiedName, newTypeData);
        }

        @Override
        public List<TypeData> loaded() {
            return localTypeMap.values().stream().toList();
        }

        @Override
        public TypeInspection.Builder inspectFromPath(Source path, TypeContext parentTypeContext, LoadMode loadMode) {
            assert path != null && path.path().endsWith(".class");

            String fqn = MyClassVisitor.pathToFqn(path.stripDotClass());
            TypeData typeDataInMap = localTypeMap.get(fqn);
            if (typeDataInMap != null && typeDataInMap.getInspectionState().ge(STARTING_BYTECODE)) {
                return typeDataInMap.getTypeInspectionBuilder();
            }
            TypeMap.InspectionAndState inspectionAndState = typeContext.typeMap.typeInspectionSituation(fqn);
            if (inspectionAndState != null && inspectionAndState.state().isDone()) {
                return (TypeInspection.Builder) inspectionAndState.typeInspection();
            }

            TypeData typeData;
            if (typeDataInMap == null) {
                // create, but ensure that all enclosing are present FIRST: potential recursion
                TypeInfo typeInfo = createTypeInfo(path, fqn, loadMode);
                TypeData typeDataAgain = localTypeMap.get(fqn);
                if (typeDataAgain == null) {
                    TypeInspection.Builder builder = new TypeInspectionImpl.Builder(typeInfo,
                            Inspector.BYTE_CODE_INSPECTION);
                    typeData = new TypeDataImpl(builder, TRIGGER_BYTECODE_INSPECTION);
                    localTypeMapPut(fqn, typeData);
                } else {
                    typeData = typeDataAgain;
                }
            } else {
                TypeInfo typeInfo = typeDataInMap.getTypeInspectionBuilder().typeInfo();
                if(typeInfo.packageNameOrEnclosingType.isRight()) {
                    // ensure that the enclosing type is inspected first! (TestByteCodeInspectorCommonsPool)
                    getTypeInspection(typeInfo.primaryType());
                }
                typeData = typeDataInMap;
            }
            if (typeData.getInspectionState().ge(STARTING_BYTECODE)) {
                return typeData.getTypeInspectionBuilder();
            }
            if (loadMode == LoadMode.NOW) {
                return continueLoadByteCodeAndStartASM(path, parentTypeContext, fqn, typeData);
            }
            LOGGER.debug("Stored type data for {}, state {}", fqn, typeData.getInspectionState());
            return typeData.getTypeInspectionBuilder();
        }

        private TypeInfo createTypeInfo(Source source, String fqn, LoadMode loadMode) {
            String path = source.stripDotClass();
            int dollar = path.lastIndexOf('$');
            TypeInfo typeInfo;
            if (dollar >= 0) {
                String simpleName = path.substring(dollar + 1);
                String newPath = Source.ensureDotClass(path.substring(0, dollar));
                Source newSource = new Source(newPath, source.uri());
                TypeInspection.Builder enclosedInspection = inspectFromPath(newSource,
                        new TypeContext(typeContext), loadMode);
                typeInfo = new TypeInfo(enclosedInspection.typeInfo(), simpleName);
            } else {
                int lastDot = fqn.lastIndexOf(".");
                String packageName = fqn.substring(0, lastDot);
                String simpleName = fqn.substring(lastDot + 1);
                typeInfo = new TypeInfo(Identifier.from(source.uri()), packageName, simpleName);
            }
            return typeContext.typeMap.addToTrie(typeInfo);
        }

        private TypeInspection.Builder continueLoadByteCodeAndStartASM(Source path,
                                                                       TypeContext parentTypeContext,
                                                                       String fqn,
                                                                       TypeData typeData) {
            assert typeData.getInspectionState() == TRIGGER_BYTECODE_INSPECTION;
            typeData.setInspectionState(InspectionState.STARTING_BYTECODE);
            byte[] classBytes = classPath.loadBytes(path.path());
            if (classBytes == null) {
                return null;
            }

            ClassReader classReader = new ClassReader(classBytes);
            LOGGER.debug("Constructed class reader with {} bytes", classBytes.length);

            MyClassVisitor myClassVisitor = new MyClassVisitor(this, annotationStore,
                    new TypeContext(parentTypeContext), path);
            classReader.accept(myClassVisitor, 0);
            typeData.setInspectionState(InspectionState.FINISHED_BYTECODE);
            LOGGER.debug("Finished bytecode inspection of {}", fqn);
            return typeData.getTypeInspectionBuilder();
        }

        @Override
        public void registerFieldInspection(FieldInfo fieldInfo, FieldInspection.Builder fieldInspectionBuilder) {
            TypeData td = localTypeMap.get(fieldInfo.owner.fullyQualifiedName);
            td.fieldInspectionsPut(fieldInfo, fieldInspectionBuilder);
        }

        @Override
        public void registerMethodInspection(MethodInspection.Builder methodInspectionBuilder) {
            TypeData td = localTypeMap.get(methodInspectionBuilder.methodInfo().typeInfo.fullyQualifiedName);
            td.methodInspectionsPut(methodInspectionBuilder.methodInfo().distinguishingName, methodInspectionBuilder);
        }
    }
}
