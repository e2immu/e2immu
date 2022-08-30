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

package org.e2immu.analyser.parser.independence.testexample;


import org.e2immu.annotation.ImmutableContainer;

import java.util.List;
import java.util.stream.Stream;

public class Independent_4 {
    record MethodInfo(List<String> names) {
    }

    interface MethodAnalyser {
        MethodInfo getMethodInfo();

        String getName();
    }

    record MethodAnalyserImpl(MethodInfo methodInfo) implements MethodAnalyser {

        @Override
        public MethodInfo getMethodInfo() {
            return methodInfo;
        }

        @Override
        public String getName() {
            return methodInfo.names.get(0);
        }
    }

    interface AnalyserContext {
        AnalyserContext DEFAULT = new AnalyserContext() {
            @Override
            public MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
                return new MethodAnalyserImpl(methodInfo);
            }
        };

        default MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
            return null;
        }

        default Stream<MethodAnalyser> methodAnalyserStream() {
            return null;
        }

        // IMPROVE this is debatable!!
        @ImmutableContainer("null")
        default Stream<MethodAnalyser> parallelMethodAnalyserStream() {
            return methodAnalyserStream();
        }
    }

    // we make sure there is in implementation of methodAnalyserStream, so that a vanilla value is returned!
    record AnalyserContextImpl(List<MethodAnalyser> methodAnalysers) implements AnalyserContext {
        @Override
        public Stream<MethodAnalyser> methodAnalyserStream() {
            return this.methodAnalysers.stream();
        }
    }
}
