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

package org.e2immu.analyser.resolver.impl;

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.MethodAnalyser;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.resolver.AnalyserGenerator;
import org.e2immu.analyser.resolver.TypeCycle;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListOfSortedTypes implements TypeCycle {
    private final List<SortedType> sortedTypes;

    public ListOfSortedTypes(List<SortedType> sortedTypes) {
        this.sortedTypes = sortedTypes;
    }

    @Override
    public AnalyserGenerator createAnalyserGeneratorAndGenerateAnalysers(Configuration configuration,
                                                                         AnalyserContext analyserContext) {
        return new DefaultAnalyserGeneratorImpl(sortedTypes, configuration, analyserContext);
    }

    @Override
    public String toString() {
        return sortedTypes.stream().map(st -> st.primaryType().fullyQualifiedName).collect(Collectors.joining(", "));
    }

    @Override
    public Stream<TypeInfo> primaryTypeStream() {
        return sortedTypes.stream().map(SortedType::primaryType);
    }
}
