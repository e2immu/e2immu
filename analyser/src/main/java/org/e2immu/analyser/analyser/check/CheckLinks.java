package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.FieldAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.Linked;

import java.util.*;
import java.util.stream.Collectors;

public record CheckLinks(Primitives primitives, E2ImmuAnnotationExpressions e2) {

    public AnnotationExpression createLinkAnnotation(E2ImmuAnnotationExpressions typeContext, Set<Variable> links) {
        List<Expression> linkNameList = links.stream().map(variable -> new StringConstant(primitives,
                variable.nameInLinkedAnnotation())).collect(Collectors.toList());
        Expression linksStringArray = new MemberValuePair("to", new ArrayInitializer(primitives, ObjectFlow.NO_FLOW, linkNameList));
        List<Expression> expressions = List.of(linksStringArray);
        return new AnnotationExpressionImpl(typeContext.linked.typeInfo(), expressions);
    }

    public void checkLinksForFields(Messages messages, FieldInfo fieldInfo, FieldAnalysisImpl.Builder fieldAnalysis) {
        fieldAnalysis.annotationChecks.put(new AnnotationExpressionImpl(e2.linked.typeInfo(), List.of()), Analysis.AnnotationCheck.COMPUTED);
        // FIXME
        
        Optional<AnnotationExpression> linkedOpt = fieldInfo.fieldInspection.get().getAnnotations().stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(Linked.class.getName())).findFirst();
        if (linkedOpt.isEmpty()) return;
        Set<Variable> linkedVariables = fieldAnalysis.getLinkedVariables();

        String[] inspected = linkedOpt.get().extract("to", new String[]{});
        String inspectedString = Arrays.stream(inspected).sorted().collect(Collectors.joining(","));
        String computedString = linkedVariables.stream()
                .map(Variable::nameInLinkedAnnotation)
                .sorted().collect(Collectors.joining(","));
        if (!inspectedString.equals(computedString)) {
            messages.add(Message.newMessage(new Location(fieldInfo), Message.WRONG_LINKS,
                    "Expected [" + inspectedString + "], computed [" + computedString + "]"));
        }
    }
}
