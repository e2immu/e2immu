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
import org.e2immu.analyser.model.*;

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
        TypeInfo loadType(String fullyQualifiedName, boolean complain);

        MethodInspection getMethodInspectionDoNotTrigger(String distinguishingName);

        void setByteCodeInspector(OnDemandInspection byteCodeInspector);

        TypeInspection.Builder add(TypeInfo typeInfo, InspectionState triggerJavaParser);

        void setInspectWithJavaParser(InspectWithJavaParser onDemandSourceInspection);

        void makeParametersImmutable();

        TypeMap build();

        TypeInfo getOrCreate(String packageName, String name, InspectionState triggerJavaParser);

        void registerFieldInspection(FieldInfo fieldInfo, FieldInspection.Builder fieldBuilder);

        void registerMethodInspection(MethodInspection.Builder builder);

        TypeInspection.Builder ensureTypeAndInspection(TypeInfo subType, InspectionState inspectionState);

        InspectionState getInspectionState(TypeInfo inMap);

        TypeInfo getOrCreateFromPath(String stripDotClass, InspectionState triggerBytecodeInspection);

        TypeInspection.Builder ensureTypeInspection(TypeInfo typeInfo, InspectionState byHand);

        TypeInfo syntheticFunction(int parameters, boolean isVoid);

        TypeInspection.Builder getOrCreateFromPathReturnInspection(String name, InspectionState startingBytecode);

        Stream<Map.Entry<TypeInfo, TypeInspection.Builder>> streamTypes();
    }
}
