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

package org.e2immu.analyser.inspector;


import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Inspection;
import org.e2immu.support.AddOnceSet;

import java.util.List;

public abstract class AbstractInspectionBuilder<B> implements Inspection, Inspection.InspectionBuilder<B> {

    protected final AddOnceSet<AnnotationExpression> annotations = new AddOnceSet<>();
    private boolean synthetic;

    @SuppressWarnings("unchecked")
    public B addAnnotation(AnnotationExpression annotationExpression) {
        if (!annotations.contains(annotationExpression)) {
            annotations.add(annotationExpression);
        }
        return (B) this; // unchecked cast saves us 4 copies
    }

    @Override
    public List<AnnotationExpression> getAnnotations() {
        return List.copyOf(annotations.toImmutableSet());
    }

    @Override
    public boolean hasAnnotation(AnnotationExpression annotationExpression) {
        return annotations.contains(annotationExpression);
    }

    @Override
    public boolean isSynthetic() {
        return synthetic;
    }

    @SuppressWarnings("unchecked")
    public B setSynthetic(boolean synthetic) {
        this.synthetic = synthetic;
        return (B) this; // saves us copies
    }
}
