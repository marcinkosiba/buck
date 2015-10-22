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

package com.facebook.buck.jvm.java.intellij;

import static org.junit.Assert.assertThat;

import com.facebook.buck.android.AndroidBinaryBuilder;
import com.facebook.buck.android.AndroidLibraryBuilder;
import com.facebook.buck.android.AndroidLibraryDescription;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.TargetNode;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSortedSet;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IjAndroidManifestDeterminatorTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void whenNoBinaryIsPresentAbsentHolder() {
    TargetNode<AndroidLibraryDescription.Arg> libraryTargetNode =
        AndroidLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:lib"))
            .build();

    IjAndroidManifestDeterminator manifestDeterminator = new IjAndroidManifestDeterminator(
        TargetGraphFactory.newInstance(libraryTargetNode));

    assertThat(
        manifestDeterminator.getManifestHolder(libraryTargetNode),
        Matchers.equalTo(Optional.<TargetNode<?>>absent()));

  }

  @Test
  public void whenBinaryPresent() {
    TargetNode<AndroidLibraryDescription.Arg> libraryTargetNode =
        AndroidLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:lib"))
            .build();

    BuildTarget binaryBuildTarget = BuildTargetFactory.newInstance("//:binary");
    TargetNode<?> binaryTargetNode = AndroidBinaryBuilder.createBuilder(binaryBuildTarget)
        .setOriginalDeps(ImmutableSortedSet.of(libraryTargetNode.getBuildTarget()))
        .build();

    IjAndroidManifestDeterminator manifestDeterminator = new IjAndroidManifestDeterminator(
        TargetGraphFactory.newInstance(libraryTargetNode, binaryTargetNode));
    assertThat(
        manifestDeterminator.getManifestHolder(libraryTargetNode),
        Matchers.equalTo(Optional.<TargetNode<?>>of(binaryTargetNode)));
  }

  @Test
  public void whenBinaryDoesntDependOnLibDefaultManifestIsUsed() {
    TargetNode<AndroidLibraryDescription.Arg> binaryRelatedLibraryTargetNode =
        AndroidLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:lib-for-binary"))
            .build();
    TargetNode<AndroidLibraryDescription.Arg> standaloneLibraryTargetNode =
        AndroidLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:lib-standalone"))
            .build();

    BuildTarget binaryBuildTarget = BuildTargetFactory.newInstance("//:binary");
    TargetNode<?> binaryTargetNode = AndroidBinaryBuilder.createBuilder(binaryBuildTarget)
        .setOriginalDeps(ImmutableSortedSet.of(binaryRelatedLibraryTargetNode.getBuildTarget()))
        .build();

    IjAndroidManifestDeterminator manifestDeterminator = new IjAndroidManifestDeterminator(
        TargetGraphFactory.newInstance(
            binaryRelatedLibraryTargetNode,
            standaloneLibraryTargetNode,
            binaryTargetNode));
    assertThat(
        manifestDeterminator.getManifestHolder(standaloneLibraryTargetNode),
        Matchers.equalTo(Optional.<TargetNode<?>>absent()));
    assertThat(
        manifestDeterminator.getManifestHolder(binaryRelatedLibraryTargetNode),
        Matchers.equalTo(Optional.<TargetNode<?>>of(binaryTargetNode)));
  }
}
