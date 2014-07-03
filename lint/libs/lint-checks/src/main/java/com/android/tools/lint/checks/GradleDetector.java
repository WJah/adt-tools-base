/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.lint.checks;

import static com.android.SdkConstants.FD_BUILD_TOOLS;
import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.SdkConstants.FD_M2_REPOSITORY;
import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER;
import static com.android.tools.lint.checks.ManifestDetector.TARGET_NEWER;
import static com.google.common.base.Charsets.UTF_8;
import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Checks Gradle files for potential errors
 */
public class GradleDetector extends Detector implements Detector.GradleScanner {
    private static final Implementation IMPLEMENTATION = new Implementation(
            GradleDetector.class,
            Scope.GRADLE_SCOPE);

    /** Obsolete dependencies */
    public static final Issue DEPENDENCY = Issue.create(
            "GradleDependency", //$NON-NLS-1$
            "Obsolete Gradle Dependency",
            "Looks for old or obsolete Gradle library dependencies",
            "This detector looks for usages of libraries where the version you are using " +
            "is not the current stable release. Using older versions is fine, and there are " +
            "cases where you deliberately want to stick with an older version. However, " +
            "you may simply not be aware that a more recent version is available, and that is " +
            "what this lint check helps find.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Incompatible Android Gradle plugin */
    public static final Issue GRADLE_PLUGIN_COMPATIBILITY = Issue.create(
            "AndroidGradlePluginVersion", //$NON-NLS-1$
            "Incompatible Android Gradle Plugin",
            "Ensures that the Android Gradle plugin version is compatible with this SDK",
            "Not all versions of the Android Gradle plugin are compatible with all versions " +
            "of the SDK. If you update your tools, or if you are trying to open a project that " +
            "was built with an old version of the tools, you may need to update your plugin " +
            "version number.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Invalid or dangerous paths */
    public static final Issue PATH = Issue.create(
            "GradlePath", //$NON-NLS-1$
            "Gradle Path Issues",
            "Looks for Gradle path problems such as using platform specific path separators",
            "Gradle build scripts are meant to be cross platform, so file paths use " +
            "Unix-style path separators (a forward slash) rather than Windows path separators " +
            "(a backslash). Similarly, to keep projects portable and repeatable, avoid " +
            "using absolute paths on the system; keep files within the project instead. To " +
            "share code between projects, consider creating an android-library and an AAR " +
            "dependency",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Constructs the IDE support struggles with */
    public static final Issue IDE_SUPPORT = Issue.create(
            "GradleIdeError", //$NON-NLS-1$
            "Gradle IDE Support Issues",
            "Looks for constructs in Gradle files which affect IDE usage",
            "Gradle is highly flexible, and there are things you can do in Gradle files which " +
            "can make it hard or impossible for IDEs to properly handle the project. This lint " +
            "check looks for constructs that potentially break IDE support.",
            Category.CORRECTNESS,
            4,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Using + in versions */
    public static final Issue PLUS = Issue.create(
            "GradleDynamicVersion", //$NON-NLS-1$
            "Gradle Dynamic Version",
            "Looks for dependencies using a dynamic version rather than a fixed version",
            "Using `+` in dependencies lets you automatically pick up the latest available " +
            "version rather than a specific, named version. However, this is not recommended; " +
            "your builds are not repeatable; you may have tested with a slightly different " +
            "version than what the build server used. (Using a dynamic version as the major " +
            "version number is more problematic than using it in the minor version position.)",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION).setEnabledByDefault(false);

    /** Accidentally calling a getter instead of your own methods */
    public static final Issue GRADLE_GETTER = Issue.create(
            "GradleGetter", //$NON-NLS-1$
            "Gradle Implicit Getter Call",
            "Identifies accidental calls to implicit getters",
            "Gradle will let you replace specific constants in your build scripts with method " +
            "calls, so you can for example dynamically compute a version string based on your " +
            "current version control revision number, rather than hardcoding a number.\n" +
            "\n" +
            "When computing a version name, it's tempting to for example call the method to do " +
            "that `getVersionName`. However, when you put that method call inside the " +
            "`defaultConfig` block, you will actually be calling the Groovy getter for the "  +
            "`versionName` property instead. Therefore, you need to name your method something " +
            "which does not conflict with the existing implicit getters. Consider using " +
            "`compute` as a prefix instead of `get`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Using incompatible versions */
    public static final Issue COMPATIBILITY = Issue.create(
            "GradleCompatible", //$NON-NLS-1$
            "Incompatible Gradle Versions",
            "Ensures that tool and library versions are compatible",

            "There are some combinations of libraries, or tools and libraries, that are " +
            "incompatible, or can lead to bugs. One such incompatibility is compiling with " +
            "a version of the Android support libraries that is not the latest version (or in " +
            "particular, a version lower than your `targetSdkVersion`.)",

            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            IMPLEMENTATION);

    public static final Issue REMOTE_VERSION = Issue.create(
            "NewerVersionAvailable", //$NON-NLS-1$
            "Newer Library Versions Available",
            "Looks for Gradle library dependencies that can be replaced by newer versions",
            "This detector checks with a central repository to see if there are newer versions " +
            "available for the dependencies used by this project.\n" +
            "This is similar to the `GradleDependency` check, which checks for newer versions " +
            "available in the Android SDK tools and libraries, but this works with any " +
            "MavenCentral dependency, and connects to the library every time, which makes " +
            "it more flexible but also *much* slower.",
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            IMPLEMENTATION).setEnabledByDefault(false);

    /** Accidentally using octal numbers */
    public static final Issue ACCIDENTAL_OCTAL = Issue.create(
            "AccidentalOctal", //$NON-NLS-1$
            "Accidental Octal",
            "Looks for integer literals that are interpreted as octal numbers",

            "In Groovy, an integer literal that starts with a leading 0 will be interpreted " +
            "as an octal number. That is usually (always?) an accident and can lead to " +
            "subtle bugs, for example when used in the `versionCode` of an app.",

            Category.CORRECTNESS,
            2,
            Severity.ERROR,
            IMPLEMENTATION);

    /** A statement appearing at the root of the top-level build file that shouldn't be there */
    public static final Issue IMPROPER_PROJECT_LEVEL_STATEMENT = Issue.create(
            "ImproperProjectLevelStatement", //$NON-NLS-1$
            "Improper project-level build file statement",
            "Looks for statements that likely don't belong in a project-level build file",

            "The top-level build file in a multi-module project is generally used to configure project-wide " +
            "build parameters and often does not describe a corresponding top-level module. In build files " +
            "without a module, it is an error to use build file constructs that require a module; doing so can " +
            "lead to unpredictable error messages.",

            Category.CORRECTNESS,
            2,
            Severity.WARNING,
            IMPLEMENTATION);

    /** A statement appearing within the wrong scope of a build file */
    public static final Issue MISPLACED_STATEMENT = Issue.create(
            "MisplacedStatement", //$NON-NLS-1$
            "Misplaced statement",
            "Looks for build file statements that belong elsewhere in the build file",

            "Most build file directives only make sense in certain contexts in the build file. If you put a " +
            "statement in the wrong place, you can get errors or unexpected behavior.",

            Category.CORRECTNESS,
            2,
            Severity.WARNING,
            IMPLEMENTATION);

    private int mMinSdkVersion;
    private int mCompileSdkVersion;
    private int mTargetSdkVersion;
    private Object myAndroidPluginCookie;
    private Object myDependenciesCookie;
    private Object myRepositoriesCookie;
    private Object myAndroidBlockCookie;

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    @NonNull
    public Speed getSpeed(@SuppressWarnings("UnusedParameters") @NonNull Issue issue) {
        return issue == REMOTE_VERSION ? Speed.REALLY_SLOW : Speed.NORMAL;
    }

    // ---- Implements Detector.GradleScanner ----

    @Override
    public void visitBuildScript(@NonNull Context context, Map<String, Object> sharedData) {
    }

    @SuppressWarnings("UnusedDeclaration")
    protected static boolean isInterestingBlock(
            @NonNull String parent,
            @Nullable String parentParent) {
        return parent.equals("defaultConfig")
                || parent.equals("android")
                || parent.equals("dependencies")
                || parent.equals("repositories")
                || parentParent != null && parentParent.equals("buildTypes");
    }

    protected static boolean isInterestingStatement(
            @NonNull String statement,
            @Nullable String parent) {
        return parent == null && statement.equals("apply");
    }

    @SuppressWarnings("UnusedDeclaration")
    protected static boolean isInterestingProperty(
            @NonNull String property,
            @SuppressWarnings("UnusedParameters")
            @NonNull String parent,
            @Nullable String parentParent) {
        return property.equals("targetSdkVersion")
                || property.equals("buildToolsVersion")
                || property.equals("versionName")
                || property.equals("versionCode")
                || property.equals("compileSdkVersion")
                || property.equals("minSdkVersion")
                || property.equals("applicationIdSuffix")
                || property.equals("packageName")
                || property.equals("packageNameSuffix")
                || parent.equals("dependencies");
    }

    protected void checkOctal(
            @NonNull Context context,
            @NonNull String value,
            @NonNull Object cookie) {
        if (value.length() >= 2
                && value.charAt(0) == '0'
                && (value.length() > 2 || value.charAt(1) >= '8'
                && isInteger(value))
                && context.isEnabled(ACCIDENTAL_OCTAL)) {
            String message = "The leading 0 turns this number into octal which is probably "
                    + "not what was intended";
            try {
                long numericValue = Long.decode(value);
                message += " (interpreted as " + numericValue + ")";
            } catch (NumberFormatException nufe) {
                message += " (and it is not a valid octal number)";
            }
            report(context, cookie, ACCIDENTAL_OCTAL, message);
        }
    }

    /** Called with for example "android", "defaultConfig", "minSdkVersion", "7"  */
    @SuppressWarnings("UnusedDeclaration")
    protected void checkDslPropertyAssignment(
        @NonNull Context context,
        @NonNull String property,
        @NonNull String value,
        @NonNull String parent,
        @Nullable String parentParent,
        @NonNull Object valueCookie,
        @NonNull Object statementCookie) {
        if (parent.equals("defaultConfig")) {
            if (property.equals("targetSdkVersion")) {
                int version = getIntLiteralValue(value, -1);
                if (version > 0 && version < context.getClient().getHighestKnownApiLevel()) {
                    String message =
                            "Not targeting the latest versions of Android; compatibility " +
                            "modes apply. Consider testing and updating this version. " +
                           "Consult the android.os.Build.VERSION_CODES javadoc for details.";
                    report(context, valueCookie, TARGET_NEWER, message);
                }
                if (version > 0) {
                    mTargetSdkVersion = version;
                    checkTargetCompatibility(context, valueCookie);
                }
            }

            if (value.startsWith("0")) {
                checkOctal(context, value, valueCookie);
            }

            if (property.equals("versionName") || property.equals("versionCode") &&
                    !isInteger(value) || !isStringLiteral(value)) {
                // Method call -- make sure it does not match one of the getters in the
                // configuration!
                if ((value.equals("getVersionCode") ||
                        value.equals("getVersionName"))) {
                    String message = "Bad method name: pick a unique method name which does not "
                            + "conflict with the implicit getters for the defaultConfig "
                            + "properties. For example, try using the prefix compute- "
                            + "instead of get-.";
                    report(context, valueCookie, GRADLE_GETTER, message);
                }
            } else if (property.equals("packageName")) {
                if (isModelOlderThan011(context)) {
                    return;
                }
                String message = "Deprecated: Replace 'packageName' with 'applicationId'";
                report(context, getPropertyKeyCookie(valueCookie), IDE_SUPPORT, message);
            }
        } else if (property.equals("compileSdkVersion") && parent.equals("android")) {
            int version = getIntLiteralValue(value, -1);
            if (version > 0) {
                mCompileSdkVersion = version;
                checkTargetCompatibility(context, valueCookie);
            }
        } else if (property.equals("minSdkVersion") && parent.equals("android")) {
            int version = getIntLiteralValue(value, -1);
            if (version > 0) {
                mMinSdkVersion = version;
            }
        } else if (property.equals("buildToolsVersion") && parent.equals("android")) {
            String versionString = getStringLiteralValue(value);
            if (versionString != null) {
                FullRevision version = parseRevisionSilently(versionString);
                if (version != null) {
                    FullRevision recommended = getLatestBuildTools(context.getClient(),
                            version.getMajor());
                    if (recommended != null && version.compareTo(recommended) < 0) {
                        String message = "Old buildToolsVersion; recommended version "
                                + "is " + recommended + " or later";
                        report(context, valueCookie, DEPENDENCY, message);
                    }
                }
            }
        } else if (parent.equals("dependencies")) {
            if (value.startsWith("files('") && value.endsWith("')")) {
                String path = value.substring("files('".length(), value.length() - 2);
                if (path.contains("\\\\")) {
                    String message = "Do not use Windows file separators in .gradle files; "
                            + "use / instead";
                    report(context, valueCookie, PATH, message);

                } else if (new File(path.replace('/', File.separatorChar)).isAbsolute()) {
                    String message = "Avoid using absolute paths in .gradle files";
                    report(context, valueCookie, PATH, message);
                }
            } else {
                String dependency = getStringLiteralValue(value);
                if (dependency != null) {
                    GradleCoordinate gc = GradleCoordinate.parseCoordinateString(dependency);
                    if (gc != null) {
                        if (gc.acceptsGreaterRevisions()) {
                            String message = "Avoid using + in version numbers; can lead " + "to unpredictable and  unrepeatable builds";
                            report(context, valueCookie, PLUS, message);
                        }
                        if (!dependency.startsWith(SdkConstants.GRADLE_PLUGIN_NAME) ||
                            !checkGradlePluginDependency(context, gc, valueCookie)) {
                            checkDependency(context, gc, valueCookie);
                        }
                    }
                }
            }
            if ((!property.equals("classpath")) && "buildscript".equals(parentParent)) {
                String message = "Only `classpath` dependencies should appear in the `buildscript` dependencies block";
                report(context, statementCookie, IMPROPER_PROJECT_LEVEL_STATEMENT, message);
            }
        } else if (property.equals("packageNameSuffix")) {
            if (isModelOlderThan011(context)) {
                return;
            }
            String message = "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'";
            report(context, getPropertyKeyCookie(valueCookie), IDE_SUPPORT, message);
        } else if (property.equals("applicationIdSuffix")) {
            String suffix = getStringLiteralValue(value);
            if (suffix != null && !suffix.startsWith(".")) {
                String message = "Package suffix should probably start with a \".\"";
                report(context, valueCookie, PATH, message);
            }
        }
    }

    protected void checkBlock(
            @NonNull Context context,
            @NonNull String block,
            @Nullable String parent,
            @NonNull Object cookie) {
        if ("android".equals(block) && parent == null) {
            myAndroidBlockCookie = cookie;
        } else if ("dependencies".equals(block)) {
            if (parent == null) {
                myDependenciesCookie = cookie;
            } else if (!parent.equals("buildscript") && !parent.equals("allprojects")) {
                String message = "A `dependencies` block doesn't belong here.";
                report(context, cookie, MISPLACED_STATEMENT, message);
            }
        } else if ("repositories".equals(block)) {
            if (parent == null) {
                myRepositoriesCookie = cookie;
            } else if (!parent.equals("buildscript") && !parent.equals("allprojects")) {
                String message = "A `repositories` block doesn't belong here.";
                report(context, cookie, MISPLACED_STATEMENT, message);
            }
        }
    }

    protected void checkMethodCall(
            @NonNull Context context,
            @NonNull String statement,
            @Nullable String parent,
            @NonNull Map<String, String> namedArguments,
            @NonNull List<String> unnamedArguments,
            @NonNull Object cookie) {
        String plugin = namedArguments.get("plugin");
        if (statement.equals("apply") && parent == null && "android".equals(plugin) || "android-library".equals(plugin)) {
           myAndroidPluginCookie = cookie;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (myAndroidPluginCookie != null && !isAndroidProject()) {
            String message = "The `apply plugin` statement should only be used if there is a corresponding module for this build file.";
            report(context, myAndroidPluginCookie, IMPROPER_PROJECT_LEVEL_STATEMENT, message);
        }
        if (myAndroidBlockCookie != null && !isAndroidProject()) {
            String message = "An `android` block should only appear in build files that correspond to a module and have an " +
                             "`apply plugin: 'android'` or `apply plugin: 'android-library'` statement.";
            report(context, myAndroidBlockCookie, IMPROPER_PROJECT_LEVEL_STATEMENT, message);
        }
        if (myDependenciesCookie != null && !isAndroidProject()) {
            String message = "A top-level `dependencies` block should only appear in build files that correspond to a module.";
            report(context, myDependenciesCookie, IMPROPER_PROJECT_LEVEL_STATEMENT, message);
            super.afterCheckFile(context);
        }
        if (myRepositoriesCookie != null && !isAndroidProject()) {
            String message = "A top-level `repositories` block should only appear in build files that correspond to a module.";
            report(context, myRepositoriesCookie, IMPROPER_PROJECT_LEVEL_STATEMENT, message);
            super.afterCheckFile(context);
        }
    }

    private boolean isAndroidProject() {
        return myAndroidBlockCookie != null && myAndroidPluginCookie  != null;
    }

    @Nullable
    private static FullRevision parseRevisionSilently(String versionString) {
        try {
            return FullRevision.parseRevision(versionString);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isModelOlderThan011(@NonNull Context context) {
        return LintUtils.isModelOlderThan(context.getProject().getGradleProjectModel(), 0, 11, 0);
    }

    private static int sMajorBuildTools;
    private static FullRevision sLatestBuildTools;

    /** Returns the latest build tools installed for the given major version.
     * We just cache this once; we don't need to be accurate in the sense that if the
     * user opens the SDK manager and installs a more recent version, we capture this in
     * the same IDE session.
     *
     * @param client the associated client
     * @param major the major version of build tools to look up (e.g. typically 18, 19, ...)
     * @return the corresponding highest known revision
     */
    @Nullable
    private static FullRevision getLatestBuildTools(@NonNull LintClient client, int major) {
        if (major != sMajorBuildTools) {
            sMajorBuildTools = major;

            List<FullRevision> revisions = Lists.newArrayList();
            if (major == 19) {
                revisions.add(new FullRevision(19, 1, 0));
            } else if (major == 18) {
                revisions.add(new FullRevision(18, 1, 1));
            }
            // The above versions can go stale.
            // Check if a more recent one is installed. (The above are still useful for
            // people who haven't updated with the SDK manager recently.)
            File sdkHome = client.getSdkHome();
            if (sdkHome != null) {
                File[] dirs = new File(sdkHome, FD_BUILD_TOOLS).listFiles();
                if (dirs != null) {
                    for (File dir : dirs) {
                        String name = dir.getName();
                        if (!dir.isDirectory() || !Character.isDigit(name.charAt(0))) {
                            continue;
                        }
                        FullRevision v = parseRevisionSilently(name);
                        if (v != null && v.getMajor() == major) {
                            revisions.add(v);
                        }
                    }
                }
            }

            if (!revisions.isEmpty()) {
                sLatestBuildTools = Collections.max(revisions);
            }
        }

        return sLatestBuildTools;
    }

    private void checkTargetCompatibility(Context context, Object cookie) {
        if (mCompileSdkVersion > 0 && mTargetSdkVersion > 0
                && mTargetSdkVersion > mCompileSdkVersion) {
            String message = "The targetSdkVersion should not be higher than the compileSdkVersion";
            report(context, cookie, DEPENDENCY, message);
        }
    }

    @Nullable
    private static String getStringLiteralValue(@NonNull String value) {
        if (value.length() > 2 && (value.startsWith("'") && value.endsWith("'") ||
                value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }

        return null;
    }

    private static int getIntLiteralValue(@NonNull String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isInteger(String token) {
        return token.matches("\\d+");
    }

    private static boolean isStringLiteral(String token) {
        return token.startsWith("\"") && token.endsWith("\"") ||
                token.startsWith("'") && token.endsWith("'");
    }

    private void checkDependency(
            @NonNull Context context,
            @NonNull GradleCoordinate dependency,
            @NonNull Object cookie) {
        if ("com.android.support".equals(dependency.getGroupId()) &&
                ("support-v4".equals(dependency.getArtifactId()) ||
                        "appcompat-v7".equals(dependency.getArtifactId()))) {
            checkSupportLibraries(context, dependency, cookie);
            if (mMinSdkVersion >= 14 && "appcompat-v7".equals(dependency.getArtifactId())) {
                report(context, cookie, DEPENDENCY,
                        "Using the appcompat library when minSdkVersion >= 14 is not necessary");
            }
            return;
        } else if ("com.google.android.gms".equals(dependency.getGroupId()) &&
                "play-services".equals(dependency.getArtifactId())) {
            checkPlayServices(context, dependency, cookie);
            return;
        }

        FullRevision version = null;
        Issue issue = DEPENDENCY;
        if ("com.android.tools.build".equals(dependency.getGroupId()) &&
                "gradle".equals(dependency.getArtifactId())) {
            version = getNewerRevision(dependency, 0, 11, 0);
        } else if ("com.google.guava".equals(dependency.getGroupId()) &&
                "guava".equals(dependency.getArtifactId())) {
            version = getNewerRevision(dependency, 17, 0, 0);
        } else if ("com.google.code.gson".equals(dependency.getGroupId()) &&
                "gson".equals(dependency.getArtifactId())) {
            version = getNewerRevision(dependency, 2, 2, 4);
        } else if ("org.apache.httpcomponents".equals(dependency.getGroupId()) &&
                "httpclient".equals(dependency.getArtifactId())) {
            version = getNewerRevision(dependency, 4, 3, 3);
        }

        // Network check for really up to date libraries? Only done in batch mode
        if (context.getScope().size() > 1 && context.isEnabled(REMOTE_VERSION)) {
            FullRevision latest = getLatestVersion(context, dependency);
            if (latest != null && isOlderThan(dependency, latest.getMajor(), latest.getMinor(),
                    latest.getMicro())) {
                version = latest;
                issue = REMOTE_VERSION;
            }
        }

        if (version != null) {
            String message = "A newer version of " + dependency.getGroupId() + ":" +
                    dependency.getArtifactId() + " than " + dependency.getFullRevision() +
                    " is available: " + version.toShortString();
            report(context, cookie, issue, message);
        }
    }

    /** TODO: Cache these results somewhere! */
    private static FullRevision getLatestVersion(@NonNull Context context,
            @NonNull GradleCoordinate dependency) {
        StringBuilder query = new StringBuilder();
        String encoding = UTF_8.name();
        try {
            query.append("http://search.maven.org/solrsearch/select?q=g:%22");
            query.append(URLEncoder.encode(dependency.getGroupId(), encoding));
            query.append("%22+AND+a:%22");
            query.append(URLEncoder.encode(dependency.getArtifactId(), encoding));
        } catch (UnsupportedEncodingException ee) {
            return null;
        }
        query.append("%22&core=gav&rows=1&wt=json");

        String response = readUrlData(context, dependency, query.toString());
        if (response == null) {
            return null;
        }

        // Sample response:
        //    {
        //        "responseHeader": {
        //            "status": 0,
        //            "QTime": 0,
        //            "params": {
        //                "fl": "id,g,a,v,p,ec,timestamp,tags",
        //                "sort": "score desc,timestamp desc,g asc,a asc,v desc",
        //                "indent": "off",
        //                "q": "g:\"com.google.guava\" AND a:\"guava\"",
        //                "core": "gav",
        //                "wt": "json",
        //                "rows": "1",
        //                "version": "2.2"
        //            }
        //        },
        //        "response": {
        //            "numFound": 37,
        //            "start": 0,
        //            "docs": [{
        //                "id": "com.google.guava:guava:17.0",
        //                "g": "com.google.guava",
        //                "a": "guava",
        //                "v": "17.0",
        //                "p": "bundle",
        //                "timestamp": 1398199666000,
        //                "tags": ["spec", "libraries", "classes", "google", "code"],
        //                "ec": ["-javadoc.jar", "-sources.jar", ".jar", "-site.jar", ".pom"]
        //            }]
        //        }
        //    }

        // Look for version info:  This is just a cheap skim of the above JSON results
        int index = response.indexOf("\"response\"");   //$NON-NLS-1$
        if (index != -1) {
            index = response.indexOf("\"v\":", index);  //$NON-NLS-1$
            if (index != -1) {
                index += 4;
                int start = response.indexOf('"', index) + 1;
                int end = response.indexOf('"', start + 1);
                if (end > start && start >= 0) {
                    return parseRevisionSilently(response.substring(start, end));
                }
            }
        }

        return null;
    }

    @Nullable
    private static String readUrlData(
            @NonNull Context context,
            @NonNull GradleCoordinate dependency,
            @NonNull String query) {
        LintClient client = context.getClient();
        try {
            URL url = new URL(query);

            URLConnection connection = client.openConnection(url);
            if (connection == null) {
                return null;
            }
            try {
                InputStream is = connection.getInputStream();
                if (is == null) {
                    return null;
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8));
                try {
                    StringBuilder sb = new StringBuilder(500);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                        sb.append('\n');
                    }

                    return sb.toString();
                } finally {
                    reader.close();
                }
            } finally {
                client.closeConnection(connection);
            }
        } catch (IOException ioe) {
            client.log(ioe, "Could not connect to maven central to look up the " + "latest available version for %1$s", dependency);
            return null;
        }
    }

    private boolean checkGradlePluginDependency(Context context, GradleCoordinate dependency,
            Object cookie) {
        GradleCoordinate latestPlugin = GradleCoordinate.parseCoordinateString(SdkConstants.GRADLE_PLUGIN_NAME +
                SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION);
        if (GradleCoordinate.COMPARE_PLUS_HIGHER.compare(dependency, latestPlugin) < 0) {
            String message = "You must use a newer version of the Android Gradle plugin. The "
                    + "minimum supported version is " + SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION +
                    " and the recommended version is " + SdkConstants.GRADLE_PLUGIN_LATEST_VERSION;
            report(context, cookie, GRADLE_PLUGIN_COMPATIBILITY, message);
            return true;
        }
        return false;
    }

    private void checkSupportLibraries(Context context, GradleCoordinate dependency,
            Object cookie) {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        assert groupId != null && artifactId != null;

        // See if the support library version is lower than the targetSdkVersion
        if (mTargetSdkVersion > 0 && dependency.getMajorVersion() < mTargetSdkVersion &&
                dependency.getMajorVersion() != GradleCoordinate.PLUS_REV_VALUE &&
                context.isEnabled(COMPATIBILITY)) {
            String message = "This support library should not use a lower version ("
                + dependency.getMajorVersion() + ") than the targetSdkVersion ("
                    + mTargetSdkVersion + ")";
            report(context, cookie, COMPATIBILITY, message);
        }

        // Check to make sure you have the Android support repository installed
        File repository = findRepository(context.getClient(), "android");
        if (repository == null) {
            report(context, cookie, DEPENDENCY,
                    "Dependency on a support library, but the SDK installation does not "
                            + "have the \"Extras > Android Support Repository\" installed. "
                            + "Open the SDK manager and install it.");
        } else {
            checkLocalMavenVersions(context, dependency, cookie, groupId, artifactId,
                    repository);
        }
    }

    private void checkPlayServices(Context context, GradleCoordinate dependency, Object cookie) {
        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        assert groupId != null && artifactId != null;

        File repository = findRepository(context.getClient(), "google");
        if (repository == null) {
            report(context, cookie, DEPENDENCY,
                    "Dependency on Play Services, but the SDK installation does not "
                            + "have the \"Extras > Google Repository\" installed. "
                            + "Open the SDK manager and install it.");
        } else {
            checkLocalMavenVersions(context, dependency, cookie, groupId, artifactId,
                    repository);
        }
    }

    private void checkLocalMavenVersions(Context context, GradleCoordinate dependency,
            Object cookie, String groupId, String artifactId, File repository) {
        GradleCoordinate max = getHighestInstalledVersion(groupId, artifactId, repository);
        if (max != null) {
            if (COMPARE_PLUS_HIGHER.compare(dependency, max) < 0
                    && context.isEnabled(DEPENDENCY)) {
                String message = "A newer version of " + groupId
                        + ":" + artifactId + " than " +
                        dependency.getFullRevision() + " is available: " +
                        max.getFullRevision();
                report(context, cookie, DEPENDENCY, message);
            }
        }
    }

    private static File findRepository(LintClient client, String extrasName) {
        File sdkHome = client.getSdkHome();
        if (sdkHome != null) {
            File repository = new File(sdkHome, FD_EXTRAS + separator + extrasName + separator
                    + FD_M2_REPOSITORY);
            if (repository.exists()) {
                return repository;
            }
        }

        return null;
    }

    @Nullable
    private static GradleCoordinate getHighestInstalledVersion(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull File repository) {
        File versionDir = new File(repository,
                groupId.replace('.', separatorChar) + separator + artifactId);
        File[] versions = versionDir.listFiles();
        if (versions != null) {
            List<GradleCoordinate> versionCoordinates = Lists.newArrayList();
            for (File dir : versions) {
                if (!dir.isDirectory()) {
                    continue;
                }
                GradleCoordinate gc = GradleCoordinate.parseCoordinateString(
                        groupId + ":" + artifactId + ":" + dir.getName());
                if (gc != null) {
                    versionCoordinates.add(gc);
                }
            }
            if (!versionCoordinates.isEmpty()) {
                return Collections.max(versionCoordinates, COMPARE_PLUS_HIGHER);
            }
        }

        return null;
    }

    private static FullRevision getNewerRevision(@NonNull GradleCoordinate dependency,
            int major, int minor, int micro) {
        assert dependency.getGroupId() != null;
        assert dependency.getArtifactId() != null;
        if (COMPARE_PLUS_HIGHER.compare(dependency,
                new GradleCoordinate(dependency.getGroupId(),
                        dependency.getArtifactId(), major, minor, micro)) < 0) {
            return new FullRevision(major, minor, micro);
        } else {
            return null;
        }
    }

    private static boolean isOlderThan(@NonNull GradleCoordinate dependency, int major, int minor,
            int micro) {
        assert dependency.getGroupId() != null;
        assert dependency.getArtifactId() != null;
        return COMPARE_PLUS_HIGHER.compare(dependency,
                new GradleCoordinate(dependency.getGroupId(),
                        dependency.getArtifactId(), major, minor, micro)) < 0;
    }

    private void report(@NonNull Context context, @NonNull Object cookie, @NonNull Issue issue,
            @NonNull String message) {
        if (context.isEnabled(issue)) {
            // Suppressed?
            // Temporarily unconditionally checking for suppress comments in Gradle files
            // since Studio insists on an AndroidLint id prefix
            boolean checkComments = /*context.getClient().checkForSuppressComments()
                    &&*/ context.containsCommentSuppress();
            if (checkComments) {
                int startOffset = getStartOffset(context, cookie);
                if (startOffset >= 0 && context.isSuppressedWithComment(startOffset, issue)) {
                    return;
                }
            }

            context.report(issue, createLocation(context, cookie), message, null);
        }
    }

    @SuppressWarnings("MethodMayBeStatic")
    @NonNull
    protected Object getPropertyKeyCookie(@NonNull Object cookie) {
        return cookie;
    }

    @SuppressWarnings("MethodMayBeStatic")
    @NonNull
    protected Object getPropertyPairCookie(@NonNull Object cookie) {
      return cookie;
    }

    @SuppressWarnings("MethodMayBeStatic")
    protected int getStartOffset(@NonNull Context context, @NonNull Object cookie) {
        return -1;
    }

    @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
    protected Location createLocation(@NonNull Context context, @NonNull Object cookie) {
        return null;
    }
}