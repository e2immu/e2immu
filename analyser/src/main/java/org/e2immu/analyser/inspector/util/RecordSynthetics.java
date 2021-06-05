package org.e2immu.analyser.inspector.util;

import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;

import java.util.List;


public class RecordSynthetics {

    /*
    Create one method per field;
    for now, not writing the code for equals, hashCode, toString
     */
    public static void create(ExpressionContext expressionContext,
                              TypeInfo typeInfo,
                              TypeInspectionImpl.Builder builder,
                              List<FieldInfo> recordFields) {

        var primitives = expressionContext.typeContext.getPrimitives();
        var e2 = expressionContext.typeContext.typeMapBuilder.getE2ImmuAnnotationExpressions();
        var notModifiedContract = E2ImmuAnnotationExpressions.createContract(primitives, e2.notModified);

        for (var fieldInfo : recordFields) {
            builder.addMethod(createGetter(expressionContext, typeInfo, fieldInfo, notModifiedContract));
        }
    }

    private static MethodInfo createGetter(ExpressionContext expressionContext,
                                           TypeInfo typeInfo,
                                           FieldInfo fieldInfo,
                                           AnnotationExpression notModifiedContract) {
        var getter = new MethodInspectionImpl.Builder(typeInfo, fieldInfo.name)
                .setSynthetic(true)
                .setReturnType(fieldInfo.type)
                .addModifier(MethodModifier.PUBLIC)
                .addAnnotation(notModifiedContract);
        getter.readyToComputeFQN(expressionContext.typeContext);
        var codeBlock = getterCodeBlock(expressionContext, fieldInfo);
        getter.setInspectedBlock(codeBlock);
        expressionContext.typeContext.typeMapBuilder.registerMethodInspection(getter);
        return getter.getMethodInfo();
    }

    // return this.field;
    private static Block getterCodeBlock(ExpressionContext expressionContext, FieldInfo fieldInfo) {
        var returnStatement = new ReturnStatement(
                new VariableExpression(new FieldReference(expressionContext.typeContext, fieldInfo)));
        return new Block.BlockBuilder().addStatement(returnStatement).build();
    }
}
