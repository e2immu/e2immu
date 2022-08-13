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

package org.e2immu.annotatedapi.java;

import org.e2immu.annotation.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandlerFactory;
import java.util.jar.JarFile;

public class JavaNet {
    final static String PACKAGE_NAME = "java.net";

    /*
    we take the view that a URL is immutable
     */
    @ImmutableContainer
    interface URL$ {
        @NotNull
        InputStream openStream();

        // not modifying!!
        @NotNull
        URLConnection openConnection();

        // static method setting some info regarding the runtime system, to be ignored by us.
        @StaticSideEffects
        void setURLStreamHandlerFactory(URLStreamHandlerFactory fac);
    }

    @ImmutableContainer
    interface URI$ {
        @NotNull
        URL toURL();
    }

    @Container
    interface URLConnection$ {
        @Modified
        void addRequestProperty(String key, String value);

        @Modified
        void connect();

        // dependent!! the output stream writes to this connection
        OutputStream getOutputStream();
    }

    @Container
    interface JarURLConnection$ {
        String getEntryName();

        @NotNull
        JarFile getJarFile();
    }
}
