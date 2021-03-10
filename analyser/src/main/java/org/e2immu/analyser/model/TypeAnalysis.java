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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.SetUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface TypeAnalysis extends Analysis {

    Set<ObjectFlow> getConstantObjectFlows();

    Map<String, Expression> getApprovedPreconditionsE1();

    Map<String, Expression> getApprovedPreconditionsE2();

    Expression getApprovedPreconditions(boolean e2, String markLabel);

    boolean approvedPreconditionsIsSet(boolean e2, String markLabel);

    boolean approvedPreconditionsIsFrozen(boolean e2);

    Set<String> getNamesOfEventuallyImmutableFields();

    default boolean isEventual() {
        return !getApprovedPreconditionsE1().isEmpty() || !getApprovedPreconditionsE2().isEmpty() ||
                !getNamesOfEventuallyImmutableFields().isEmpty();
    }

    default Set<String> marksRequiredForImmutable() {
        return SetUtil.immutableUnion(getApprovedPreconditionsE1().keySet(), getApprovedPreconditionsE2().keySet(),
                getNamesOfEventuallyImmutableFields());
    }

    default String allLabelsRequiredForImmutable() {
        return marksRequiredForImmutable().stream().sorted().collect(Collectors.joining(","));
    }

    /**
     * @return null when not yet set
     */
    Set<ParameterizedType> getImplicitlyImmutableDataTypes();

    default int getTypeProperty(VariableProperty variableProperty) {
        if (variableProperty == VariableProperty.NOT_NULL_EXPRESSION) return MultiLevel.EFFECTIVELY_NOT_NULL;
        return internalGetProperty(variableProperty);
    }

    /**
     * @return the labels of the aspects, pointing to the method on which the aspect has been marked
     */
    Map<String, MethodInfo> getAspects();

    /**
     * Invariants can be associated with an aspect, such as "size()>=0". They can describe the aspect and the state of fields.
     * Invariants that are not associated with an aspect must only describe the state of fields.
     *
     * @return a list of values, each of boolean return type, describing invariants.
     */
    List<Expression> getInvariants();

    default boolean aspectsIsSet(String aspect) {
        return getAspects().containsKey(aspect);
    }

}
