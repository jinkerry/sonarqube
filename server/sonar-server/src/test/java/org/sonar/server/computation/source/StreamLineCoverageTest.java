/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.computation.source;

import org.junit.Test;
import org.sonar.batch.protocol.output.BatchReport;
import org.sonar.server.source.db.FileSourceDb;

import java.util.Collections;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;

public class StreamLineCoverageTest {

  @Test
  public void set_coverage() throws Exception {
    StreamLineCoverage computeCoverageLine = new StreamLineCoverage(newArrayList(BatchReport.Coverage.newBuilder()
      .setLine(1)
      .setConditions(10)
      .setUtHits(true)
      .setUtCoveredConditions(2)
      .setItHits(true)
      .setItCoveredConditions(3)
      .setOverallCoveredConditions(4)
      .build()).iterator());

    FileSourceDb.Line.Builder lineBuilder = FileSourceDb.Data.newBuilder().addLinesBuilder();
    computeCoverageLine.readLine(1, lineBuilder);

    assertThat(lineBuilder.getUtLineHits()).isEqualTo(1);
    assertThat(lineBuilder.getUtConditions()).isEqualTo(10);
    assertThat(lineBuilder.getItLineHits()).isEqualTo(1);
    assertThat(lineBuilder.getItConditions()).isEqualTo(10);
    assertThat(lineBuilder.getItCoveredConditions()).isEqualTo(3);
    assertThat(lineBuilder.getOverallLineHits()).isEqualTo(1);
    assertThat(lineBuilder.getOverallConditions()).isEqualTo(10);
  }

  @Test
  public void set_coverage_without_line_hits() throws Exception {
    StreamLineCoverage computeCoverageLine = new StreamLineCoverage(newArrayList(BatchReport.Coverage.newBuilder()
      .setLine(1)
      .setConditions(10)
      .setUtHits(false)
      .setUtCoveredConditions(2)
      .setItHits(false)
      .setItCoveredConditions(3)
      .setOverallCoveredConditions(4)
      .build()).iterator());

    FileSourceDb.Line.Builder lineBuilder = FileSourceDb.Data.newBuilder().addLinesBuilder();
    computeCoverageLine.readLine(1, lineBuilder);

    assertThat(lineBuilder.hasUtLineHits()).isFalse();
    assertThat(lineBuilder.hasItLineHits()).isFalse();
    assertThat(lineBuilder.hasOverallLineHits()).isFalse();
  }

  @Test
  public void set_overall_line_hits_with_only_ut() throws Exception {
    StreamLineCoverage computeCoverageLine = new StreamLineCoverage(newArrayList(BatchReport.Coverage.newBuilder()
        .setLine(1)
        .setUtHits(true)
        .setItHits(false)
        .build()).iterator());

    FileSourceDb.Line.Builder lineBuilder = FileSourceDb.Data.newBuilder().addLinesBuilder();
    computeCoverageLine.readLine(1, lineBuilder);

    assertThat(lineBuilder.getOverallLineHits()).isEqualTo(1);
  }

  @Test
  public void set_overall_line_hits_with_only_it() throws Exception {
    StreamLineCoverage computeCoverageLine = new StreamLineCoverage(newArrayList(BatchReport.Coverage.newBuilder()
      .setLine(1)
      .setUtHits(false)
      .setItHits(true)
      .build()).iterator());

    FileSourceDb.Line.Builder lineBuilder = FileSourceDb.Data.newBuilder().addLinesBuilder();
    computeCoverageLine.readLine(1, lineBuilder);

    assertThat(lineBuilder.getOverallLineHits()).isEqualTo(1);
  }

