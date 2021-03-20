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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Cast;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.SwitchStatementNewStyle;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
import java.util.function.Consumer;

/*
Compute the list of types which cannot be replaced by an unbound parameter type (which are not implicitly immutable)
*/
public class ExplicitTypes {

    public enum UsedAs {
        METHOD, NEW_OBJECT, FIELD_ACCESS, FOR_EACH, SWITCH, CAST_TO_E2IMMU, CAST, CAST_DELAY, CAST_SELF
    }

    private final AnalysisProvider analysisProvider;
    private final InspectionProvider inspectionProvider;
    private final Map<ParameterizedType, Set<UsedAs>> result = new HashMap<>();
    private final TypeInfo typeBeingAnalysed;

    public ExplicitTypes(AnalysisProvider analysisProvider,
                         InspectionProvider inspectionProvider,
                         TypeInspection typeInspection,
                         TypeInfo typeBeingAnalysed) {
        this.analysisProvider = analysisProvider;
        this.inspectionProvider = inspectionProvider;
        this.typeBeingAnalysed = typeBeingAnalysed;

        // handles SAMs of fields as well
        typeInspection.methodsAndConstructors(TypeInspectionImpl.Methods.THIS_TYPE_ONLY)
                .forEach(this::explicitTypes);
        typeInspection.fields().forEach(this::explicitTypes);
    }

    public Map<ParameterizedType, Set<UsedAs>> getResult() {
        return result;
    }

    private void explicitTypes(FieldInfo fieldInfo) {
        FieldInspection fieldInspection = inspectionProvider.getFieldInspection(fieldInfo);
        FieldInspection.FieldInitialiser fieldInitialiser = fieldInspection.getFieldInitialiser();
        if (fieldInitialiser != null && fieldInitialiser.initialiser() != null) {
            explicitTypes(fieldInitialiser.initialiser());
        }
    }

    private void explicitTypes(MethodInfo methodInfo) {
        MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
        explicitTypes(methodInspection.getMethodBody());
    }

    private void explicitTypes(Element start) {
        Consumer<Element> visitor = element -> {

            // a.method() -> type of a cannot be replaced by unbound type parameter
            if (element instanceof MethodCall mc) {
                add(mc.object.returnType(), UsedAs.METHOD);
                addTypesFromParameters(mc.methodInfo, UsedAs.METHOD);
            }

            // new A() -> A cannot be replaced by unbound type parameter
            if (element instanceof NewObject newObject) {
                add(newObject.parameterizedType(), UsedAs.NEW_OBJECT);
                if (newObject.constructor() != null) { // can be null, anonymous implementation of interface
                    addTypesFromParameters(newObject.constructor(), UsedAs.NEW_OBJECT);
                }
            }

            // a.b -> type of a cannot be replaced by unbound type parameter
            if (element instanceof FieldAccess fieldAccess) {
                add(fieldAccess.expression().returnType(), UsedAs.FIELD_ACCESS);
            }

            // for(E e: list) -> type of list cannot be replaced by unbound type parameter
            if (element instanceof ForEachStatement forEach) {
                add(forEach.expression.returnType(), UsedAs.FOR_EACH);
            }

            // switch(e) -> type of e cannot be replaced
            if (element instanceof SwitchStatementNewStyle switchStatement) {
                add(switchStatement.expression.returnType(), UsedAs.SWITCH);
            }

            // add the subject of the cast, i.e., if T t is unbound, then
            // (String)t forces T to become explicit
            if (element instanceof Cast cast) {
                ParameterizedType expressionType = cast.expression().returnType();
                ParameterizedType castType = cast.parameterizedType();
                TypeInfo bestType = castType.bestTypeInfo();
                if (bestType != null) {
                    if (bestType == typeBeingAnalysed) {
                        add(expressionType, UsedAs.CAST_SELF);
                    } else {
                        TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(bestType);
                        int immu = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
                        if (immu == Level.DELAY) {
                            add(expressionType, UsedAs.CAST_DELAY);
                        } else if (MultiLevel.isE2Immutable(immu)) {
                            add(expressionType, UsedAs.CAST_TO_E2IMMU);
                        } else {
                            add(expressionType, UsedAs.CAST);
                        }
                    }
                } else {
                    add(expressionType, UsedAs.CAST);
                }
            }
        };
        start.visit(visitor);
    }

    private void add(ParameterizedType type, UsedAs usedAs) {
        result.merge(type, new HashSet<>(Set.of(usedAs)), (set1, set2) -> {
            set1.addAll(set2);
            return set1;
        });
    }

    // a.method(b, c) -> unless the formal parameter types are either Object or another unbound parameter type,
    // they cannot be replaced by unbound type parameter
    private void addTypesFromParameters(MethodInfo methodInfo, UsedAs usedAs) {
        Objects.requireNonNull(methodInfo);
        for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().getParameters()) {
            ParameterizedType formal = parameterInfo.parameterizedType;
            if (!Primitives.isJavaLangObject(formal) && !formal.isUnboundParameterType()) {
                add(formal, usedAs);
            }
        }
    }

}
