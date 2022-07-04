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

import org.e2immu.analyser.bytecode.OnDemandInspection;
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.TypeInspector;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public interface TypeMap extends InspectionProvider {

    TypeInfo get(Class<?> clazz);

    TypeInfo get(String fullyQualifiedName);

    boolean isPackagePrefix(PackagePrefix packagePrefix);

    void visit(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer);

    E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions();

    interface Builder extends TypeMap {
        @Modified
        TypeInfo loadType(String fullyQualifiedName, boolean complain);

        MethodInspection getMethodInspectionDoNotTrigger(String distinguishingName);

        @Modified
        void setByteCodeInspector(OnDemandInspection byteCodeInspector);

        @Modified
        TypeInspection.Builder add(TypeInfo typeInfo, InspectionState triggerJavaParser);

        @Modified
        void setInspectWithJavaParser(InspectWithJavaParser onDemandSourceInspection);

        @Modified
        void makeParametersImmutable();

        TypeMap build();

        @Modified
        @NotNull
        TypeInfo getOrCreate(String packageName, String name, InspectionState triggerJavaParser);

        @Modified
        void registerFieldInspection(FieldInfo fieldInfo, FieldInspection.Builder fieldBuilder);

        @Modified
        void registerMethodInspection(MethodInspection.Builder builder);

        @Modified
        @NotNull
        TypeInspection.Builder ensureTypeAndInspection(TypeInfo subType, InspectionState inspectionState);

        @NotNull
        InspectionState getInspectionState(TypeInfo inMap);

        @Modified
        @NotNull
        TypeInfo getOrCreateFromPath(String stripDotClass, InspectionState triggerBytecodeInspection);

        @Modified
        @NotNull
        TypeInspection.Builder ensureTypeInspection(TypeInfo typeInfo, InspectionState byHand);

        @Modified
        @NotNull
        TypeInfo syntheticFunction(int parameters, boolean isVoid);

        @Modified
        @NotNull
        TypeInspection.Builder getOrCreateFromPathReturnInspection(String name, InspectionState startingBytecode);

        @NotNull
        Stream<Map.Entry<TypeInfo, TypeInspection.Builder>> streamTypes();

        @NotNull
        TypeInspector newTypeInspector(TypeInfo typeInfo, boolean b, boolean b1);

        TypeInspection getTypeInspectionDoNotTrigger(TypeInfo currentType);
    }
}
