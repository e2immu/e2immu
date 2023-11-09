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

package org.e2immu.analyser.config;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;

import java.io.File;

public record InspectorConfiguration(boolean storeComments,
                                     String graphDirectory) {

    @Override
    public String toString() {
        return "InspectionConfiguration:" +
                "\n    storeComments=" + storeComments +
                "\n   graphDirectory='" + graphDirectory + "'";
    }

    @Container
    public static class Builder {
        private boolean storeComments;
        private String graphDirectory;

        public InspectorConfiguration build() {
            return new InspectorConfiguration(storeComments, graphDirectory);
        }

        @Fluent
        public Builder setStoreComments(boolean storeComments) {
            this.storeComments = storeComments;
            return this;
        }

        @Fluent
        public Builder setGraphDirectory(String graphDirectory) {
            this.graphDirectory = graphDirectory;
            return this;
        }
    }
}
