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

import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.statement.SwitchStatementNewStyle;
import org.e2immu.analyser.model.statement.TryStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;
import java.util.function.Consumer;

/*
Compute the list of types which cannot be replaced by an unbound parameter type (which are not transparent)

Extra: abstract types on which ONLY abstract methods without modification status are called, are also transparent.
*/
public class ExplicitTypes {

    public enum UsedAs {
        METHOD, ASSIGN_TO_NEW_OBJECT, CONSTRUCTOR_CALL, FIELD_ACCESS, FOR_EACH, SWITCH, CAST, CAST_DELAY, CAST_SELF,
        EXPLICIT_RETURN_TYPE, CATCH, LAMBDA,
    }

    private final InspectionProvider inspectionProvider;
    private final Map<ParameterizedType, Set<UsedAs>> result = new HashMap<>();
    private final TypeInfo typeBeingAnalysed;

    public ExplicitTypes(InspectionProvider inspectionProvider,
                         TypeInfo typeBeingAnalysed) {
        this.inspectionProvider = inspectionProvider;
        this.typeBeingAnalysed = typeBeingAnalysed;
    }

    public ExplicitTypes go(TypeInspection typeInspection) {
        // handles SAMs of fields as well
        typeInspection.methodsAndConstructors(TypeInspectionImpl.Methods.THIS_TYPE_ONLY)
                .forEach(this::explicitTypes);
        typeInspection.fields().forEach(this::explicitTypes);
        // recurse down
        typeInspection.subTypes().forEach(st -> go(inspectionProvider.getTypeInspection(st)));
        return this;
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

            /* a.method() -> the type of "a" cannot be replaced by unbound type parameter,
             */
            MethodCall mc;
            if ((mc = element.asInstanceOf(MethodCall.class)) != null) {
                add(mc.object.returnType(), UsedAs.METHOD);
                addTypesFromParameters(mc.methodInfo, mc.parameterExpressions, UsedAs.METHOD);
            }

            // new A() -> A cannot be replaced by unbound type parameter
            ConstructorCall constructorCall;
            if ((constructorCall = element.asInstanceOf(ConstructorCall.class)) != null) {
                add(constructorCall.parameterizedType(), UsedAs.CONSTRUCTOR_CALL);
                if (constructorCall.constructor() != null) { // can be null, anonymous implementation of interface
                    addTypesFromParameters(constructorCall.constructor(), constructorCall.parameterExpressions(),
                            UsedAs.CONSTRUCTOR_CALL);
                }
            }

            // x = new Y() -> the type of x cannot be replaced by an unbound type parameter
            if (element instanceof Assignment assignment && assignment.value.isInstanceOf(Instance.class)) {
                add(assignment.target.returnType(), UsedAs.ASSIGN_TO_NEW_OBJECT);
            }

            if (element instanceof ReturnStatement returnStatement) {
                boolean ok;
                if (returnStatement.expression instanceof MethodCall methodCall) {
                    ok = methodCall.returnType().typeInfo != null;
                } else {
                    ok = false;
                }
                if (ok) {
                    ParameterizedType returnType = returnStatement.expression.returnType();
                    add(returnType, UsedAs.EXPLICIT_RETURN_TYPE);
                }
            }

            // a.b -> the type of "a" == the owner of "b" cannot be replaced by unbound type parameter
            VariableExpression ve;
            if ((ve = element.asInstanceOf(VariableExpression.class)) != null && ve.variable() instanceof FieldReference fr) {
                add(fr.fieldInfo.owner.asParameterizedType(inspectionProvider), UsedAs.FIELD_ACCESS);
            }

            // for(E e: list) -> type of list cannot be replaced by unbound type parameter
            if (element instanceof ForEachStatement forEach) {
                add(forEach.expression.returnType(), UsedAs.FOR_EACH);
            }

            // switch(e) -> type of e cannot be replaced
            if (element instanceof SwitchStatementNewStyle switchStatement) {
                add(switchStatement.expression.returnType(), UsedAs.SWITCH);
            }

            // catch(E e)
            if (element instanceof TryStatement.CatchParameter catchParameter) {
                add(catchParameter.returnType(), UsedAs.CATCH);
            }

            // add the subject of the cast, i.e., if T t is unbound, then
            // (String)t forces T to become explicit
            Cast cast;
            if ((cast = element.asInstanceOf(Cast.class)) != null) {
                ParameterizedType expressionType = cast.getExpression().returnType();
                ParameterizedType castType = cast.getParameterizedType();
                TypeInfo bestType = castType.bestTypeInfo();
                if (bestType != null) {
                    if (bestType == typeBeingAnalysed) {
                        add(expressionType, UsedAs.CAST_SELF);
                    } else {
                        add(expressionType, UsedAs.CAST);
                    }
                } else {
                    add(expressionType, UsedAs.CAST);
                }
                add(castType, UsedAs.CAST);
            }

            // lambda == new InterfaceType() { .. }
            if (element instanceof Lambda lambda) {
                add(lambda.abstractFunctionalType, UsedAs.LAMBDA);
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
    private void addTypesFromParameters(MethodInfo methodInfo, List<Expression> expressions, UsedAs usedAs) {
        Objects.requireNonNull(methodInfo);
        List<ParameterInfo> parameters = methodInfo.methodInspection.get().getParameters();
        int i = 0;
        for (Expression expression : expressions) {
            ParameterInfo parameterInfo = i < parameters.size() ? parameters.get(i) : parameters.get(parameters.size() - 1);
            ParameterizedType formal = parameterInfo.parameterizedType;
            if (!formal.isJavaLangObject() && !formal.isUnboundTypeParameter()) {
                add(formal, usedAs);
            }
            ParameterizedType concrete = expression.returnType();
            if(concrete.isPrimitiveExcludingVoid() && !formal.isPrimitiveExcludingVoid()) {
                // formal could be an unbound type parameter, so int is boxed into Integer
                ParameterizedType boxed = inspectionProvider.getPrimitives().boxed(concrete.typeInfo).asSimpleParameterizedType();
                add(boxed, usedAs);
            }
            i++;
        }
        for (Expression expression : expressions) {
            ParameterizedType dynamic = expression.returnType();
            if (!dynamic.isJavaLangObject() && !dynamic.isUnboundTypeParameter()) {
                add(dynamic, usedAs);
            }
        }
    }

}
