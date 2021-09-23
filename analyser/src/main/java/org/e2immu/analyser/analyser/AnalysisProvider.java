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

import org.e2immu.analyser.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface AnalysisProvider {
    Logger LOGGER = LoggerFactory.getLogger(AnalysisProvider.class);

    FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo);

    ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo);

    default TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        return getTypeAnalysis(typeInfo);
    }

    TypeAnalysis getTypeAnalysis(TypeInfo typeInfo);

    MethodAnalysis getMethodAnalysis(MethodInfo methodInfo);

    AnalysisProvider DEFAULT_PROVIDER = new AnalysisProvider() {

        @Override
        public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
            return fieldInfo.fieldAnalysis.get();
        }

        @Override
        public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
            return parameterInfo.parameterAnalysis.get();
        }

        @Override
        public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
            return typeInfo.typeAnalysis.get("Type analysis of "+typeInfo.fullyQualifiedName);
        }

        @Override
        public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
            try {
                return methodInfo.methodAnalysis.get("Method analysis of "+methodInfo.fullyQualifiedName);
            } catch (RuntimeException re) {
                LOGGER.error("Caught exception trying to obtain default method analysis for " + methodInfo.fullyQualifiedName());
                throw re;
            }
        }
    };

    AnalysisProvider NULL_IF_NOT_SET = new AnalysisProvider() {
        @Override
        public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
            return fieldInfo.fieldAnalysis.getOrDefaultNull();
        }

        @Override
        public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
            return parameterInfo.parameterAnalysis.getOrDefaultNull();
        }

        @Override
        public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
            return typeInfo.typeAnalysis.getOrDefaultNull();
        }

        @Override
        public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
            return methodInfo.methodAnalysis.getOrDefaultNull();
        }
    };
}
