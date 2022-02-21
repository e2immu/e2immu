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

import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class ConvertExpressionWithTypeCreations {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConvertExpressionWithTypeCreations.class);

    public static MethodInfo convertExpressionIntoSupplier(ParameterizedType supplierReturnType,
                                                           boolean fieldIsStatic,
                                                           TypeInfo enclosingType,
                                                           Expression parsedExpression,
                                                           ExpressionContext expressionContext,
                                                           Identifier identifier) {
        TypeContext typeContext = expressionContext.typeContext();
        TypeInfo supplier = typeContext.typeMap.syntheticFunction(0, false);
        ParameterizedType supplierType = new ParameterizedType(supplier, List.of(supplierReturnType));

        int index = expressionContext.anonymousTypeCounters().newIndex(expressionContext.primaryType());
        TypeInfo typeInfo = new TypeInfo(enclosingType, index);
        TypeInspection.Builder builder = typeContext.typeMap.add(typeInfo, InspectionState.BY_HAND);

        builder.setSynthetic(true);
        builder.setTypeNature(TypeNature.CLASS);
        builder.addTypeModifier(TypeModifier.PRIVATE);
        builder.addInterfaceImplemented(supplierType);
        builder.noParent(typeContext.getPrimitives());

        // there are no extra type parameters; only those of the enclosing type(s) can be in 'type'
        MethodInspection.Builder methodBuilder = new MethodInspectionImpl.Builder(typeInfo, "get");
        methodBuilder.setReturnType(supplierReturnType);
        methodBuilder.setStatic(fieldIsStatic);
        methodBuilder.setSynthetic(true);
        methodBuilder.readyToComputeFQN(typeContext);
        typeContext.typeMap.registerMethodInspection(methodBuilder);

        Block block = methodContent(parsedExpression, identifier);
        methodBuilder.setInspectedBlock(block);
        MethodInfo methodInfo = methodBuilder.getMethodInfo();

        builder.addMethod(methodInfo);
        return methodInfo;
    }

    private static Block methodContent(Expression parsedExpression,
                                       Identifier identifier) {
        ReturnStatement statement = new ReturnStatement(Identifier.generate("return in initializer"), parsedExpression);
        Block block = new Block.BlockBuilder(identifier).addStatement(statement).build();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Constructed initializer {}", block.output(Qualification.FULLY_QUALIFIED_NAME, null));
        }
        return block;
    }
}
