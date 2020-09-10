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

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Statement;

import java.util.List;

public abstract class StatementWithStructure implements Statement {
    public final CodeOrganization codeOrganization;
    public static final CodeOrganization EMPTY_CODE_ORGANIZATION = new CodeOrganization.Builder().build();

    public StatementWithStructure() {
        codeOrganization = EMPTY_CODE_ORGANIZATION;
    }

    public StatementWithStructure(CodeOrganization codeOrganization) {
        this.codeOrganization = codeOrganization;
    }

    @Override
    public CodeOrganization codeOrganization() {
        return codeOrganization;
    }

}
