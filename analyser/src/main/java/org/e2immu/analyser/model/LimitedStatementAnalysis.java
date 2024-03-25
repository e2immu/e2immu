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
import org.e2immu.annotation.NotNull;

import java.util.stream.Stream;

public interface LimitedStatementAnalysis extends  CommonHasNavigationDataAndLimitedStatementAnalysis {

    default  boolean haveLocalMessages(){
        throw new UnsupportedOperationException();
    }

    @NotNull
    default  Stream<Message> localMessageStream(){
        throw new UnsupportedOperationException();
    }

    static LimitedStatementAnalysis startOfBlock(LimitedStatementAnalysis sa, int block) {
        return sa == null ? null : sa.startOfBlock(block);
    }

    default  LimitedStatementAnalysis startOfBlock(int block){
        throw new UnsupportedOperationException();
    }

    // navigationData.blocks.get().get(0).orElse(null)
    default LimitedStatementAnalysis navigationBlock0OrElseNull(){
        throw new UnsupportedOperationException();
    }

    default  OutputBuilder output(Qualification qualification){
        throw new UnsupportedOperationException();
    }

    //navigationData.replacement.isSet()
    default  boolean navigationReplacementIsSet(){
        throw new UnsupportedOperationException();
    }

    //navigationData.next.isSet()
    default  boolean navigationNextIsSet(){
        throw new UnsupportedOperationException();
    }

    // navigationData.next.get().orElse(null)
    default LimitedStatementAnalysis navigationNextGetOrElseNull(){
        throw new UnsupportedOperationException();
    }

    //navigationData.replacement.get()
    default LimitedStatementAnalysis navigationReplacementGet(){
        throw new UnsupportedOperationException();
    }

    // navigationData.hasSubBlocks()
    default boolean navigationHasSubBlocks(){
        throw new UnsupportedOperationException();
    }

    // navigationData.blocks.get().get(0).isPresent()
    default  boolean navigationBlock0IsPresent(){
        throw new UnsupportedOperationException();
    }
}
