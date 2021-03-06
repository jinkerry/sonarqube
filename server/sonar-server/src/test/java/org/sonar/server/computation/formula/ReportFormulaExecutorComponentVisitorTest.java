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

package org.sonar.server.computation.formula;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.server.computation.batch.TreeRootHolderRule;
import org.sonar.server.computation.component.Component;
import org.sonar.server.computation.component.PathAwareCrawler;
import org.sonar.server.computation.component.ReportComponent;
import org.sonar.server.computation.formula.counter.IntVariationValue;
import org.sonar.server.computation.measure.Measure;
import org.sonar.server.computation.measure.MeasureRepositoryRule;
import org.sonar.server.computation.measure.MeasureVariations;
import org.sonar.server.computation.metric.Metric;
import org.sonar.server.computation.metric.MetricRepositoryRule;
import org.sonar.server.computation.period.Period;
import org.sonar.server.computation.period.PeriodsHolderRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.measures.CoreMetrics.LINES_KEY;
import static org.sonar.api.measures.CoreMetrics.NCLOC_KEY;
import static org.sonar.api.measures.CoreMetrics.NEW_IT_COVERAGE_KEY;
import static org.sonar.api.measures.CoreMetrics.NEW_LINES_TO_COVER_KEY;
import static org.sonar.server.computation.component.Component.Type.DIRECTORY;
import static org.sonar.server.computation.component.Component.Type.MODULE;
import static org.sonar.server.computation.component.Component.Type.PROJECT;
import static org.sonar.server.computation.component.ReportComponent.builder;
import static org.sonar.server.computation.measure.Measure.newMeasureBuilder;
import static org.sonar.server.computation.measure.MeasureRepoEntry.entryOf;
import static org.sonar.server.computation.measure.MeasureRepoEntry.toEntries;

public class ReportFormulaExecutorComponentVisitorTest {
  private static final int ROOT_REF = 1;
  private static final int MODULE_1_REF = 11;
  private static final int DIRECTORY_1_REF = 111;
  private static final int FILE_1_REF = 1111;
  private static final int FILE_2_REF = 1112;
  private static final int MODULE_2_REF = 12;
  private static final int DIRECTORY_2_REF = 121;
  private static final int FILE_3_REF = 1211;

  private static final ReportComponent BALANCED_COMPONENT_TREE = ReportComponent.builder(PROJECT, ROOT_REF)
    .addChildren(
      ReportComponent.builder(MODULE, MODULE_1_REF)
        .addChildren(
          ReportComponent.builder(DIRECTORY, DIRECTORY_1_REF)
            .addChildren(
              builder(Component.Type.FILE, FILE_1_REF).build(),
              builder(Component.Type.FILE, FILE_2_REF).build())
            .build())
        .build(),
      ReportComponent.builder(MODULE, MODULE_2_REF)
        .addChildren(
          ReportComponent.builder(DIRECTORY, DIRECTORY_2_REF)
            .addChildren(
              builder(Component.Type.FILE, FILE_3_REF).build())
            .build())
        .build())
    .build();

  @Rule
  public TreeRootHolderRule treeRootHolder = new TreeRootHolderRule();
  @Rule
  public MetricRepositoryRule metricRepository = new MetricRepositoryRule()
    .add(CoreMetrics.LINES)
    .add(CoreMetrics.NCLOC)
    .add(CoreMetrics.NEW_LINES_TO_COVER)
    .add(CoreMetrics.NEW_IT_COVERAGE);
  @Rule
  public MeasureRepositoryRule measureRepository = MeasureRepositoryRule.create(treeRootHolder, metricRepository);
  @Rule
  public PeriodsHolderRule periodsHolder = new PeriodsHolderRule()
    .setPeriods(new Period(2, "some mode", null, 95l, 756l), new Period(5, "some other mode", null, 756L, 956L));

