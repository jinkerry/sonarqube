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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Ordering;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.sonar.db.protobuf.DbFileSources;
import org.sonar.server.computation.duplication.Duplication;
import org.sonar.server.computation.duplication.InnerDuplicate;
import org.sonar.server.computation.duplication.TextBlock;

import static com.google.common.collect.FluentIterable.from;

public class DuplicationLineReader implements LineReader {

  private final Map<TextBlock, Integer> duplicatedTextBlockIndexByTextBlock;

  public DuplicationLineReader(Set<Duplication> duplications) {
    this.duplicatedTextBlockIndexByTextBlock = createIndexOfDuplicatedTextBlocks(duplications);
  }

  @Override
  public void read(DbFileSources.Line.Builder lineBuilder) {
    Predicate<Map.Entry<TextBlock, Integer>> containsLine = new TextBlockContainsLine(lineBuilder.getLine());
    for (Integer textBlockIndex : from(duplicatedTextBlockIndexByTextBlock.entrySet())
      .filter(containsLine)
      .transform(MapEntryToBlockId.INSTANCE)
      // list is sorted to cope with the non-guaranteed order of Map entries which would trigger false detection of changes
      // in {@link DbFileSources.Line#getDuplicationList()}
      .toSortedList(Ordering.natural())) {
      lineBuilder.addDuplication(textBlockIndex);
    }
  }

  private static boolean isLineInBlock(TextBlock range, int line) {
    return line >= range.getStart() && line <= range.getEnd();
  }

  /**
   *
   * <p>
   * This method uses the natural order of TextBlocks to ensure that given the same set of TextBlocks, they get the same
   * index. It avoids false detections of changes in {@link DbFileSources.Line#getDuplicationList()}.
   * </p>
   */
  private static Map<TextBlock, Integer> createIndexOfDuplicatedTextBlocks(Collection<Duplication> duplications) {
    List<TextBlock> duplicatedTextBlocks = extractAllDuplicatedTextBlocks(duplications);
    Collections.sort(duplicatedTextBlocks);
    return from(duplicatedTextBlocks)
      .toMap(new TextBlockIndexGenerator());
  }

  /**
   * Duplicated blocks in the current file are either {@link Duplication#getOriginal()} or {@link Duplication#getDuplicates()}
   * when the {@link org.sonar.server.computation.duplication.Duplicate} is a {@link InnerDuplicate}.
   * <p>
   * The returned list is mutable on purpose because it will be sorted.
   * </p>
   *
   * @see {@link #createIndexOfDuplicatedTextBlocks(Collection)}
   */
  private static List<TextBlock> extractAllDuplicatedTextBlocks(Collection<Duplication> duplications) {
    List<TextBlock> duplicatedBlock = new ArrayList<>(duplications.size());
    for (Duplication duplication : duplications) {
      duplicatedBlock.add(duplication.getOriginal());
      for (InnerDuplicate duplicate : from(duplication.getDuplicates()).filter(InnerDuplicate.class)) {
        duplicatedBlock.add(duplicate.getTextBlock());
      }
    }
    return duplicatedBlock;
  }

  private static class TextBlockContainsLine implements Predicate<Map.Entry<TextBlock, Integer>> {
    private final int line;

    public TextBlockContainsLine(int line) {
      this.line = line;
    }

    @Override
    public boolean apply(@Nonnull Map.Entry<TextBlock, Integer> input) {
      return isLineInBlock(input.getKey(), line);
    }
  }

  private enum MapEntryToBlockId implements Function<Map.Entry<TextBlock, Integer>, Integer> {
    INSTANCE;

    @Override
    @Nonnull
    public Integer apply(@Nonnull Map.Entry<TextBlock, Integer> input) {
      return input.getValue();
    }
  }

  private static class TextBlockIndexGenerator implements Function<TextBlock, Integer> {
    int i = 1;

    @Nullable
    @Override
    public Integer apply(TextBlock input) {
      return i++;
    }
  }
}
