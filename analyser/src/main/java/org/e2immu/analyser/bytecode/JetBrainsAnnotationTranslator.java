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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.annotationxml.model.Annotation;
import org.e2immu.analyser.inspector.AbstractInspectionBuilder;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.AnnotationExpressionImpl;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.MemberValuePair;
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

    public <T> void mapAnnotations(List<Annotation> annotations, AbstractInspectionBuilder<T> inspectionBuilder) {
        for (Annotation annotation : annotations) {
            mapAnnotation(annotation, inspectionBuilder);
        }
    }

    private <T> void mapAnnotation(Annotation annotation, AbstractInspectionBuilder<T> inspectionBuilder) {
        if (ORG_JETBRAINS_ANNOTATIONS_NOTNULL.equals(annotation.name())) {
            if (inspectionBuilder instanceof ParameterInspectionImpl.Builder) {
                inspectionBuilder.addAnnotation(e2ImmuAnnotationExpressions.notNull);
            }
        } else if (annotation.name().startsWith(E2IMMU)) {
            inspectionBuilder.addAnnotation(toAnnotationExpression(annotation));
        }
    }

    private AnnotationExpression toAnnotationExpression(Annotation annotation) {
        TypeInfo typeInfo = e2ImmuAnnotationExpressions.get(annotation.name());
        MemberValuePair contractExpression = new MemberValuePair("contract", new BooleanConstant(primitives, true));
        return new AnnotationExpressionImpl(typeInfo, List.of(contractExpression));
    }
}