  @Test
  public void verify_aggregation_on_value() throws Exception {
    treeRootHolder.setRoot(BALANCED_COMPONENT_TREE);

    measureRepository.addRawMeasure(FILE_1_REF, LINES_KEY, newMeasureBuilder().create(10));
    measureRepository.addRawMeasure(FILE_2_REF, LINES_KEY, newMeasureBuilder().create(8));
    measureRepository.addRawMeasure(FILE_3_REF, LINES_KEY, newMeasureBuilder().create(2));

    new PathAwareCrawler<>(formulaExecutorComponentVisitor(new FakeFormula()))
      .visit(BALANCED_COMPONENT_TREE);

    assertAddedRawMeasure(ROOT_REF, 20);
    assertAddedRawMeasure(MODULE_1_REF, 18);
    assertAddedRawMeasure(111, 18);
    assertAddedRawMeasure(FILE_1_REF, 10);
    assertAddedRawMeasure(FILE_2_REF, 8);
    assertAddedRawMeasure(MODULE_2_REF, 2);
    assertAddedRawMeasure(DIRECTORY_2_REF, 2);
    assertAddedRawMeasure(FILE_3_REF, 2);
  }

  @Test
  public void verify_multi_metric_formula_support_and_aggregation() throws Exception {
    treeRootHolder.setRoot(BALANCED_COMPONENT_TREE);

    measureRepository.addRawMeasure(FILE_1_REF, LINES_KEY, newMeasureBuilder().create(10));
    measureRepository.addRawMeasure(FILE_2_REF, LINES_KEY, newMeasureBuilder().create(8));
    measureRepository.addRawMeasure(FILE_3_REF, LINES_KEY, newMeasureBuilder().create(2));

    new PathAwareCrawler<>(formulaExecutorComponentVisitor(new FakeMultiMetricFormula()))
      .visit(BALANCED_COMPONENT_TREE);

    assertThat(toEntries(measureRepository.getAddedRawMeasures(ROOT_REF))).containsOnly(
      entryOf(NEW_LINES_TO_COVER_KEY, newMeasureBuilder().create(30)),
      entryOf(NEW_IT_COVERAGE_KEY, newMeasureBuilder().create(120)));
    assertThat(toEntries(measureRepository.getAddedRawMeasures(MODULE_1_REF))).containsOnly(
      entryOf(NEW_LINES_TO_COVER_KEY, newMeasureBuilder().create(28)),
      entryOf(NEW_IT_COVERAGE_KEY, newMeasureBuilder().create(118)));
    assertThat(toEntries(measureRepository.getAddedRawMeasures(111))).containsOnly(
      entryOf(NEW_LINES_TO_COVER_KEY, newMeasureBuilder().create(28)),
      entryOf(NEW_IT_COVERAGE_KEY, newMeasureBuilder().create(118)));
    assertThat(toEntries(measureRepository.getAddedRawMeasures(FILE_1_REF))).containsOnly(
      entryOf(NEW_LINES_TO_COVER_KEY, newMeasureBuilder().create(20)),
      entryOf(NEW_IT_COVERAGE_KEY, newMeasureBuilder().create(110)));
    assertThat(toEntries(measureRepository.getAddedRawMeasures(FILE_2_REF))).containsOnly(
      entryOf(NEW_LINES_TO_COVER_KEY, newMeasureBuilder().create(18)),
      entryOf(NEW_IT_COVERAGE_KEY, newMeasureBuilder().create(108)));
    assertThat(toEntries(measureRepository.getAddedRawMeasures(MODULE_2_REF))).containsOnly(
      entryOf(NEW_LINES_TO_COVER_KEY, newMeasureBuilder().create(MODULE_2_REF)),
      entryOf(NEW_IT_COVERAGE_KEY, newMeasureBuilder().create(102)));
    assertThat(toEntries(measureRepository.getAddedRawMeasures(DIRECTORY_2_REF))).containsOnly(
      entryOf(NEW_LINES_TO_COVER_KEY, newMeasureBuilder().create(MODULE_2_REF)),
      entryOf(NEW_IT_COVERAGE_KEY, newMeasureBuilder().create(102)));
    assertThat(toEntries(measureRepository.getAddedRawMeasures(FILE_3_REF))).containsOnly(
      entryOf(NEW_LINES_TO_COVER_KEY, newMeasureBuilder().create(MODULE_2_REF)),
      entryOf(NEW_IT_COVERAGE_KEY, newMeasureBuilder().create(102)));
  }