  @Test
  public void set_overall_line_hits_with_ut_and_it() throws Exception {
    StreamLineCoverage computeCoverageLine = new StreamLineCoverage(newArrayList(BatchReport.Coverage.newBuilder()
      .setLine(1)
      .setUtHits(true)
      .setItHits(true)
      .build()).iterator());

    FileSourceDb.Line.Builder lineBuilder = FileSourceDb.Data.newBuilder().addLinesBuilder();
    computeCoverageLine.readLine(1, lineBuilder);

    assertThat(lineBuilder.getOverallLineHits()).isEqualTo(1);
  }

  @Test
  public void nothing_to_do_when_no_coverage_info() throws Exception {
    StreamLineCoverage computeCoverageLine = new StreamLineCoverage(Collections.<BatchReport.Coverage>emptyList().iterator());

    FileSourceDb.Line.Builder lineBuilder = FileSourceDb.Data.newBuilder().addLinesBuilder();
    computeCoverageLine.readLine(1, lineBuilder);

    assertThat(lineBuilder.hasUtLineHits()).isFalse();
    assertThat(lineBuilder.hasUtConditions()).isFalse();
    assertThat(lineBuilder.hasItLineHits()).isFalse();
    assertThat(lineBuilder.hasItConditions()).isFalse();
    assertThat(lineBuilder.hasItCoveredConditions()).isFalse();
    assertThat(lineBuilder.hasOverallLineHits()).isFalse();
    assertThat(lineBuilder.hasOverallConditions()).isFalse();
  }

  @Test
  public void nothing_to_do_when_no_coverage_info_for_current_line() throws Exception {
    StreamLineCoverage computeCoverageLine = new StreamLineCoverage(newArrayList(
      BatchReport.Coverage.newBuilder()
        .setLine(1)
        .setConditions(10)
        .setUtHits(true)
        .setUtCoveredConditions(2)
        .setItHits(true)
        .setItCoveredConditions(3)
        .setOverallCoveredConditions(4)
        .build()
      // No coverage info on line 2
      ).iterator());

    FileSourceDb.Line.Builder line2Builder = FileSourceDb.Data.newBuilder().addLinesBuilder();
    computeCoverageLine.readLine(2, line2Builder);

    assertThat(line2Builder.hasUtLineHits()).isFalse();
    assertThat(line2Builder.hasUtConditions()).isFalse();
    assertThat(line2Builder.hasItLineHits()).isFalse();
    assertThat(line2Builder.hasItConditions()).isFalse();
    assertThat(line2Builder.hasItCoveredConditions()).isFalse();
    assertThat(line2Builder.hasOverallLineHits()).isFalse();
    assertThat(line2Builder.hasOverallConditions()).isFalse();
  }

  @Test
  public void nothing_to_do_when_no_coverage_info_for_next_line() throws Exception {
    StreamLineCoverage computeCoverageLine = new StreamLineCoverage(newArrayList(
      BatchReport.Coverage.newBuilder()
        .setLine(1)
        .setConditions(10)
        .setUtHits(true)
        .setUtCoveredConditions(2)
        .setItHits(true)
        .setItCoveredConditions(3)
        .setOverallCoveredConditions(4)
        .build()
      // No coverage info on line 2
      ).iterator());

    FileSourceDb.Data.Builder fileSourceBuilder = FileSourceDb.Data.newBuilder();
    FileSourceDb.Line.Builder line1Builder = fileSourceBuilder.addLinesBuilder();
    FileSourceDb.Line.Builder line2Builder = fileSourceBuilder.addLinesBuilder();
    computeCoverageLine.readLine(1, line1Builder);
    computeCoverageLine.readLine(2, line2Builder);

    assertThat(line2Builder.hasUtLineHits()).isFalse();
    assertThat(line2Builder.hasUtConditions()).isFalse();
    assertThat(line2Builder.hasItLineHits()).isFalse();
    assertThat(line2Builder.hasItConditions()).isFalse();
    assertThat(line2Builder.hasItCoveredConditions()).isFalse();
    assertThat(line2Builder.hasOverallLineHits()).isFalse();
    assertThat(line2Builder.hasOverallConditions()).isFalse();
  }

}
