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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.annotationxml.model.Annotation;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;

public class JetBrainsAnnotationTranslator {
    private static final String ORG_JETBRAINS_ANNOTATIONS_NOTNULL = "org.jetbrains.annotations.NotNull";
    private static final String E2IMMU = "org.e2immu.annotation";

    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Primitives primitives;

    public JetBrainsAnnotationTranslator(Primitives primitives, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.primitives = primitives;
    }

    public <T> void mapAnnotations(List<Annotation> annotations, AbstractInspectionBuilder inspectionBuilder) {
        for (Annotation annotation : annotations) {
            mapAnnotation(annotation, inspectionBuilder);
        }
    }

    private <T> void mapAnnotation(Annotation annotation, AbstractInspectionBuilder inspectionBuilder) {
        if (ORG_JETBRAINS_ANNOTATIONS_NOTNULL.equals(annotation.name())) {
            if (inspectionBuilder instanceof ParameterInspectionImpl.Builder) {
                inspectionBuilder.addAnnotation(e2ImmuAnnotationExpressions.notNull.get());
            }
        } else if (annotation.name().startsWith(E2IMMU)) {
            inspectionBuilder.addAnnotation(toAnnotationExpression(annotation));
        }
    }

    private AnnotationExpression toAnnotationExpression(Annotation annotation) {
        TypeInfo typeInfo = e2ImmuAnnotationExpressions.getFullyQualified(annotation.name());
        MemberValuePair contractExpression = new MemberValuePair("type",
                new VariableExpression(new FieldReference(primitives.annotationTypeContract, null)));
        return new AnnotationExpressionImpl(typeInfo, List.of(contractExpression));
    }
}
