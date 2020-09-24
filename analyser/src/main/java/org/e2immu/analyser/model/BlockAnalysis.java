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

package org.e2immu.analyser.model;

import org.e2immu.analyser.model.statement.Block;

import java.util.Iterator;
import java.util.List;

public class BlockAnalysis {

    public final Block block;
    public final String index;
    public final StatementAnalysis firstStatement;

    public BlockAnalysis(MethodAnalysis methodAnalysis, Block block, List<> index) {
        this.block = block;
        this.index = index;
        if (block == Block.EMPTY_BLOCK) {
            firstStatement = null;
        } else {
            firstStatement = recursivelyCreateAnalysisObjects(methodAnalysis, block,)
        }
    }


    public static StatementAnalysis recursivelyCreateAnalysisObjects() {
        Iterator<? extends Element> statementIterator = block.subElements().iterator();
        assert statementIterator.hasNext();
        Statement statement = (Statement) statementIterator.next();

        return new StatementAnalysis(me);
    }
 }
