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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

import java.util.ArrayList;
import java.util.List;

/*
From the manual
 */
public class Modification_16_M {

    @Container
    static class ErrorMessage {
        private String message;

        public ErrorMessage(String message) {
            this.message = message;
        }

        @NotModified
        public String getMessage() {
            return message;
        }

        @Modified
        public void setMessage(String message) {
            this.message = message;
        }
    }

    @Container
    interface ErrorRegistry {
        @NotModified
        List<ErrorMessage> getErrors();

        // parameter ErrorMessage @Dependent implicitly
        @Modified
        void addError(@NotModified ErrorMessage errorMessage);
    }

    static class FaultyImplementation implements ErrorRegistry {

        private final List<ErrorMessage> messages = new ArrayList<>();

        @Override
        public List<ErrorMessage> getErrors() {
            return messages;
        }

        @Override
        public void addError(ErrorMessage errorMessage) {
            messages.add(errorMessage);
            errorMessage.setMessage("Added: " + errorMessage.message); // raises error!
        }
    }
}
