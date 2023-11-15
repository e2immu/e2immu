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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.bytecode.TypeData;
import org.e2immu.analyser.bytecode.asm.LocalTypeMap;
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.TypeInspector;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.Source;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public interface TypeMap extends InspectionProvider {

    TypeInfo get(Class<?> clazz);

    TypeInfo get(String fullyQualifiedName);

    boolean isPackagePrefix(PackagePrefix packagePrefix);

    void visit(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer);

    E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions();

    record InspectionAndState(TypeInspection typeInspection, InspectionState state) {
    }

    interface Builder extends TypeMap {
        // generic, could be from source, could be from byte code; used in direct type access in source code
        TypeInfo getOrCreate(String fqn, boolean complain);

        /*
        main entry point to start building types
         */
        @Modified
        @NotNull
        TypeInspection.Builder getOrCreate(String packageName, String name, Identifier identifier, InspectionState triggerJavaParser);

        /*
        convenience method for getOrCreate, first calling the classPath to obtain the identifier from the source
         */
        @Modified
        @NotNull
        TypeInfo getOrCreateByteCode(String packageName, String simpleName);

        /*
        NOTE: this method should not be used by the ASM visitors or bytecode inspection implementation!!!
         */
        @Modified
        @NotNull
        TypeInspection.Builder getOrCreateFromClassPathEnsureEnclosing(Source source,
                                                                       InspectionState startingBytecode);

        TypeInfo addToTrie(TypeInfo typeInfo);

        /*
                lowest level: a call on get() will return null, called by getOrCreate
                 */
        @Modified
        @NotNull
        TypeInspection.Builder add(TypeInfo typeInfo, InspectionState triggerJavaParser);

        /*
        for use inside byte code inspection
         */
        InspectionAndState typeInspectionSituation(String fqName);

        MethodInspection getMethodInspectionDoNotTrigger(TypeInfo typeInfo, String distinguishingName);

        @Modified
        void setByteCodeInspector(ByteCodeInspector byteCodeInspector);

        @Modified
        void setInspectWithJavaParser(InspectWithJavaParser onDemandSourceInspection);

        @Modified
        void makeParametersImmutable();

        // can be called only one, freezes
        @Modified
        TypeMap build();

        @Modified
        void registerFieldInspection(FieldInfo fieldInfo, FieldInspection.Builder fieldBuilder);

        @Modified
        void registerMethodInspection(MethodInspection.Builder builder);

        @NotNull
        InspectionState getInspectionState(TypeInfo inMap);

        @Modified
        @NotNull
        TypeInfo syntheticFunction(int parameters, boolean isVoid);

        @NotNull
        Stream<TypeInfo> streamTypesStartingByteCode();

        /*
        Convenience method for local class declarations and anonymous types; calls `add` to add them
         */
        @NotNull
        TypeInspector newTypeInspector(TypeInfo typeInfo, boolean b, boolean b1);

        TypeInspection getTypeInspectionToStartResolving(TypeInfo typeInfo);

        // from byteCodeInspection into my typeMap
        void copyIntoTypeMap(List<TypeData> localTypeData);
    }
}
