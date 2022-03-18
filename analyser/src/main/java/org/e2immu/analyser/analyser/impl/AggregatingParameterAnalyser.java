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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.AnalyserComponents;
import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.FieldAnalyser;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.model.ParameterInfo;

import java.util.stream.Stream;

public class AggregatingParameterAnalyser extends ParameterAnalyserImpl {

    public AggregatingParameterAnalyser(AnalyserContext analyserContext, ParameterInfo parameterInfo) {
        super(analyserContext, parameterInfo);
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initialize(Stream<FieldAnalyser> fieldAnalyserStream) {

    }

    @Override
    public AnalyserResult analyse(int iteration) {
        return AnalyserResult.EMPTY;
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return "APA " + parameterInfo.fullyQualifiedName;
    }
}
