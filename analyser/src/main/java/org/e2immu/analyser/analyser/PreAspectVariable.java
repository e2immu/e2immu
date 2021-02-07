/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;

/**
 * Purpose: describe the state of the aspect before the modification.
 * Example:
 * <p>
 * add$Modification$Size(int post, int pre, E e) --> post will be mapped to "size()", while "pre" in the
 * expression will describe the "size()" before the modification took place.
 */
public record PreAspectVariable(ParameterizedType returnType,
                                Expression valueForProperties) implements Variable {

    @Override
    public ParameterizedType concreteReturnType() {
        return returnType;
    }

    @Override
    public ParameterizedType parameterizedType() {
        return returnType;
    }

    @Override
    public String simpleName() {
        return "pre";
    }

    @Override
    public String fullyQualifiedName() {
        return "pre";
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return SideEffect.NONE_CONTEXT;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("pre"));
    }
}
