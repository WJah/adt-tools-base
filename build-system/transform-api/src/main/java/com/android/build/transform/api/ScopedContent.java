/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.transform.api;

import com.android.annotations.NonNull;
import com.google.common.annotations.Beta;

import java.util.Set;

/**
 * Represent content manipulated during the build.
 *
 * <p/>
 * The content has a type (classes, dex, resources), a format (jar(s), folder(s)), and a scope
 * (project, external library, sub-project, etc...).
 *
 * <p/>
 * This interface does not actually represent the content itself. This is provided by children
 * interfaces.
 */
@Beta
public interface ScopedContent {

    /**
     * The type of of the content.
     */
    enum ContentType {
        /**
         * This is java class files. This can be in a folder or a jar depending on
         * the {@link Format}.
         */
        CLASSES,
        /**
         * This is a dex files.
         */
        DEX,
        /**
         * This is standard Java resources. This can be in a folder or a jar depending on
         * the {@link Format}.
         */
        RESOURCES
    }

    /**
     * The format in which the content is represented.
     */
    enum Format {
        /**
         * The content is directly under the root folder(s).
         *
         * <p/>
         * This means that in the case of java class files, the files should be in folders
         * matching their package names, directly under the root folder(s).
         */
        SINGLE_FOLDER,
        /**
         * This means that there is an extra level of folders under the root folder(s).
         *
         * <p/>
         * There can only be a single level of additional folders.
         * There can be no content directly under the root folder(s).
         */
        MULTI_FOLDER,
        /**
         * The content is jar(s).
         *
         * <p/>
         * As Input, there can be one or more jar files.
         * As output, a transform can only write a single jar files.
         */
        JAR
    }

    /**
     * The scope of the content.
     *
     * <p/>
     * This indicates what the content represents, so that Transforms can apply to only part(s)
     * of the classes or resources that the build manipulates.
     */
    enum Scope {
        /** Only the project content */
        PROJECT,
        /** Only the project's local dependencies (local jars) */
        PROJECT_LOCAL_DEPS,
        /** Only the sub-projects. */
        SUB_PROJECTS,
        /** Only the sub-projects's local dependencies (local jars). */
        SUB_PROJECTS_LOCAL_DEPS,
        /** Only the external libraries */
        EXTERNAL_LIBRARIES,
        /** Code that is being tested by the current variant, including dependencies */
        TESTED_CODE,
        /** Local or remote dependencies that are provided-only */
        PROVIDED_ONLY,
    }


    /**
     * Returns the type of content that the stream represents.
     *
     * <p/>
     * It's never null nor empty, but can contain several types.
     */
    @NonNull
    Set<ContentType> getContentTypes();

    /**
     * Returns the scope of the stream.
     *
     * <p/>
     * It's never null nor empty, but can contain several scopes.
     */
    @NonNull
    Set<Scope> getScopes();

    /**
     * Returns the format of the stream.
     */
    @NonNull
    Format getFormat();
}