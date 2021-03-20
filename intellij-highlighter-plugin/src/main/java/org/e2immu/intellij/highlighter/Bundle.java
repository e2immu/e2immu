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

package org.e2immu.intellij.highlighter;

import com.intellij.CommonBundle;
import com.intellij.reference.SoftReference;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Singleton;
import org.jetbrains.annotations.PropertyKey;

import java.lang.ref.Reference;
import java.util.ResourceBundle;

@Singleton
@Container
public class Bundle {
    private static final String BUNDLE = "messages.E2ImmuHighlighterBundle";
    public static final Bundle INSTANCE = new Bundle();

    private Bundle() {
    }

    private Reference<ResourceBundle> ourBundle;

    public String get(@PropertyKey(resourceBundle = BUNDLE) String keyword, Object... params) {
        ResourceBundle bundle = getBundle();
        if (bundle == null) return "";
        return CommonBundle.message(bundle, keyword, params);
    }

    private ResourceBundle getBundle() {
        ResourceBundle bundle = SoftReference.dereference(ourBundle);
        if (bundle == null) {
            bundle = ResourceBundle.getBundle(BUNDLE);
            ourBundle = new SoftReference<>(bundle);
        }
        return bundle;
    }

}
