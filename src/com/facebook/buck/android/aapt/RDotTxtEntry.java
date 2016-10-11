/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.android.aapt;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.util.MoreStrings;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Represents a row from a symbols file generated by {@code aapt}. */
public class RDotTxtEntry implements Comparable<RDotTxtEntry> {

  // Taken from http://developer.android.com/reference/android/R.html
  public static enum RType {
    ANIM,
    ANIMATOR,
    ARRAY,
    ATTR,
    BOOL,
    COLOR,
    DIMEN,
    DRAWABLE,
    FRACTION,
    ID,
    INTEGER,
    INTERPOLATOR,
    LAYOUT,
    MENU,
    MIPMAP,
    PLURALS,
    RAW,
    STRING,
    STYLE,
    STYLEABLE,
    TRANSITION,
    XML;

    @Override
    public String toString() {
      return super.toString().toLowerCase();
    }
  }

  public static enum IdType {
    INT,
    INT_ARRAY;

    public static IdType from(String raw) {
      if (raw.equals("int")) {
        return INT;
      } else if (raw.equals("int[]")) {
        return INT_ARRAY;
      }
      throw new IllegalArgumentException(String.format("'%s' is not a valid ID type.", raw));
    }

    @Override
    public String toString() {
      if (this.equals(INT)) {
        return "int";
      }
      return "int[]";
    }
  }

  public static final Function<String, RDotTxtEntry> TO_ENTRY =
      input -> {
        Optional<RDotTxtEntry> entry = parse(input);
        Preconditions.checkState(entry.isPresent(), "Could not parse R.txt entry: '%s'", input);
        return entry.get();
      };

  // An identifier for custom drawables.
  public static final String CUSTOM_DRAWABLE_IDENTIFIER = "#";
  public static final String INT_ARRAY_SEPARATOR = ",";
  private static final Pattern INT_ARRAY_VALUES = Pattern.compile("\\s*\\{\\s*(\\S+)?\\s*\\}\\s*");
  private static final Pattern TEXT_SYMBOLS_LINE =
      Pattern.compile(
          "(\\S+) (\\S+) (\\S+) ([^" + CUSTOM_DRAWABLE_IDENTIFIER + "]+)" +
          "( " + CUSTOM_DRAWABLE_IDENTIFIER + ")?");

  // A symbols file may look like:
  //
  //    int id placeholder 0x7f020000
  //    int string debug_http_proxy_dialog_title 0x7f030004
  //    int string debug_http_proxy_hint 0x7f030005
  //    int string debug_http_proxy_summary 0x7f030003
  //    int string debug_http_proxy_title 0x7f030002
  //    int string debug_ssl_cert_check_summary 0x7f030001
  //    int string debug_ssl_cert_check_title 0x7f030000
  //
  // Note that there are four columns of information:
  // - the type of the resource id (always seems to be int or int[], in practice)
  // - the type of the resource
  // - the name of the resource
  // - the value of the resource id
  //
  // Custom drawables will have an additional column to denote them.
  //    int drawable custom_drawable 0x07f01250 #
  public final IdType idType;
  public final RType type;
  public final String name;
  public final String idValue;
  public final boolean custom;

  public RDotTxtEntry(
      IdType idType,
      RType type,
      String name,
      String idValue) {
    this(idType, type, name, idValue, false);
  }

  public RDotTxtEntry(
      IdType idType,
      RType type,
      String name,
      String idValue,
      boolean custom) {
    this.idType = idType;
    this.type = type;
    this.name = name;
    this.idValue = idValue;
    this.custom = custom;
  }

  public int getNumArrayValues() {
    Preconditions.checkState(idType == IdType.INT_ARRAY);

    Matcher matcher = INT_ARRAY_VALUES.matcher(idValue);
    if (!matcher.matches() || matcher.group(1) == null) {
      return 0;
    }

    return matcher.group(1).split(INT_ARRAY_SEPARATOR).length;
  }

  public RDotTxtEntry copyWithNewIdValue(String newIdValue) {
    return new RDotTxtEntry(idType, type, name, newIdValue, custom);
  }

  public static Optional<RDotTxtEntry> parse(String rDotTxtLine) {
    Matcher matcher = TEXT_SYMBOLS_LINE.matcher(rDotTxtLine);
    if (!matcher.matches()) {
      return Optional.absent();
    }

    IdType idType = IdType.from(matcher.group(1));
    RType type = RType.valueOf(matcher.group(2).toUpperCase());
    String name = matcher.group(3);
    String idValue = matcher.group(4);
    boolean custom = matcher.group(5) != null;

    return Optional.of(new RDotTxtEntry(idType, type, name, idValue, custom));
  }

  public static Iterable<RDotTxtEntry> readResources(
      ProjectFilesystem owningFilesystem,
      Path rDotTxt)
      throws IOException {
    return FluentIterable.from(owningFilesystem.readLines(rDotTxt))
        .filter(MoreStrings.NON_EMPTY)
        .transform(RDotTxtEntry.TO_ENTRY);
  }

  /**
   * A collection of Resources should be sorted such that Resources of the same type should be
   * grouped together, and should be alphabetized within that group.
   */
  @Override
  public int compareTo(RDotTxtEntry that) {
    if (this == that) {
      return 0;
    }

    return ComparisonChain.start()
        .compare(this.type, that.type)
        .compare(this.name, that.name)
        .result();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof RDotTxtEntry)) {
      return false;
    }

    RDotTxtEntry that = (RDotTxtEntry) obj;
    return Objects.equal(this.type, that.type) && Objects.equal(this.name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, name);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(RDotTxtEntry.class)
        .add("idType", idType)
        .add("type", type)
        .add("name", name)
        .add("idValue", idValue)
        .toString();
  }
}
