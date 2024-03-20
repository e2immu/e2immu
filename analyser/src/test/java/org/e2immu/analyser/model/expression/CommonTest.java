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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.util.ConditionManagerImpl;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.expr.ParseArrayCreationExpr;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public abstract class CommonTest {

    protected final Primitives primitives = new PrimitivesImpl();
    protected final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);
    protected final AnalysisProvider analysisProvider = AnalysisProvider.DEFAULT_PROVIDER;
    protected final ForwardEvaluationInfo onlySort = new ForwardEvaluationInfo.Builder().setOnlySort(true).build();

    protected final AnalyserContext analyserContext = () -> primitives;

    protected final TypeMap typeMap = new TypeMap() {
        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
            return inspectionProvider.getFieldInspection(fieldInfo);
        }

        @Override
        public MethodInspection getMethodInspection(MethodInfo methodInfo) {
            return inspectionProvider.getMethodInspection(methodInfo);
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            return inspectionProvider.getTypeInspection(typeInfo);
        }
    };

    protected final TypeContext typeContext = new TypeContext() {
        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public TypeMap typeMap() {
            return typeMap;
        }
    };

    protected EvaluationContext evaluationContext(Map<String, Expression> variableValues) {
        return new AbstractEvaluationContextImpl() {
            @Override
            public DV getProperty(Expression value, Property property,
                                  boolean duringEvaluation, boolean ignoreStateInConditionManager) {
                return null;
            }

            @Override
            public int getDepth() {
                return 0;
            }

            @Override
            public Expression currentValue(Variable variable) {
                return Objects.requireNonNull(variableValues.get(variable.simpleName()),
                        "Have no value for " + variable.simpleName());
            }

            @Override
            public Expression currentValue(Variable variable,
                                           Expression scopeValue,
                                           Expression indexValue,
                                           Identifier identifier, ForwardEvaluationInfo forwardEvaluationInfo) {
                return currentValue(variable);
            }

            @Override
            public AnalyserContext getAnalyserContext() {
                return analyserContext;
            }

            @Override
            public ConditionManager getConditionManager() {
                return ConditionManagerImpl.initialConditionManager(primitives);
            }

            @Override
            public TypeInfo getCurrentType() {
                return primitives.stringTypeInfo();
            }

            @Override
            public Location getLocation(Stage level) {
                return Location.NOT_YET_SET;
            }

            @Override
            public Properties defaultValueProperties(ParameterizedType parameterizedType, DV valueForNotNullExpression) {
                if(parameterizedType == primitives.stringParameterizedType()) {
                    return PRIMITIVE_VALUE_PROPERTIES;
                }
                return super.defaultValueProperties(parameterizedType, valueForNotNullExpression);
            }
        };
    }


    @BeforeEach
    public void beforeEach() {
        TypeInfo string = primitives.stringTypeInfo();
        string.typeInspection.set(new TypeInspectionImpl.Builder(string, Inspector.BY_HAND)
                .setAccess(Inspection.Access.PUBLIC));
    }

    protected EvaluationResult context(EvaluationContext evaluationContext) {
        return new EvaluationResultImpl.Builder(evaluationContext).build();
    }

    protected LocalVariable makeLocalVariableInt(String name) {
        return new LocalVariable.Builder()
                .setName(name)
                .setParameterizedType(primitives.intParameterizedType())
                .setOwningType(primitives.stringTypeInfo())
                .build();
    }

    protected LocalVariable makeLocalVariableString(String name) {
        return new LocalVariable.Builder()
                .setName(name)
                .setParameterizedType(primitives.stringParameterizedType())
                .setOwningType(primitives.stringTypeInfo())
                .build();
    }

    protected VariableExpression makeLVAsExpression(String name, Expression initializer) {
        LocalVariable lvi = makeLocalVariableInt(name);
        LocalVariableCreation i = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(lvi, initializer));
        return new VariableExpression(newId(), i.localVariableReference);
    }

    protected static Identifier newId() {
        return Identifier.generate("test");
    }

}