  @Test
  public void verify_aggregation_on_variations() throws Exception {
    treeRootHolder.setRoot(BALANCED_COMPONENT_TREE);

    measureRepository.addRawMeasure(FILE_1_REF, NEW_LINES_TO_COVER_KEY, createMeasureWithVariation(10, 20));
    measureRepository.addRawMeasure(FILE_2_REF, NEW_LINES_TO_COVER_KEY, createMeasureWithVariation(8, 16));
    measureRepository.addRawMeasure(FILE_3_REF, NEW_LINES_TO_COVER_KEY, createMeasureWithVariation(2, 4));

    new PathAwareCrawler<>(formulaExecutorComponentVisitor(new FakeVariationFormula()))
      .visit(BALANCED_COMPONENT_TREE);

    assertAddedRawMeasure(ROOT_REF, 20, 40);
    assertAddedRawMeasure(MODULE_1_REF, 18, 36);
    assertAddedRawMeasure(DIRECTORY_1_REF, 18, 36);
    assertAddedRawMeasure(FILE_1_REF, 10, 20);
    assertAddedRawMeasure(FILE_2_REF, 8, 16);
    assertAddedRawMeasure(MODULE_2_REF, 2, 4);
    assertAddedRawMeasure(DIRECTORY_2_REF, 2, 4);
    assertAddedRawMeasure(FILE_3_REF, 2, 4);
  }

  @Test
  public void measures_are_0_when_there_is_no_input_measure() throws Exception {
    ReportComponent project = ReportComponent.builder(PROJECT, ROOT_REF)
      .addChildren(
        ReportComponent.builder(MODULE, MODULE_1_REF)
          .addChildren(
            ReportComponent.builder(DIRECTORY, DIRECTORY_1_REF)
              .addChildren(
                builder(Component.Type.FILE, FILE_1_REF).build())
              .build())
          .build())
      .build();
    treeRootHolder.setRoot(project);

    new PathAwareCrawler<>(formulaExecutorComponentVisitor(new FakeFormula()))
      .visit(project);

    assertAddedRawMeasure(ROOT_REF, 0);
    assertAddedRawMeasure(MODULE_1_REF, 0);
    assertAddedRawMeasure(DIRECTORY_1_REF, 0);
    assertAddedRawMeasure(FILE_1_REF, 0);
  }

  @Test
  public void add_measure_even_when_leaf_is_not_FILE() throws Exception {
    ReportComponent project = ReportComponent.builder(PROJECT, ROOT_REF)
      .addChildren(
        ReportComponent.builder(MODULE, MODULE_1_REF)
          .addChildren(
            ReportComponent.builder(DIRECTORY, 111).build())
          .build())
      .build();
    treeRootHolder.setRoot(project);

    new PathAwareCrawler<>(formulaExecutorComponentVisitor(new FakeFormula()))
      .visit(project);

    assertAddedRawMeasure(MODULE_1_REF, 0);
    assertAddedRawMeasure(DIRECTORY_1_REF, 0);
  }

  private FormulaExecutorComponentVisitor formulaExecutorComponentVisitor(Formula formula) {
    return FormulaExecutorComponentVisitor.newBuilder(metricRepository, measureRepository)
      .withVariationSupport(periodsHolder)
      .buildFor(ImmutableList.of(formula));
  }

  private static Measure createMeasureWithVariation(double variation2Value, double variation5Value) {
    return newMeasureBuilder().setVariations(new MeasureVariations(null, variation2Value, null, null, variation5Value)).createNoValue();
  }

  private void assertAddedRawMeasure(int componentRef, int expectedValue) {
    assertThat(toEntries(measureRepository.getAddedRawMeasures(componentRef))).containsOnly(entryOf(NCLOC_KEY, newMeasureBuilder().create(expectedValue)));
  }

  private void assertAddedRawMeasure(int componentRef, int variation2Value, int variation5Value) {
    assertThat(toEntries(measureRepository.getAddedRawMeasures(componentRef)))
      .containsOnly(entryOf(NEW_IT_COVERAGE_KEY, createMeasureWithVariation(variation2Value, variation5Value)));
  }

  private class FakeFormula implements Formula<FakeCounter> {

    @Override
    public FakeCounter createNewCounter() {
      return new FakeCounter();
    }

