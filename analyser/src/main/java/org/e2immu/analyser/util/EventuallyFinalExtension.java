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

package org.e2immu.analyser.util;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.annotation.ExtensionClass;
import org.e2immu.support.EventuallyFinal;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@ExtensionClass(of = EventuallyFinal.class)
public class EventuallyFinalExtension {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(EventuallyFinalExtension.class);

    private EventuallyFinalExtension() {
        throw new UnsupportedOperationException();
    }

    public static <T> boolean setFinalAllowEquals(EventuallyFinal<T> eventuallyFinal, T t) {
        boolean isVariable = eventuallyFinal.isVariable();
        if (isVariable || !Objects.equals(eventuallyFinal.get(), t)) {
            try {
                eventuallyFinal.setFinal(t);
            } catch (RuntimeException re) {
                LOGGER.error("Overwriting final value: old: {}, new {}", eventuallyFinal.get(), t);
                LOGGER.error("ToString equal? {}", t.toString().equals(eventuallyFinal.get().toString()));
                ((Logger) LoggerFactory.getLogger(Configuration.EQUALS)).setLevel(Level.DEBUG);
                LOGGER.error("Computed equality: {}", t.equals(eventuallyFinal.get()));
                throw re;
            }
        }
        return isVariable;
    }
}
