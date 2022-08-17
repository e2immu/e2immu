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

package org.e2immu.analyser.annotationxml.model;

import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.annotation.Immutable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class HasAnnotations {
    private List<Annotation> annotations = new ArrayList<>();

    void freeze() {
        annotations = List.copyOf(annotations);
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    protected void addAnnotations(List<AnnotationExpression> inspected, List<AnnotationExpression> analysed) {
        Set<String> e2immuAnnotationsWritten = new HashSet<>();
        for (AnnotationExpression ae : inspected) {
            boolean accept = ae.typeInfo().fullyQualifiedName.startsWith(Immutable.class.getPackageName())
                    && !ae.e2ImmuAnnotationParameters().isVerifyAbsent();
            if (accept) {
                e2immuAnnotationsWritten.add(ae.typeInfo().fullyQualifiedName);
                Annotation annotation = Annotation.from(ae);
                annotations.add(annotation);
            }
        }
        // these are always our annotations, typically of type COMPUTED
        // but the reader will make them CONTRACT...
        for (AnnotationExpression ae : analysed) {
            if (!e2immuAnnotationsWritten.contains(ae.typeInfo().fullyQualifiedName)) {
                Annotation annotation = Annotation.from(ae);
                annotations.add(annotation);
            }
        }
    }
}