    @Override
    public Optional<Measure> createMeasure(FakeCounter counter, CreateMeasureContext context) {
      // verify the context which is passed to the method
      assertThat(context.getPeriods()).isEqualTo(periodsHolder.getPeriods());
      assertThat(context.getComponent()).isNotNull();
      assertThat(context.getMetric()).isSameAs(metricRepository.getByKey(NCLOC_KEY));

      return Optional.of(Measure.newMeasureBuilder().create(counter.value));
    }

    @Override
    public String[] getOutputMetricKeys() {
      return new String[] {NCLOC_KEY};
    }
  }

  private class FakeMultiMetricFormula implements Formula<FakeCounter> {

    @Override
    public FakeCounter createNewCounter() {
      return new FakeCounter();
    }

    @Override
    public Optional<Measure> createMeasure(FakeCounter counter, CreateMeasureContext context) {
      // verify the context which is passed to the method
      assertThat(context.getPeriods()).isEqualTo(periodsHolder.getPeriods());
      assertThat(context.getComponent()).isNotNull();
      assertThat(context.getMetric())
        .isIn(metricRepository.getByKey(NEW_LINES_TO_COVER_KEY), metricRepository.getByKey(NEW_IT_COVERAGE_KEY));

      return Optional.of(Measure.newMeasureBuilder().create(counter.value + metricOffset(context.getMetric())));
    }

    private int metricOffset(Metric metric) {
      if (metric.getKey().equals(NEW_LINES_TO_COVER_KEY)) {
        return 10;
      }
      if (metric.getKey().equals(NEW_IT_COVERAGE_KEY)) {
        return 100;
      }
      throw new IllegalArgumentException("Unsupported metric " + metric);
    }

    @Override
    public String[] getOutputMetricKeys() {
      return new String[] {NEW_LINES_TO_COVER_KEY, NEW_IT_COVERAGE_KEY};
    }
  }

  private class FakeCounter implements Counter<FakeCounter> {
    private int value = 0;

    @Override
    public void aggregate(FakeCounter counter) {
      this.value += counter.value;
    }

    @Override
    public void initialize(CounterInitializationContext context) {
      // verify the context which is passed to the method
      assertThat(context.getLeaf().getChildren()).isEmpty();
      assertThat(context.getPeriods()).isEqualTo(periodsHolder.getPeriods());

      Optional<Measure> measureOptional = context.getMeasure(LINES_KEY);
      if (measureOptional.isPresent()) {
        value += measureOptional.get().getIntValue();
      }
    }
  }

  private class FakeVariationFormula implements Formula<FakeVariationCounter> {

    @Override
    public FakeVariationCounter createNewCounter() {
      return new FakeVariationCounter();
    }

    @Override
    public Optional<Measure> createMeasure(FakeVariationCounter counter, CreateMeasureContext context) {
      // verify the context which is passed to the method
      assertThat(context.getPeriods()).isEqualTo(periodsHolder.getPeriods());
      assertThat(context.getComponent()).isNotNull();
      assertThat(context.getMetric()).isSameAs(metricRepository.getByKey(NEW_IT_COVERAGE_KEY));

      Optional<MeasureVariations> measureVariations = counter.values.toMeasureVariations();
      if (measureVariations.isPresent()) {
        return Optional.of(
          newMeasureBuilder()
            .setVariations(measureVariations.get())
            .createNoValue());
      }
      return Optional.absent();
    }

    @Override
    public String[] getOutputMetricKeys() {
      return new String[] {NEW_IT_COVERAGE_KEY};
    }
  }

  private class FakeVariationCounter implements Counter<FakeVariationCounter> {
    private final IntVariationValue.Array values = IntVariationValue.newArray();

    @Override
    public void aggregate(FakeVariationCounter counter) {
      values.incrementAll(counter.values);
    }

    @Override
    public void initialize(CounterInitializationContext context) {
      // verify the context which is passed to the method
      assertThat(context.getLeaf().getChildren()).isEmpty();
      assertThat(context.getPeriods()).isEqualTo(periodsHolder.getPeriods());

      Optional<Measure> measureOptional = context.getMeasure(NEW_LINES_TO_COVER_KEY);
      if (!measureOptional.isPresent()) {
        return;
      }
      for (Period period : context.getPeriods()) {
        this.values.increment(
          period,
          (int) measureOptional.get().getVariations().getVariation(period.getIndex()));
      }
    }
  }

}
