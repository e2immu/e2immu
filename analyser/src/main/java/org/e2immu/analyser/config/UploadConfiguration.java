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
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Fluent;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@E2Immutable
public class UploadConfiguration {
    public static final String DEFAULT_ANNOTATION_SERVER_URL = "http://localhost:8281/v1";
    public static final String DEFAULT_PROJECT = "default";

    // upload
    public final boolean upload;
    public final List<String> uploadPackages;
    public final String annotationServerUrl;
    public final String projectName;

    public UploadConfiguration(boolean upload,
                               String annotationServerUrl,
                               String projectName,
                               List<String> uploadPackages) {

        this.annotationServerUrl = annotationServerUrl;
        this.projectName = projectName;
        this.upload = upload;
        this.uploadPackages = uploadPackages;
    }

    @Override
    public String toString() {
        return new StringJoiner("\n")
                .add("upload: " + upload)
                .add("uploadPackages: " + uploadPackages)
                .add("annotationServerUrl: " + annotationServerUrl)
                .add("projectName: " + projectName) + "\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadConfiguration that = (UploadConfiguration) o;
        return upload == that.upload &&
                uploadPackages.equals(that.uploadPackages) &&
                annotationServerUrl.equals(that.annotationServerUrl) &&
                projectName.equals(that.projectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(upload, uploadPackages, annotationServerUrl, projectName);
    }

    public String createUrlWithProjectName(String action) {
        try {
            return annotationServerUrl + "/" + action + "/" + URLEncoder.encode(projectName, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }

    public boolean accept(String packageOfType) {
        if (uploadPackages.isEmpty()) return true;
        for (String prefix : uploadPackages) {
            if (prefix.endsWith(".")) {
                String withoutDot = prefix.substring(0, prefix.length() - 1);
                if (packageOfType.startsWith(withoutDot)) {
                    return true;
                }
            } else if (prefix.equals(packageOfType)) return true;
        }
        return false;
    }

    @Container
    public static class Builder {
        private String projectName;
        private String annotationServerUrl;
        private boolean upload;
        private final List<String> uploadPackages = new ArrayList<>();

        public UploadConfiguration build() {
            return new UploadConfiguration(upload,
                    annotationServerUrl == null ? DEFAULT_ANNOTATION_SERVER_URL : annotationServerUrl,
                    projectName == null ? DEFAULT_PROJECT : projectName,
                    List.copyOf(uploadPackages)
            );
        }


        @Fluent
        public Builder setAnnotationServerUrl(String annotationServerUrl) {
            this.annotationServerUrl = annotationServerUrl;
            return this;
        }

        @Fluent
        public Builder setProjectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        @Fluent
        public Builder setUpload(boolean upload) {
            this.upload = upload;
            return this;
        }

        @Fluent
        public Builder addUploadPackage(String... packages) {
            this.uploadPackages.addAll(Arrays.asList(packages));
            return this;
        }

    }
}
