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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.util.SetUtil;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/*
To avoid recursive reEvaluation/inlining/...
 */
public record ForwardReEvaluationInfo(Set<MethodInfo> inlining) {

    public static ForwardReEvaluationInfo DEFAULT = new ForwardReEvaluationInfo(Set.of());

    public ForwardReEvaluationInfo addMethod(MethodInfo methodInfo) {
        Set<MethodInfo> top = topOfOverloadingHierarchy(methodInfo);
        assert Collections.disjoint(inlining, top);
        return new ForwardReEvaluationInfo(SetUtil.immutableUnion(inlining, top));
    }

    public boolean allowInline(MethodInfo methodInfo) {
        Set<MethodInfo> top = topOfOverloadingHierarchy(methodInfo);
        return Collections.disjoint(inlining, top);
    }

    private Set<MethodInfo> topOfOverloadingHierarchy(MethodInfo methodInfo) {
        MethodResolution methodResolution = methodInfo.methodResolution.get();
        if (methodResolution.overrides().isEmpty()) return Set.of(methodInfo);
        return methodResolution.overrides().stream().flatMap(mi -> topOfOverloadingHierarchy(mi).stream()).collect(Collectors.toUnmodifiableSet());
    }
}
