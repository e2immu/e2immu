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

package org.e2immu.analyser.model;

import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Message;

import java.util.stream.Stream;

public interface LimitedStatementAnalysis {

    Statement statement();

    boolean haveLocalMessages();

    Stream<Message> localMessageStream();

    static LimitedStatementAnalysis startOfBlock(LimitedStatementAnalysis sa, int block) {
        return sa == null ? null : sa.startOfBlock(block);
    }
    
    LimitedStatementAnalysis startOfBlock(int block);

    // navigationData.blocks.get().get(0).orElse(null)
    LimitedStatementAnalysis navigationBlock0OrElseNull();

    OutputBuilder output(Qualification qualification);

    String index();

    //navigationData.replacement.isSet()
    boolean navigationReplacementIsSet();

    //navigationData.next.isSet()
    boolean navigationNextIsSet();

    // navigationData.next.get().orElse(null)
    LimitedStatementAnalysis navigationNextGetOrElseNull();

    //navigationData.replacement.get()
    LimitedStatementAnalysis navigationReplacementGet();

    // navigationData.hasSubBlocks()
    boolean navigationHasSubBlocks();

    // navigationData.blocks.get().get(0).isPresent()
    boolean navigationBlock0IsPresent();
}
