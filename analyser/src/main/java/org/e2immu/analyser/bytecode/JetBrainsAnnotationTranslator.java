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
import org.e2immu.analyser.annotationxml.model.Value;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.ArrayList;
import java.util.Arrays;
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

    public <T> void mapAnnotations(List<Annotation> annotations, Inspection.InspectionBuilder<T> inspectionBuilder) {
        for (Annotation annotation : annotations) {
            mapAnnotation(annotation, inspectionBuilder);
        }
    }

    private <T> void mapAnnotation(Annotation annotation, Inspection.InspectionBuilder<T> inspectionBuilder) {
        if (ORG_JETBRAINS_ANNOTATIONS_NOTNULL.equals(annotation.name())) {
            if (inspectionBuilder instanceof ParameterInspection.Builder) {
                inspectionBuilder.addAnnotation(e2ImmuAnnotationExpressions.notNull);
            }
        } else if (annotation.name().startsWith(E2IMMU)) {
            inspectionBuilder.addAnnotation(toAnnotationExpression(annotation));
        }
    }

    private AnnotationExpression toAnnotationExpression(Annotation annotation) {
        TypeInfo typeInfo = e2ImmuAnnotationExpressions.get(annotation.name());
        MemberValuePair contractExpression = new MemberValuePair("contract", new BooleanConstant(primitives, true));
        Identifier id = Identifier.generate("asm convert");
        if (annotation.values().isEmpty()) {
            return new AnnotationExpressionImpl(id, typeInfo, List.of(contractExpression));
        }
        List<MemberValuePair> expressions = new ArrayList<>();
        expressions.add(contractExpression);
        for (Value value : annotation.values()) {
            expressions.add(new MemberValuePair(value.name, convert(value.val)));
        }
        return new AnnotationExpressionImpl(id, typeInfo, expressions);
    }

    private Expression convert(String string) {
        if ("true".equals(string)) return new BooleanConstant(primitives, true);
        if ("false".equals(string)) return new BooleanConstant(primitives, false);
        if (string.length() > 2 && string.charAt(0) == '{' && string.charAt(string.length() - 1) == '}') {
            String[] splitComma = string.substring(1, string.length() - 1).split(",");
            Identifier id = Identifier.generate("asm convert");
            return new ArrayInitializer(id, InspectionProvider.DEFAULT, Arrays.stream(splitComma).map(this::convert).toList());
        }
        try {
            Identifier id = Identifier.generate("asm convert");
            return new IntConstant(primitives, id, Integer.parseInt(string));
        } catch (NumberFormatException nfe) {
            // that's ok
        }
        return new StringConstant(primitives, string);
    }
}
