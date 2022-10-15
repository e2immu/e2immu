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

package org.e2immu.analyser.model;


import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

import java.util.List;

@Container
public interface Inspection {

    enum Access {
        PRIVATE(0), PACKAGE(1),  PROTECTED(2), PUBLIC(3);

        private final int level;

        Access(int level) {
            this.level = level;
        }

        public Access combine(Access other) {
            if (level < other.level) return this;
            return other;
        }

        public boolean le(Access other) {
            return level <= other.level;
        }
    }

    Access getAccess();

    Comment getComment();

    boolean isSynthetic();

    default boolean isPublic() {
        return getAccess() == Access.PUBLIC;
    }

    default boolean isPrivate() {
        return getAccess() == Access.PRIVATE;
    }

    default boolean isProtected() {
        return getAccess() == Access.PROTECTED;
    }

    default boolean isPackagePrivate() {
        return getAccess() == Access.PACKAGE;
    }

    @NotNull(content = true)
    List<AnnotationExpression> getAnnotations();

    interface InspectionBuilder<B> {
        @Modified
        B setSynthetic(boolean b);

        @Modified
        B addAnnotation(AnnotationExpression annotationExpression);

        @Fluent
        B setAccess(Access access);

        void setComment(Comment comment);
    }
}
