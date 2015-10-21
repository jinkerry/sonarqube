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
package org.sonar.server.computation.step;

import org.junit.Rule;
import org.junit.Test;
import org.sonar.server.computation.batch.TreeRootHolderRule;
import org.sonar.server.computation.measure.MeasureRepositoryRule;
import org.sonar.server.computation.metric.MetricRepositoryRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.guava.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.sonar.api.measures.CoreMetrics.COMMENT_LINES;
import static org.sonar.api.measures.CoreMetrics.COMMENT_LINES_KEY;
import static org.sonar.api.measures.CoreMetrics.DUPLICATED_BLOCKS;
import static org.sonar.api.measures.CoreMetrics.DUPLICATED_BLOCKS_KEY;
import static org.sonar.api.measures.CoreMetrics.DUPLICATED_FILES;
import static org.sonar.api.measures.CoreMetrics.DUPLICATED_FILES_KEY;
import static org.sonar.api.measures.CoreMetrics.DUPLICATED_LINES;
import static org.sonar.api.measures.CoreMetrics.DUPLICATED_LINES_DENSITY;
import static org.sonar.api.measures.CoreMetrics.DUPLICATED_LINES_DENSITY_KEY;
import static org.sonar.api.measures.CoreMetrics.DUPLICATED_LINES_KEY;
import static org.sonar.api.measures.CoreMetrics.LINES;
import static org.sonar.api.measures.CoreMetrics.LINES_KEY;
import static org.sonar.api.measures.CoreMetrics.NCLOC;
import static org.sonar.api.measures.CoreMetrics.NCLOC_KEY;
import static org.sonar.server.computation.component.Component.Type.PROJECT_VIEW;
import static org.sonar.server.computation.component.Component.Type.SUBVIEW;
import static org.sonar.server.computation.component.Component.Type.VIEW;
import static org.sonar.server.computation.component.ViewsComponent.builder;
import static org.sonar.server.computation.measure.Measure.newMeasureBuilder;

public class ViewsDuplicationMeasuresStepTest {

  private static final int ROOT_REF = 1;
  private static final int SUBVIEW_REF = 12;
  private static final int SUB_SUBVIEW_REF = 123;
  private static final int PROJECT_VIEW_1_REF = 1231;
  private static final int PROJECT_VIEW_2_REF = 1232;
  private static final int PROJECT_VIEW_3_REF = 13;

  @Rule
  public TreeRootHolderRule treeRootHolder = new TreeRootHolderRule()
    .setRoot(builder(VIEW, ROOT_REF)
      .addChildren(
          builder(SUBVIEW, SUBVIEW_REF)
              .addChildren(
                  builder(SUBVIEW, SUB_SUBVIEW_REF)
                      .addChildren(
                          builder(PROJECT_VIEW, PROJECT_VIEW_1_REF).build(),
                          builder(PROJECT_VIEW, PROJECT_VIEW_2_REF).build())
                      .build())
              .build(),
          builder(PROJECT_VIEW, PROJECT_VIEW_3_REF).build())
      .build());
  @Rule
  public MetricRepositoryRule metricRepository = new MetricRepositoryRule()
    .add(LINES)
    .add(NCLOC)
    .add(COMMENT_LINES)
    .add(DUPLICATED_BLOCKS)
    .add(DUPLICATED_FILES)
    .add(DUPLICATED_LINES)
    .add(DUPLICATED_LINES_DENSITY);
  @Rule
  public MeasureRepositoryRule measureRepository = MeasureRepositoryRule.create(treeRootHolder, metricRepository);

  ComputationStep underTest = new DuplicationMeasuresStep(treeRootHolder, metricRepository, measureRepository);

