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

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.model.TypeInfo;

import java.util.stream.Stream;

/**
 * A list of types which forms a cycle with respect to dependencies (extension, type usage, method calls).
 * The list is ordered according to a cycle breaking algorithm.
 * <p>
 * Each type has a list of methods and fields which need analysing too.
 * Method call cycles have been detected, and have been broken (in ignoreMeBecauseOfPartOfCallCycle).
 * In a type cycle, all methods, fields and types are analysed by one PrimaryTypeAnalyser.
 * <p>
 * The primary type analyser can analyse as follows:
 * (1) methods, fields, types; each group alphabetically sorted (CURRENT METHOD A)
 * (2) methods, fields, types; each group according to the order presented: typesInCycle -> methodsInType,
 * typesInCycle -> fieldsInType, typesInCycle (CURRENT METHOD B)
 * (3) (methods, fields, type) per type, really as presented (NEW METHOD C, NOT YET IMPLEMENTED)
 * (4) methods+fields+types all sorted according to one system (NEW METHOD D, NOT YET IMPLEMENTED)
 */
public interface TypeCycle {

    AnalyserGenerator createAnalyserGeneratorAndGenerateAnalysers(AnalyserContext analyserContext);

    Stream<TypeInfo> primaryTypeStream();

    int size();

    TypeInfo first();
}
