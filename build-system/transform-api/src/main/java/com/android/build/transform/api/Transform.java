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
import com.android.annotations.Nullable;
import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A Transform that processes intermediary build artifacts.
 * <p>
 * For each added transform, a new task is created. The action of adding a transform takes
 * care of handling dependencies between the tasks. This is done based on what the transform
 * processes. The output of the transform becomes consumable by other transforms and these
 * tasks get automatically linked together.
 * <p/>
 * The Transform indicates what it applies to (content, scope) and what it generates (content,
 * format):
 * <ul>
 *     <li>The {@link com.android.build.transform.api.ScopedContent.ContentType} is the type
 *     of the artifact that is consumed (via {@link #getInputTypes()}.) The output type
 *     can be different and specified through {@link #getOutputTypes()}.). A transform
 *     may process more than one type, and each type may show up in a different input. However,
 *     all outputs will be of the type(s) indicated in {@link #getOutputTypes()}.</li>
 *     <li>The {@link com.android.build.transform.api.ScopedContent.Scope} indicates
 *     (via {@link #getScopes()}) the scope that this transform applies to. It is possible to
 *     apply a transform only to the project's code, or only to the external libraries for
 *     instance.</li>
 *     <li>The referenced scopes {@link #getReferencedScopes()} allows receiving additional
 *     scopes for reference only. These scopes are not consumed by the transform and further
 *     transforms will receive them untouched. This can be useful when the transform need to
 *     see the rest of the classes but only applies to a subset of the scopes.</li>
 *     <li>The {@link com.android.build.transform.api.ScopedContent.Format} indicates (via
 *     {@link #getOutputFormat()}) what the output will be. In general,
 *     {@link com.android.build.transform.api.ScopedContent.Format#SINGLE_FOLDER} is the
 *     preferred format as it allows Gradle to provide incremental information.</li>
 * </ul>
 *
 * <p/>
 * A transform indicates how it transforms the content based on the interface it implements:
 * <ul>
 *     <li>{@link AsInputTransform}: This transform reads multiple scopes and outputs the
 *     transformed data in separate output for each scope. This allows later transforms
 *     to be applied to a smaller number of scopes. This is the preferred type for
 *     interoperability with other transforms.</li>
 *     <li>{@link CombinedTransform}: This transform reads multiple scopes and outputs the
 *     transformed data in a single folder. This folder is now tied to all the scopes it
 *     contain and later transforms can only process this data if they declare that they apply
 *     to all these scopes (or more).
 *     Applying such a transform will restrict the ability to add more transforms.</li>
 *     <li>{@link ForkTransform}: This transform works similarly to {@link AsInputTransform}
 *     when it comes to scopes (each input as a matching output to write to). However this
 *     transform must indicate a single {@link #getInputTypes()}), and several
 *     {@link #getOutputTypes()}. For each input, the transform will receive one output per
 *     declared output type.</li>
 *     <li>{@link NoOpTransform}: This transform does not have any normal output. Instead it
 *     can output data using the secondary outputs.</li>
 * </ul>
 *
 * <p/>
 * A transform receives input as {@link TransformInput}, which contains both file and scope/type
 * information. This is typically the output of a previous transform.
 * The output is handled by {@link TransformOutput} which has similar information. This can be
 * consumed by a later transform.
 * The content handled by TransformInput/Output is managed by the transform system, and their
 * location is not configurable.
 *
 * <p/>
 * Additionally, a transform can indicate secondary inputs/outputs. These are not handled by
 * previous or later transforms, and are not restricted by type handled by transform. They can
 * be anything.
 * It's up to each transform to manage where these files are, and to make sure that these files
 * are generated before the transform is called. This is done through additional parameters
 * when register the transform.
 *
 */
@Beta
@SuppressWarnings("MethodMayBeStatic")
public abstract class Transform {

    /**
     * Returns the unique name of the transform.
     *
     * <p/>
     * This is associated with the type of work that the transform does. It does not have to be
     * unique per variant.
     */
    @NonNull
    public abstract String getName();

    /**
     * Returns the type(s) of data that is consumed by the Transform. This may be more than
     * one type.
     */
    @NonNull
    public abstract Set<ScopedContent.ContentType> getInputTypes();

    /**
     * Returns the type(s) of data that is generated by the Transform. This may be more than
     * one type.
     *
     * <p/>
     * The default implementation returns {@link #getInputTypes()}.
     */
    @NonNull
    public Set<ScopedContent.ContentType> getOutputTypes() {
        return getInputTypes();
    }

    /**
     * Returns the scope(s) of the Transform. This indicates what the transform consumes, not in
     * term of content types, but in term of which streams it consumes.
     */
    @NonNull
    public abstract Set<ScopedContent.Scope> getScopes();

    /**
     * Returns the referenced scope(s) for the Transform. These scopes are not consumed by
     * the Transform. They are provided as inputs, but are still available as inputs for
     * other Transforms to consume.
     *
     * <p/>
     * The default implementation returns an empty Set.
     */
    @NonNull
    public Set<ScopedContent.Scope> getReferencedScopes() {
        return ImmutableSet.of();
    }

    /**
     * Returns the format of the output stream(s) that this Transform writes into. Null can be used
     * to indicate that every output stream uses the same format as the corresponding input stream
     * (only applicable to some transform types).
     */
    @Nullable
    public abstract ScopedContent.Format getOutputFormat();

    /**
     * Returns a list of additional file(s) that this Transform needs to run.
     *
     * <p/>
     * Changes to files returned in this list will trigger a new execution of the Transform
     * even if the streams haven't been touched.
     * <p/>
     * Any changes to these files will trigger a non incremental execution.
     *
     * <p/>
     * The default implementation returns an empty collection.
     */
    @NonNull
    public Collection<File> getSecondaryFileInputs() {
        return ImmutableList.of();
    }

    /**
     * Returns a list of additional (out of streams) file(s) that this Transform creates.
     *
     * <p/>
     * These File instances can only represent files, not folders. For folders, use
     * {@link #getSecondaryFolderOutputs()}
     *
     * <p/>
     * Changes to files returned in this list will trigger a new execution of the Transform
     * even if the streams haven't been touched.
     * <p/>
     * Changes to these output files force a non incremental execution.
     *
     * <p/>
     * The default implementation returns an empty collection.
     */
    @NonNull
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of();
    }

    /**
     * Returns a list of additional (out of streams) folder(s) that this Transform creates.
     *
     * <p/>
     * These File instances can only represent folders. For files, use
     * {@link #getSecondaryFileOutputs()}
     *
     * <p/>
     * Changes to folders returned in this list will trigger a new execution of the Transform
     * even if the streams haven't been touched.
     * <p/>
     * Changes to these output folders force a non incremental execution.
     *
     * <p/>
     * The default implementation returns an empty collection.
     */
    @NonNull
    public Collection<File> getSecondaryFolderOutputs() {
        return ImmutableList.of();
    }

    /**
     * Returns a map of non-file input parameters using a unique identifier as the map key.
     *
     * <p/>
     * Changes to values returned in this map will trigger a new execution of the Transform
     * even if the streams haven't been touched.
     * <p/>
     * Changes to these values force a non incremental execution.
     *
     * <p/>
     * The default implementation returns an empty Map.
     */
    @NonNull
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of();
    }

    /**
     * Returns whether the Transform can perform incremental work.
     *
     * <p/>
     * If it does, then the TransformInput will contains a list of changed/removed/added files.
     */
    public abstract boolean isIncremental();
}