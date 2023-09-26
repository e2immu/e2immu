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

package org.e2immu.analyser.visitor;

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.TypeMap;

public interface TypeMapVisitor {
    void visit(Data data);

    record Data(TypeMap typeMap, AnalyserContext analyserContext) {
        public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
            return analyserContext.getTypeAnalysis(typeInfo);
        }

        public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
            return analyserContext.getFieldAnalysis(fieldInfo);
        }

        public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
            return analyserContext.getMethodAnalysis(methodInfo);
        }

        public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
            return analyserContext.getParameterAnalysis(parameterInfo);
        }
    }
}
