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

package org.e2immu.analyser.model.util;

/*
  This helper class converts a method reference in a field initialiser

  Function<String, Integer> f = Type::someFunction;

  into an instance of an anonymous type

  Function<String, Integer> f2 = new Function<String, Integer>() {
      @Override
      public Integer apply(String s) {
          return Type.someFunction(s);
      }
  };
*/

import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.util.Logger;

import java.util.List;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.LAMBDA;
import static org.e2immu.analyser.util.Logger.log;

public class ConvertMethodReference {


    public static MethodInfo convertMethodReferenceIntoAnonymous(ParameterizedType functionalInterfaceType,
                                                                 TypeInfo enclosingType,
                                                                 MethodReference methodReference,
                                                                 ExpressionContext expressionContext) {
        TypeContext typeContext = expressionContext.typeContext();
        MethodTypeParameterMap method = functionalInterfaceType.findSingleAbstractMethodOfInterface(typeContext);
        int index = expressionContext.anonymousTypeCounters().newIndex(expressionContext.primaryType());
        TypeInfo typeInfo = new TypeInfo(enclosingType, index);
        TypeInspection.Builder builder = typeContext.typeMap.add(typeInfo, InspectionState.BY_HAND);

        builder.setTypeNature(TypeNature.CLASS);
        builder.addInterfaceImplemented(functionalInterfaceType);
        builder.noParent(typeContext.getPrimitives());

        // there are no extra type parameters; only those of the enclosing type(s) can be in 'type'
        MethodInspection.Builder methodBuilder = method.buildCopy(typeContext, typeInfo);
        typeContext.typeMap.registerMethodInspection(methodBuilder);

        Block block = methodContent(methodBuilder, methodReference, expressionContext);
        methodBuilder.setInspectedBlock(block);
        MethodInfo methodInfo = methodBuilder.getMethodInfo();

        builder.addMethod(methodInfo);
        return methodInfo;
    }

    private static Block methodContent(MethodInspection.Builder methodBuilder,
                                       MethodReference methodReference,
                                       ExpressionContext expressionContext) {

        // compose the content of the method...
        MethodInspection methodReferenceInspection = expressionContext.typeContext()
                .getMethodInspection(methodReference.methodInfo);
        Expression newReturnExpression;
        if (methodReferenceInspection.isStatic() || !(methodReference.scope instanceof TypeExpression)) {
            newReturnExpression = methodCallCopyAllParameters(methodReference.scope, methodReferenceInspection, methodBuilder);
        } else {
            if (methodBuilder.getParameters().size() != 1)
                throw new UnsupportedOperationException("Referenced method has multiple parameters");
            newReturnExpression = methodCallNoParameters(methodBuilder.getParameters().get(0), methodReferenceInspection);
        }
        Statement statement;
        if (methodBuilder.isVoid()) {
            statement = new ExpressionAsStatement(Identifier.generate(), newReturnExpression);
        } else {
            statement = new ReturnStatement(Identifier.generate(), newReturnExpression);
        }
        Block block = new Block.BlockBuilder(methodReference.identifier).addStatement(statement).build();

        if (Logger.isLogEnabled(LAMBDA)) {
            log(LAMBDA, "Result of translating block: {}", block.output(Qualification.FULLY_QUALIFIED_NAME, null));
        }
        return block;
    }


    private static Expression methodCallNoParameters(ParameterInfo firstParameter, MethodInspection concreteMethod) {
        Expression newScope = new VariableExpression(firstParameter);
        return new MethodCall(Identifier.generate(), false, newScope, concreteMethod.getMethodInfo(),
                concreteMethod.getReturnType(), List.of());
    }

    /*
    exclusively used for creating an anonymous type from a method reference in field initializers.
     */
    private static Expression methodCallCopyAllParameters(Expression scope,
                                                          MethodInspection concreteMethod,
                                                          MethodInspection interfaceMethod) {
        List<Expression> parameterExpressions = interfaceMethod
                .getParameters().stream().map(VariableExpression::new).collect(Collectors.toList());
        return new MethodCall(Identifier.generate(), false, scope, concreteMethod.getMethodInfo(),
                concreteMethod.getReturnType(), parameterExpressions);
    }

}
