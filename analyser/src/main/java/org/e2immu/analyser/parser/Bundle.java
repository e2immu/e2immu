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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.util.Logger;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.e2immu.analyser.util.Logger.log;

public class Bundle {
    private static final String BUNDLE = "messages.Messages";
    public static final Bundle INSTANCE = new Bundle();

    private Bundle() {
    }

    private Reference<ResourceBundle> ourBundle;

    public String get(String keyword) {
        return getBundle().getString(keyword);
    }

    /*
    easy way to change default locale: add -Duser.country=BE -Duser.language=nl to JVM options
     */
    private ResourceBundle getBundle() {
        ResourceBundle bundle = ourBundle == null ? null : ourBundle.get();
        if (bundle == null) {
            Locale locale =Locale.getDefault();
            log(Logger.LogTarget.CONFIGURATION, "Use resource bundle for locale {}", locale);
            bundle = ResourceBundle.getBundle(BUNDLE, locale);
            ourBundle = new SoftReference<>(bundle);
        }
        return bundle;
    }
}
