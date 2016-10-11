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

package com.facebook.buck.event.listener;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.event.LeafEvent;
import com.facebook.buck.event.TestEventConfigerator;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleEvent;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.FakeBuildRule;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.step.StepEvent;
import com.facebook.buck.util.Ansi;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

import org.junit.Test;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class BuildThreadStateRendererTest {

  private static final Ansi ANSI = Ansi.withoutTty();
  private static final Function<Long, String> FORMAT_TIME_FUNCTION =
      timeMs -> String.format(Locale.US, "%.1fs", timeMs / 1000.0);
  private static final SourcePathResolver PATH_RESOLVER =
      new SourcePathResolver(
          new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer()));
  private static final BuildTarget TARGET1 = BuildTargetFactory.newInstance("//:target1");
  private static final BuildTarget TARGET2 = BuildTargetFactory.newInstance("//:target2");
  private static final BuildTarget TARGET3 = BuildTargetFactory.newInstance("//:target3");
  private static final BuildTarget TARGET4 = BuildTargetFactory.newInstance("//:target4");
  private static final BuildRule RULE1 = createFakeRule(TARGET1);
  private static final BuildRule RULE2 = createFakeRule(TARGET2);
  private static final BuildRule RULE3 = createFakeRule(TARGET3);
  private static final BuildRule RULE4 = createFakeRule(TARGET4);

  @Test
  public void emptyInput() {
    BuildThreadStateRenderer renderer = createRenderer(
        2100,
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableMap.of());
    assertThat(
        renderLines(renderer, true),
        is(equalTo(ImmutableList.<String>of())));
    assertThat(
        renderLines(renderer, false),
        is(equalTo(ImmutableList.<String>of())));
    assertThat(
        renderShortStatus(renderer, true),
        is(equalTo(ImmutableList.<String>of())));
    assertThat(
        renderShortStatus(renderer, false),
        is(equalTo(ImmutableList.<String>of())));
  }

  @Test
  public void commonCase() {
    BuildThreadStateRenderer renderer = createRenderer(
        4200,
        ImmutableMap.of(
            1L, createRuleStartedEventOptional(1, 1200, RULE2),
            3L, createRuleStartedEventOptional(3, 2300, RULE3),
            4L, createRuleStartedEventOptional(4, 1100, RULE1),
            5L, Optional.absent(),
            8L, createRuleStartedEventOptional(6, 3000, RULE4)),
        ImmutableMap.of(
            1L, createStepStartedEventOptional(1, 1500, "step A"),
            3L, Optional.absent(),
            4L, Optional.absent(),
            5L, Optional.absent(),
            8L, createStepStartedEventOptional(1, 3700, "step B")),
        ImmutableMap.of(
            TARGET1, new AtomicLong(200),
            TARGET2, new AtomicLong(1400),
            TARGET3, new AtomicLong(700),
            TARGET4, new AtomicLong(0)));
    assertThat(
        renderLines(renderer, true),
        is(equalTo(
            ImmutableList.of(
                " |=> //:target2...  4.4s (running step A[2.7s])",
                " |=> //:target1...  3.3s (checking local cache)",
                " |=> //:target3...  2.6s (checking local cache)",
                " |=> //:target4...  1.2s (running step B[0.5s])",
                " |=> IDLE"))));
    assertThat(
        renderLines(renderer, false),
        is(equalTo(
            ImmutableList.of(
                " |=> //:target2...  4.4s (running step A[2.7s])",
                " |=> //:target3...  2.6s (checking local cache)",
                " |=> //:target1...  3.3s (checking local cache)",
                " |=> IDLE",
                " |=> //:target4...  1.2s (running step B[0.5s])"))));
    assertThat(
        renderShortStatus(renderer, true),
        is(equalTo(ImmutableList.of("[:]", "[:]", "[:]", "[:]", "[ ]"))));
    assertThat(
        renderShortStatus(renderer, false),
        is(equalTo(ImmutableList.of("[:]", "[:]", "[:]", "[ ]", "[:]"))));
  }

  @Test
  public void withMissingInformation() {
    // SuperConsoleEventBusListener stores the data it passes to the renderer in a map that might
    // be concurrently modified from other threads. It is important that the renderer can handle
    // data containing inconsistencies.
    BuildThreadStateRenderer renderer = createRenderer(
        4200,
        ImmutableMap.of(
            3L, createRuleStartedEventOptional(3, 2300, RULE3),
            5L, Optional.absent(),
            8L, createRuleStartedEventOptional(6, 3000, RULE4)),
        ImmutableMap.of(
            1L, createStepStartedEventOptional(1, 1500, "step A"),
            4L, Optional.absent(),
            5L, Optional.absent(),
            8L, createStepStartedEventOptional(1, 3700, "step B")),
        ImmutableMap.of(
            TARGET1, new AtomicLong(200),
            TARGET2, new AtomicLong(1400),
            TARGET3, new AtomicLong(700)));
    assertThat(
        renderLines(renderer, true),
        is(equalTo(
            ImmutableList.of(
                // two missing build rules - no output
                " |=> //:target3...  2.6s (checking local cache)", // missing step information
                " |=> IDLE",
                " |=> IDLE")))); // missing accumulated time - show as IDLE
    assertThat(
        renderShortStatus(renderer, true),
        is(equalTo(
            ImmutableList.of("[:]", "[ ]", "[ ]"))));
  }

  private static BuildRule createFakeRule(BuildTarget target) {
    return new FakeBuildRule(target, PATH_RESOLVER, ImmutableSortedSet.of());
  }

  private static Optional<? extends BuildRuleEvent> createRuleStartedEventOptional(
      long threadId,
      long timeMs,
      BuildRule rule) {
    return Optional.of(
        TestEventConfigerator.configureTestEventAtTime(
            BuildRuleEvent.started(rule),
            timeMs,
            TimeUnit.MILLISECONDS,
            threadId));
  }

  private static Optional<? extends LeafEvent> createStepStartedEventOptional(
      long threadId,
      long timeMs,
      String name) {
    return Optional.of(
        TestEventConfigerator.configureTestEventAtTime(
            StepEvent.started(name, name + " description", UUID.randomUUID()),
            timeMs,
            TimeUnit.MILLISECONDS,
            threadId));
  }

  private BuildThreadStateRenderer createRenderer(
      long timeMs,
      Map<Long, Optional<? extends BuildRuleEvent>> buildEvents,
      Map<Long, Optional<? extends LeafEvent>> runningSteps,
      Map<BuildTarget, AtomicLong> accumulatedTimes) {
    return new BuildThreadStateRenderer(
        ANSI,
        FORMAT_TIME_FUNCTION,
        timeMs,
        runningSteps,
        new AccumulatedTimeTracker(
            buildEvents,
            ImmutableMap.of(),
            accumulatedTimes));
  }

  private ImmutableList<String> renderLines(BuildThreadStateRenderer renderer, boolean sortByTime) {
    ImmutableList.Builder<String> lines = ImmutableList.builder();
    StringBuilder lineBuilder = new StringBuilder();
    for (long threadId : renderer.getSortedThreadIds(sortByTime)) {
      lines.add(renderer.renderStatusLine(threadId, lineBuilder));
    }
    return lines.build();
  }

  private ImmutableList<String> renderShortStatus(
      BuildThreadStateRenderer renderer,
      boolean sortByTime) {
    ImmutableList.Builder<String> status = ImmutableList.builder();
    for (long threadId : renderer.getSortedThreadIds(sortByTime)) {
      status.add(renderer.renderShortStatus(threadId));
    }
    return status.build();
  }
}
