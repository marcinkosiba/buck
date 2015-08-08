/*
 * Copyright 2015-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.java.intellij;

import static org.junit.Assert.assertThat;

import com.facebook.buck.java.DefaultJavaPackageFinder;
import com.facebook.buck.java.JavaPackageFinder;
import com.facebook.buck.model.BuildTarget;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class IjSourceRootSimplifierTest {

  private static IjFolder buildFolder(String path, AbstractIjFolder.Type type) {
    return IjFolder.builder()
        .setPath(Paths.get(path))
        .setType(type)
        .setWantsPackagePrefix(true)
        .setInputs(ImmutableSortedSet.<Path>of())
        .build();
  }

  private static IjFolder buildExcludeFolder(String path) {
    return IjFolder.builder()
        .setPath(Paths.get(path))
        .setType(AbstractIjFolder.Type.EXCLUDE_FOLDER)
        .setWantsPackagePrefix(false)
        .setInputs(ImmutableSortedSet.<Path>of())
        .build();
  }

  private static IjFolder buildSourceFolder(String path) {
    return buildFolder(path, AbstractIjFolder.Type.SOURCE_FOLDER);
  }

  private static IjFolder buildNoPrefixSourceFolder(String path) {
    return IjFolder.builder()
        .setPath(Paths.get(path))
        .setType(AbstractIjFolder.Type.SOURCE_FOLDER)
        .setWantsPackagePrefix(false)
        .setInputs(ImmutableSortedSet.<Path>of())
        .build();
  }

  private static IjFolder buildTestFolder(String path) {
    return buildFolder(path, AbstractIjFolder.Type.TEST_FOLDER);
  }

  private static JavaPackageFinder fakePackageFinder() {
    return fakePackageFinder(ImmutableMap.<Path, Path>of());
  }

  private static JavaPackageFinder fakePackageFinder(final ImmutableMap<Path, Path> packageMap) {
    return new JavaPackageFinder() {
      @Override
      public Path findJavaPackageFolder(Path pathRelativeToProjectRoot) {
        // The Path given here is a path to a file, not a folder.
        pathRelativeToProjectRoot =
            Preconditions.checkNotNull(pathRelativeToProjectRoot.getParent());
        if (packageMap.containsKey(pathRelativeToProjectRoot)) {
          return packageMap.get(pathRelativeToProjectRoot);
        }
        return pathRelativeToProjectRoot;
      }

      @Override
      public String findJavaPackage(Path pathRelativeToProjectRoot) {
        return DefaultJavaPackageFinder.findJavaPackageWithPackageFolder(
            findJavaPackageFolder(pathRelativeToProjectRoot));
      }

      @Override
      public String findJavaPackage(BuildTarget buildTarget) {
        return findJavaPackage(buildTarget.getBasePath().resolve("removed"));
      }
    };
  }

  @Test
  public void testSameTypeAndPackageAreMerged() {
    IjFolder left = buildSourceFolder("src/left");
    IjFolder right = buildSourceFolder("src/right");

    ImmutableSet<IjFolder> mergedFolders = IjSourceRootSimplifier.simplify(
        ImmutableSet.of(left, right),
        fakePackageFinder());

    assertThat(
        mergedFolders,
        Matchers.contains(buildSourceFolder("src")));
  }

  @Test
  public void testSinglePathElement() {
    IjFolder src = buildSourceFolder("src");

    ImmutableSet<IjFolder> mergedFolders = IjSourceRootSimplifier.simplify(
        ImmutableSet.of(src),
        fakePackageFinder());

    assertThat(
        mergedFolders,
        Matchers.contains(src));
  }

  @Test
  public void testComplexPathElement() {
    IjFolder src = buildSourceFolder("src/a/b/c/d");

    ImmutableSet<IjFolder> mergedFolders = IjSourceRootSimplifier.simplify(
        ImmutableSet.of(src),
        fakePackageFinder());

    assertThat(
        mergedFolders,
        Matchers.contains(buildSourceFolder("src")));
  }

  @Test
  public void testDifferentTypeAreNotMerged() {
    IjFolder leftSource = buildSourceFolder("src/left");
    IjFolder rightTest = buildTestFolder("src/right");

    ImmutableSet<IjFolder> mergedFolders = IjSourceRootSimplifier.simplify(
        ImmutableSet.of(leftSource, rightTest),
        fakePackageFinder());

    assertThat(
        mergedFolders,
        Matchers.containsInAnyOrder(leftSource, rightTest));
  }

  @Test
  public void testDifferentTypeAreNotMergedWhileSameOnesAre() {
    IjFolder aaaSource = buildSourceFolder("a/a/a");
    IjFolder aabSource = buildSourceFolder("a/a/b");
    IjFolder abSource = buildSourceFolder("a/b");
    IjFolder acTest = buildTestFolder("a/c");
    IjFolder adaTest = buildTestFolder("a/d/a");

    ImmutableSet<IjFolder> mergedFolders = IjSourceRootSimplifier.simplify(
        ImmutableSet.of(aaaSource, aabSource, abSource, acTest, adaTest),
        fakePackageFinder());

    IjFolder aaSource = buildSourceFolder("a/a");
    IjFolder adTest = buildTestFolder("a/d");
    assertThat(
        mergedFolders,
        Matchers.containsInAnyOrder(aaSource, abSource, acTest, adTest));
  }

  @Test
  public void testDifferentPackageHierarchiesAreNotMerged() {
    IjFolder leftSource = buildSourceFolder("src/left");
    IjFolder rightSource = buildTestFolder("src/right");

    ImmutableSet<IjFolder> mergedFolders = IjSourceRootSimplifier.simplify(
        ImmutableSet.of(leftSource, rightSource),
        fakePackageFinder(
            ImmutableMap.of(
                Paths.get("src/left"), Paths.get("onething"),
                Paths.get("src/right"), Paths.get("another"))));

    assertThat(
        mergedFolders,
        Matchers.containsInAnyOrder(leftSource, rightSource));
  }

  @Test
  public void testShortPackagesAreNotMerged() {
    IjFolder aSource = buildSourceFolder("x/a/a");
    IjFolder bSource = buildSourceFolder("x/a/b");

    ImmutableSet<IjFolder> mergedFolders = IjSourceRootSimplifier.simplify(
        ImmutableSet.of(aSource, bSource),
        fakePackageFinder(
            ImmutableMap.of(
                Paths.get("x/a/a"), Paths.get("a/a"),
                Paths.get("x/a/b"), Paths.get("a/b"))));

    assertThat(
        mergedFolders,
        Matchers.contains(buildSourceFolder("x/a")));
  }

  @Test
  public void testExcludeFoldersAreIgnored() {
    // While flattening source folder hierarchies is fine within certain bounds given the
    // information available in the set of IjFolders and their package information, it is not
    // possible to do anything with exclude folders at this level of abstraction.
    // That's fine though as the IjTemplateDataPreparer generates excludes at the highest possible
    // location in the file tree, so they don't need to be merged.

    IjFolder leftSource = buildSourceFolder("src/left");
    IjFolder aExclude = buildExcludeFolder("src/a");
    IjFolder aaExclude = buildExcludeFolder("src/a/a");

    ImmutableSet<IjFolder> mergedFolders = IjSourceRootSimplifier.simplify(
        ImmutableSet.of(leftSource, aExclude, aaExclude),
        fakePackageFinder());

    assertThat(
        mergedFolders,
        Matchers.containsInAnyOrder(buildSourceFolder("src"), aExclude, aaExclude));
  }

  @Test
  public void textPrefixlessSourcesAreMergedToHighestRoot() {
    IjFolder aFolder = buildNoPrefixSourceFolder("src/a/b");
    IjFolder aaFolder = buildNoPrefixSourceFolder("src/a/a");
    IjFolder bFolder = buildNoPrefixSourceFolder("src/b");

    ImmutableSet<IjFolder> mergedFolders = IjSourceRootSimplifier.simplify(
        ImmutableSet.of(aFolder, aaFolder, bFolder),
        fakePackageFinder());

    assertThat(
        mergedFolders,
        Matchers.contains(buildNoPrefixSourceFolder("src")));
  }

  @Test
  public void textPrefixAndPrefixlessSourcesDontMerge() {
    IjFolder aFolder = buildNoPrefixSourceFolder("src/a/b");
    IjFolder aaFolder = buildSourceFolder("src/a/a");
    IjFolder bFolder = buildNoPrefixSourceFolder("src/b");

    ImmutableSet<IjFolder> mergedFolders = IjSourceRootSimplifier.simplify(
        ImmutableSet.of(aFolder, aaFolder, bFolder),
        fakePackageFinder());

    assertThat(
        mergedFolders,
        Matchers.containsInAnyOrder(aFolder, aaFolder, bFolder));
  }
}