  @Test
  public void aggregate_duplicated_blocks() {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_BLOCKS_KEY, 10);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_BLOCKS_KEY, 40);
    addRawMeasure(PROJECT_VIEW_3_REF, DUPLICATED_BLOCKS_KEY, 60);

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_BLOCKS_KEY, 50);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_BLOCKS_KEY, 50);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_BLOCKS_KEY, 110);
  }

  @Test
  public void aggregate_zero_duplicated_blocks() {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_BLOCKS_KEY, 0);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_BLOCKS_KEY, 0);
    // no raw measure for PROJECT_VIEW_3_REF

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_BLOCKS_KEY, 0);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_BLOCKS_KEY, 0);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_BLOCKS_KEY, 0);
  }

  @Test
  public void aggregate_zero_duplicated_blocks_when_no_data() {
    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_BLOCKS_KEY, 0);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_BLOCKS_KEY, 0);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_BLOCKS_KEY, 0);
  }

  @Test
  public void aggregate_duplicated_files() {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_FILES_KEY, 10);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_FILES_KEY, 40);
    addRawMeasure(PROJECT_VIEW_3_REF, DUPLICATED_FILES_KEY, 70);

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_FILES_KEY, 50);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_FILES_KEY, 50);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_FILES_KEY, 120);
  }

  @Test
  public void aggregate_zero_duplicated_files() {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_FILES_KEY, 0);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_FILES_KEY, 0);
    // no raw measure for PROJECT_VIEW_3_REF

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_FILES_KEY, 0);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_FILES_KEY, 0);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_FILES_KEY, 0);
  }

  @Test
  public void aggregate_zero_duplicated_files_when_no_data() {
    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_FILES_KEY, 0);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_FILES_KEY, 0);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_FILES_KEY, 0);
  }

  @Test
  public void aggregate_duplicated_lines() {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_LINES_KEY, 10);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_LINES_KEY, 40);
    addRawMeasure(PROJECT_VIEW_3_REF, DUPLICATED_LINES_KEY, 50);

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_LINES_KEY, 50);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_LINES_KEY, 50);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_LINES_KEY, 100);
  }

  @Test
  public void aggregate_zero_duplicated_line() {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_LINES_KEY, 0);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_LINES_KEY, 0);
    // no raw measure for PROJECT_VIEW_3_REF

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_LINES_KEY, 0);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_LINES_KEY, 0);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_LINES_KEY, 0);
  }

  @Test
  public void aggregate_zero_duplicated_line_when_no_data() {
    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_LINES_KEY, 0);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_LINES_KEY, 0);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_LINES_KEY, 0);
  }

  @Test
  public void compute_and_aggregate_duplicated_lines_density_using_lines() throws Exception {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_LINES_KEY, 2);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_LINES_KEY, 3);
    addRawMeasure(PROJECT_VIEW_3_REF, DUPLICATED_LINES_KEY, 4);

    addRawMeasure(PROJECT_VIEW_1_REF, LINES_KEY, 10);
    addRawMeasure(PROJECT_VIEW_2_REF, LINES_KEY, 40);
    addRawMeasure(PROJECT_VIEW_3_REF, LINES_KEY, 70);
    addRawMeasure(SUB_SUBVIEW_REF, LINES_KEY, 50);
    addRawMeasure(SUBVIEW_REF, LINES_KEY, 50);
    addRawMeasure(ROOT_REF, LINES_KEY, 50);

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_LINES_DENSITY_KEY, 10d);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_LINES_DENSITY_KEY, 10d);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_LINES_DENSITY_KEY, 18d);
  }

  @Test
  public void compute_and_aggregate_duplicated_lines_density_using_nclocs_and_comment_lines() throws Exception {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_LINES_KEY, 2);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_LINES_KEY, 3);
    addRawMeasure(PROJECT_VIEW_3_REF, DUPLICATED_LINES_KEY, 4);

    addRawMeasure(PROJECT_VIEW_1_REF, COMMENT_LINES_KEY, 2);
    addRawMeasure(PROJECT_VIEW_2_REF, COMMENT_LINES_KEY, 10);
    addRawMeasure(PROJECT_VIEW_3_REF, COMMENT_LINES_KEY, 20);
    addRawMeasure(SUB_SUBVIEW_REF, COMMENT_LINES_KEY, 12);
    addRawMeasure(SUBVIEW_REF, COMMENT_LINES_KEY, 12);
    addRawMeasure(ROOT_REF, COMMENT_LINES_KEY, 12);

    addRawMeasure(PROJECT_VIEW_1_REF, NCLOC_KEY, 8);
    addRawMeasure(PROJECT_VIEW_2_REF, NCLOC_KEY, 30);
    addRawMeasure(PROJECT_VIEW_3_REF, NCLOC_KEY, 50);
    addRawMeasure(SUB_SUBVIEW_REF, NCLOC_KEY, 38);
    addRawMeasure(SUBVIEW_REF, NCLOC_KEY, 38);
    addRawMeasure(ROOT_REF, NCLOC_KEY, 38);

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_LINES_DENSITY_KEY, 10d);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_LINES_DENSITY_KEY, 10d);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_LINES_DENSITY_KEY, 18d);
  }

  @Test
  public void compute_duplicated_lines_density_using_only_nclocs() throws Exception {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_LINES_KEY, 2);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_LINES_KEY, 3);

    addRawMeasure(PROJECT_VIEW_1_REF, NCLOC_KEY, 10);
    addRawMeasure(PROJECT_VIEW_2_REF, NCLOC_KEY, 40);
    addRawMeasure(SUB_SUBVIEW_REF, NCLOC_KEY, 50);
    addRawMeasure(SUBVIEW_REF, NCLOC_KEY, 50);
    addRawMeasure(ROOT_REF, NCLOC_KEY, 50);

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_LINES_DENSITY_KEY, 10d);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_LINES_DENSITY_KEY, 10d);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_LINES_DENSITY_KEY, 10d);
  }

  @Test
  public void compute_zero_percent_duplicated_lines_density_when_duplicated_lines_are_zero() throws Exception {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_LINES_KEY, 0);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_LINES_KEY, 0);
    // no raw measure for PROJECT_VIEW_3_REF

    addRawMeasure(PROJECT_VIEW_1_REF, COMMENT_LINES_KEY, 2);
    addRawMeasure(PROJECT_VIEW_2_REF, COMMENT_LINES_KEY, 10);
    addRawMeasure(SUB_SUBVIEW_REF, COMMENT_LINES_KEY, 12);
    addRawMeasure(SUBVIEW_REF, COMMENT_LINES_KEY, 12);
    addRawMeasure(ROOT_REF, COMMENT_LINES_KEY, 12);

    addRawMeasure(PROJECT_VIEW_1_REF, NCLOC_KEY, 8);
    addRawMeasure(PROJECT_VIEW_2_REF, NCLOC_KEY, 30);
    addRawMeasure(SUB_SUBVIEW_REF, NCLOC_KEY, 38);
    addRawMeasure(SUBVIEW_REF, NCLOC_KEY, 38);
    addRawMeasure(ROOT_REF, NCLOC_KEY, 38);

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_LINES_DENSITY_KEY, 0d);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_LINES_DENSITY_KEY, 0d);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_LINES_DENSITY_KEY, 0d);
  }

  @Test
  public void not_compute_duplicated_lines_density_when_lines_is_zero() throws Exception {
    addRawMeasure(PROJECT_VIEW_1_REF, LINES_KEY, 0);
    addRawMeasure(PROJECT_VIEW_2_REF, LINES_KEY, 0);
    // no raw measure for PROJECT_VIEW_3_REF
    addRawMeasure(SUB_SUBVIEW_REF, LINES_KEY, 0);
    addRawMeasure(SUBVIEW_REF, LINES_KEY, 0);
    addRawMeasure(ROOT_REF, LINES_KEY, 0);

    underTest.execute(mock(StepContext.class));

    assertNoRawMeasures(DUPLICATED_LINES_DENSITY_KEY);
  }

  @Test
  public void not_compute_duplicated_lines_density_when_ncloc_and_comment_are_zero() throws Exception {
    addRawMeasure(PROJECT_VIEW_1_REF, COMMENT_LINES_KEY, 0);
    addRawMeasure(PROJECT_VIEW_2_REF, COMMENT_LINES_KEY, 0);
    // no raw measure for PROJECT_VIEW_3_REF
    addRawMeasure(SUB_SUBVIEW_REF, COMMENT_LINES_KEY, 0);
    addRawMeasure(SUBVIEW_REF, COMMENT_LINES_KEY, 0);
    addRawMeasure(ROOT_REF, COMMENT_LINES_KEY, 0);

    addRawMeasure(PROJECT_VIEW_1_REF, NCLOC_KEY, 0);
    addRawMeasure(PROJECT_VIEW_2_REF, NCLOC_KEY, 0);
    addRawMeasure(SUB_SUBVIEW_REF, NCLOC_KEY, 0);
    addRawMeasure(SUBVIEW_REF, NCLOC_KEY, 0);
    addRawMeasure(ROOT_REF, NCLOC_KEY, 0);

    underTest.execute(mock(StepContext.class));

    assertNoRawMeasures(DUPLICATED_LINES_DENSITY_KEY);
  }

  @Test
  public void compute_100_percent_duplicated_lines_density() throws Exception {
    addRawMeasure(PROJECT_VIEW_1_REF, DUPLICATED_LINES_KEY, 2);
    addRawMeasure(PROJECT_VIEW_2_REF, DUPLICATED_LINES_KEY, 3);
    // no raw measure for PROJECT_VIEW_3_REF

    addRawMeasure(PROJECT_VIEW_1_REF, LINES_KEY, 2);
    addRawMeasure(PROJECT_VIEW_2_REF, LINES_KEY, 3);
    addRawMeasure(SUB_SUBVIEW_REF, LINES_KEY, 5);
    addRawMeasure(SUBVIEW_REF, LINES_KEY, 5);
    addRawMeasure(ROOT_REF, LINES_KEY, 5);

    underTest.execute(mock(StepContext.class));

    assertNoNewRawMeasuresOnProjectViews();
    assertRawMeasureValue(SUB_SUBVIEW_REF, DUPLICATED_LINES_DENSITY_KEY, 100d);
    assertRawMeasureValue(SUBVIEW_REF, DUPLICATED_LINES_DENSITY_KEY, 100d);
    assertRawMeasureValue(ROOT_REF, DUPLICATED_LINES_DENSITY_KEY, 100d);
  }

  private void addRawMeasure(int componentRef, String metricKey, int value) {
    measureRepository.addRawMeasure(componentRef, metricKey, newMeasureBuilder().create(value));
  }

  private void assertNoRawMeasures(String metricKey) {
    assertThat(measureRepository.getAddedRawMeasures(PROJECT_VIEW_1_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(PROJECT_VIEW_2_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(SUB_SUBVIEW_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(SUBVIEW_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(ROOT_REF).get(metricKey)).isEmpty();
  }

  private void assertNoNewRawMeasuresOnProjectViews() {
    assertThat(measureRepository.getAddedRawMeasures(PROJECT_VIEW_1_REF)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(PROJECT_VIEW_2_REF)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(PROJECT_VIEW_3_REF)).isEmpty();
  }

  private void assertNoRawMeasure(int componentRef, String metricKey) {
    assertThat(measureRepository.getAddedRawMeasure(componentRef, metricKey)).isAbsent();
  }

  private void assertRawMeasureValue(int componentRef, String metricKey, int value) {
    assertThat(measureRepository.getAddedRawMeasure(componentRef, metricKey).get().getIntValue()).isEqualTo(value);
  }

  private void assertRawMeasureValue(int componentRef, String metricKey, double value) {
    assertThat(measureRepository.getAddedRawMeasure(componentRef, metricKey).get().getDoubleValue()).isEqualTo(value);
  }

}
