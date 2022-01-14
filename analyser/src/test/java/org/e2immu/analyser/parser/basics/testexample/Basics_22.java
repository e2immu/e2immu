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

package org.e2immu.analyser.parser.basics.testexample;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

// catches a linking problem in main of 3: return value linked to variable that doesn't exist anymore
public class Basics_22 {

    public static byte[] loadBytes(String path) {
        String[] prefix = path.split("/");
        for (String url : prefix) {
            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                byteArrayOutputStream.write(url.getBytes());
                return byteArrayOutputStream.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("URL = " + url + ", Cannot read? " + e.getMessage());
            }
        }
        System.out.println("{} not found in class path" + path);
        return null;
    }

}
