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

package org.e2immu.analyser.resolver;

import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;


import java.util.Map;
import java.util.stream.Stream;

public interface Resolver extends ExpressionContext.ResolverRecursion {
    Stream<Message> getMessageStream();

    @NotNull
    Resolver child(InspectionProvider inspectionProvider,
                   E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                   boolean shallowResolver,
                   boolean storeComments);

    @Modified
    @NotNull(content = true)
    SortedTypes resolve(Map<TypeInfo, ExpressionContext> inspectedTypes);


    @Modified
    @NotNull(content = true)
    default SortedTypes resolve(InspectionProvider inspectionProvider,
                                     E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                     boolean shallowResolver,
                                     boolean storeComments,
                                     Map<TypeInfo, ExpressionContext> inspectedTypes) {
        Resolver child = child(inspectionProvider, e2ImmuAnnotationExpressions, shallowResolver, storeComments);
        return child.resolve(inspectedTypes);
    }
}
