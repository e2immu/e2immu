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

import org.e2immu.analyser.analyser.SetOfTypes;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;

import java.util.List;
import java.util.Set;

public interface Primitives extends PrimitivesWithoutParameterizedType {
    String JAVA_LANG = "java.lang";
    String JAVA_PRIMITIVE = "__java.lang__PRIMITIVE"; // special string, caught by constructor
    String ORG_E2IMMU_ANNOTATION = "org.e2immu.annotation";
    String INTERNAL = "_internal_";
    String SYNTHETIC_FUNCTION = "SyntheticFunction";
    String SYNTHETIC_FUNCTION_0 = "SyntheticFunction0";
    String SYNTHETIC_CONSUMER = "SyntheticConsumer";

    ParameterizedType stringParameterizedType();

    ParameterizedType intParameterizedType();

    ParameterizedType booleanParameterizedType();

    ParameterizedType boxedBooleanParameterizedType();

    ParameterizedType longParameterizedType();

    ParameterizedType doubleParameterizedType();

    ParameterizedType floatParameterizedType();

    ParameterizedType shortParameterizedType();

    ParameterizedType charParameterizedType();

    MethodInfo createOperator(TypeInfo owner, String name, List<ParameterizedType> parameterizedTypes, ParameterizedType returnType);


    ParameterizedType widestType(ParameterizedType t1, ParameterizedType t2);

    int primitiveTypeOrder(ParameterizedType pt);


    int isAssignableFromTo(ParameterizedType from, ParameterizedType to, boolean covariant);

    SetOfTypes explicitTypesOfJLO();

    ParameterizedType byteParameterizedType();

    ParameterizedType objectParameterizedType();

    ParameterizedType voidParameterizedType();

    MethodAnalysis createEmptyMethodAnalysis(MethodInfo constructor);

    MethodInfo assignOperator(ParameterizedType returnType);
}
