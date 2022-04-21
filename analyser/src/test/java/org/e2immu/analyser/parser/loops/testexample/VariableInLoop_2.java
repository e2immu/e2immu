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

package org.e2immu.analyser.parser.loops.testexample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;


public record VariableInLoop_2(Map<String[], List<URL>> data) {
    private static final Logger LOGGER = LoggerFactory.getLogger(VariableInLoop_2.class);

    static class ResourceAccessException extends RuntimeException {
        public ResourceAccessException(String msg) {
            super(msg);
        }
    }

    public VariableInLoop_2 {
        assert data != null;
    }

    public byte[] loadBytes(String path) {
        String[] prefix = path.split("/");
        List<URL> urls = data.get(prefix);
        if (urls != null) {
            for (URL url : urls) {
                try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                    url.openStream().transferTo(byteArrayOutputStream);
                    return byteArrayOutputStream.toByteArray();
                } catch (IOException e) {
                    throw new ResourceAccessException("URL = " + url + ", Cannot read? " + e.getMessage());
                }
            }
        }
        LOGGER.debug("{} not found in class path", path);
        return null;
    }
}
