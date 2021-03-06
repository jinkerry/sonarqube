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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.batch.protocol.Constants;
import org.sonar.batch.protocol.output.BatchReport;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.protobuf.DbFileSources;
import org.sonar.db.source.FileSourceDto;
import org.sonar.db.source.FileSourceDto.Type;
import org.sonar.server.computation.batch.BatchReportReaderRule;
import org.sonar.server.computation.batch.TreeRootHolderRule;
import org.sonar.server.computation.component.Component;
import org.sonar.server.computation.component.ReportComponent;
import org.sonar.server.computation.duplication.DuplicationRepositoryRule;
import org.sonar.server.computation.duplication.TextBlock;
import org.sonar.server.computation.scm.Changeset;
import org.sonar.server.computation.scm.ScmInfoRepositoryRule;
import org.sonar.server.computation.source.SourceLinesRepositoryRule;
import org.sonar.test.DbTests;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Category(DbTests.class)
public class PersistFileSourcesStepTest extends BaseStepTest {

  private static final int FILE_REF = 3;
  private static final String PROJECT_UUID = "PROJECT";
  private static final String PROJECT_KEY = "PROJECT_KEY";
  private static final String FILE_UUID = "FILE";
  private static final long NOW = 123456789L;

  private System2 system2 = mock(System2.class);

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public DbTester dbTester = DbTester.create(system2);
  @Rule
  public TreeRootHolderRule treeRootHolder = new TreeRootHolderRule();
  @Rule
  public BatchReportReaderRule reportReader = new BatchReportReaderRule();
  @Rule
  public ScmInfoRepositoryRule scmInfoRepository = new ScmInfoRepositoryRule();
  @Rule
  public SourceLinesRepositoryRule fileSourceRepository = new SourceLinesRepositoryRule();
  @Rule
  public DuplicationRepositoryRule duplicationRepository = DuplicationRepositoryRule.create(treeRootHolder);

  private DbClient dbClient = dbTester.getDbClient();
  private DbSession session = dbTester.getSession();

  private PersistFileSourcesStep underTest;

  @Before
  public void setup() {
    dbTester.truncateTables();
    when(system2.now()).thenReturn(NOW);
    underTest = new PersistFileSourcesStep(dbClient, system2, treeRootHolder, reportReader, fileSourceRepository, scmInfoRepository, duplicationRepository);
  }

  @Override
  protected ComputationStep step() {
    return underTest;
  }

  @Test
  public void persist_sources() {
    initBasicReport(2);

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    assertThat(fileSourceDto.getProjectUuid()).isEqualTo(PROJECT_UUID);
    assertThat(fileSourceDto.getFileUuid()).isEqualTo(FILE_UUID);
    assertThat(fileSourceDto.getBinaryData()).isNotEmpty();
    assertThat(fileSourceDto.getDataHash()).isNotEmpty();
    assertThat(fileSourceDto.getLineHashes()).isNotEmpty();
    assertThat(fileSourceDto.getCreatedAt()).isEqualTo(NOW);
    assertThat(fileSourceDto.getUpdatedAt()).isEqualTo(NOW);

    DbFileSources.Data data = FileSourceDto.decodeSourceData(fileSourceDto.getBinaryData());
    assertThat(data.getLinesCount()).isEqualTo(2);
    assertThat(data.getLines(0).getLine()).isEqualTo(1);
    assertThat(data.getLines(0).getSource()).isEqualTo("line1");
    assertThat(data.getLines(1).getLine()).isEqualTo(2);
    assertThat(data.getLines(1).getSource()).isEqualTo("line2");
  }

  @Test
  public void persist_source_hashes() {
    initBasicReport(2);

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, "FILE");
    assertThat(fileSourceDto.getLineHashes()).isEqualTo("137f72c3708c6bd0de00a0e5a69c699b\ne6251bcf1a7dc3ba5e7933e325bbe605");
    assertThat(fileSourceDto.getSrcHash()).isEqualTo("ee5a58024a155466b43bc559d953e018");
  }

  @Test
  public void persist_coverage() {
    initBasicReport(1);

    reportReader.putCoverage(FILE_REF, newArrayList(BatchReport.Coverage.newBuilder()
      .setLine(1)
      .setConditions(10)
      .setUtHits(true)
      .setUtCoveredConditions(2)
      .setItHits(true)
      .setItCoveredConditions(3)
      .setOverallCoveredConditions(4)
      .build()));

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    DbFileSources.Data data = FileSourceDto.decodeSourceData(fileSourceDto.getBinaryData());

    assertThat(data.getLinesList()).hasSize(1);

    assertThat(data.getLines(0).getUtLineHits()).isEqualTo(1);
    assertThat(data.getLines(0).getUtConditions()).isEqualTo(10);
    assertThat(data.getLines(0).getUtCoveredConditions()).isEqualTo(2);
    assertThat(data.getLines(0).hasItLineHits()).isTrue();
    assertThat(data.getLines(0).getItConditions()).isEqualTo(10);
    assertThat(data.getLines(0).getItCoveredConditions()).isEqualTo(3);
    assertThat(data.getLines(0).getOverallLineHits()).isEqualTo(1);
    assertThat(data.getLines(0).getOverallConditions()).isEqualTo(10);
    assertThat(data.getLines(0).getOverallCoveredConditions()).isEqualTo(4);
  }

  @Test
  public void persist_scm() {
    initBasicReport(1);
    scmInfoRepository.setScmInfo(FILE_REF, Changeset.newChangesetBuilder()
      .setAuthor("john")
      .setDate(123456789L)
      .setRevision("rev-1")
      .build());

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);

    assertThat(fileSourceDto.getRevision()).isEqualTo("rev-1");

    DbFileSources.Data data = FileSourceDto.decodeSourceData(fileSourceDto.getBinaryData());

    assertThat(data.getLinesList()).hasSize(1);

    assertThat(data.getLines(0).getScmAuthor()).isEqualTo("john");
    assertThat(data.getLines(0).getScmDate()).isEqualTo(123456789L);
    assertThat(data.getLines(0).getScmRevision()).isEqualTo("rev-1");
  }

  @Test
  public void persist_highlighting() {
    initBasicReport(1);

    reportReader.putSyntaxHighlighting(FILE_REF, newArrayList(BatchReport.SyntaxHighlighting.newBuilder()
      .setRange(BatchReport.TextRange.newBuilder()
        .setStartLine(1).setEndLine(1)
        .setStartOffset(2).setEndOffset(4)
        .build())
      .setType(Constants.HighlightingType.ANNOTATION)
      .build()));

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    DbFileSources.Data data = FileSourceDto.decodeSourceData(fileSourceDto.getBinaryData());

    assertThat(data.getLinesList()).hasSize(1);

    assertThat(data.getLines(0).getHighlighting()).isEqualTo("2,4,a");
  }

  @Test
  public void persist_symbols() {
    initBasicReport(3);

    reportReader.putSymbols(FILE_REF, newArrayList(
      BatchReport.Symbol.newBuilder()
        .setDeclaration(BatchReport.TextRange.newBuilder()
          .setStartLine(1).setEndLine(1).setStartOffset(2).setEndOffset(4)
          .build())
        .addReference(BatchReport.TextRange.newBuilder()
          .setStartLine(3).setEndLine(3).setStartOffset(1).setEndOffset(3)
          .build())
        .build()));

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    DbFileSources.Data data = FileSourceDto.decodeSourceData(fileSourceDto.getBinaryData());

    assertThat(data.getLinesList()).hasSize(3);

    assertThat(data.getLines(0).getSymbols()).isEqualTo("2,4,1");
    assertThat(data.getLines(1).getSymbols()).isEmpty();
    assertThat(data.getLines(2).getSymbols()).isEqualTo("1,3,1");
  }

  @Test
  public void persist_duplication() {
    initBasicReport(1);

    duplicationRepository.addDuplication(FILE_REF, new TextBlock(1, 2), new TextBlock(3, 4));

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    DbFileSources.Data data = FileSourceDto.decodeSourceData(fileSourceDto.getBinaryData());

    assertThat(data.getLinesList()).hasSize(1);

    assertThat(data.getLines(0).getDuplicationList()).hasSize(1);
  }

  @Test
  public void save_revision() {
    initBasicReport(1);
    scmInfoRepository.setScmInfo(FILE_REF, Changeset.newChangesetBuilder()
      .setAuthor("john")
      .setDate(123456789L)
      .setRevision("rev-1")
      .build());

    underTest.execute();

    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    assertThat(fileSourceDto.getRevision()).isEqualTo("rev-1");
  }

  @Test
  public void not_save_revision() {
    initBasicReport(1);

    underTest.execute();

    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    assertThat(fileSourceDto.getRevision()).isNull();
  }

  @Test
  public void not_update_sources_when_nothing_has_changed() {
    // Existing sources
    long past = 150000L;
    String srcHash = "137f72c3708c6bd0de00a0e5a69c699b";
    String lineHashes = "137f72c3708c6bd0de00a0e5a69c699b";
    String dataHash = "29f25900140c94db38035128cb6de6a2";

    dbClient.fileSourceDao().insert(dbTester.getSession(), new FileSourceDto()
      .setProjectUuid(PROJECT_UUID)
      .setFileUuid(FILE_UUID)
      .setSrcHash(srcHash)
      .setLineHashes(lineHashes)
      .setDataHash(dataHash)
      .setSourceData(DbFileSources.Data.newBuilder()
        .addLines(DbFileSources.Line.newBuilder()
          .setLine(1)
          .setSource("line1")
          .build())
        .build())
      .setCreatedAt(past)
      .setUpdatedAt(past));
    dbTester.getSession().commit();

    // Sources from the report
    initBasicReport(1);

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    assertThat(fileSourceDto.getSrcHash()).isEqualTo(srcHash);
    assertThat(fileSourceDto.getLineHashes()).isEqualTo(lineHashes);
    assertThat(fileSourceDto.getDataHash()).isEqualTo(dataHash);
    assertThat(fileSourceDto.getCreatedAt()).isEqualTo(past);
    assertThat(fileSourceDto.getUpdatedAt()).isEqualTo(past);
  }

  @Test
  public void update_sources_when_source_updated() {
    // Existing sources
    long past = 150000L;
    dbClient.fileSourceDao().insert(dbTester.getSession(), new FileSourceDto()
      .setProjectUuid(PROJECT_UUID)
      .setFileUuid(FILE_UUID)
      .setDataType(Type.SOURCE)
      .setSrcHash("5b4bd9815cdb17b8ceae19eb1810c34c")
      .setLineHashes("6438c669e0d0de98e6929c2cc0fac474\n")
      .setDataHash("6cad150e3d065976c230cddc5a09efaa")
      .setSourceData(DbFileSources.Data.newBuilder()
        .addLines(DbFileSources.Line.newBuilder()
          .setLine(1)
          .setSource("old line")
          .build())
        .build())
      .setCreatedAt(past)
      .setUpdatedAt(past)
      .setRevision("rev-0"));
    dbTester.getSession().commit();

    initBasicReport(1);

    scmInfoRepository.setScmInfo(FILE_REF, Changeset.newChangesetBuilder()
      .setAuthor("john")
      .setDate(123456789L)
      .setRevision("rev-1")
      .build());

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    assertThat(fileSourceDto.getCreatedAt()).isEqualTo(past);
    assertThat(fileSourceDto.getUpdatedAt()).isEqualTo(NOW);
    assertThat(fileSourceDto.getRevision()).isEqualTo("rev-1");
  }

  @Test
  public void update_sources_when_src_hash_is_missing() {
    // Existing sources
    long past = 150000L;
    dbClient.fileSourceDao().insert(dbTester.getSession(), new FileSourceDto()
      .setProjectUuid(PROJECT_UUID)
      .setFileUuid(FILE_UUID)
      .setDataType(Type.SOURCE)
      // Source hash is missing, update will be made
      .setLineHashes("137f72c3708c6bd0de00a0e5a69c699b")
      .setDataHash("29f25900140c94db38035128cb6de6a2")
      .setSourceData(DbFileSources.Data.newBuilder()
        .addLines(DbFileSources.Line.newBuilder()
          .setLine(1)
          .setSource("line")
          .build())
        .build())
      .setCreatedAt(past)
      .setUpdatedAt(past));
    dbTester.getSession().commit();

    initBasicReport(1);

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    assertThat(fileSourceDto.getCreatedAt()).isEqualTo(past);
    assertThat(fileSourceDto.getUpdatedAt()).isEqualTo(NOW);
    assertThat(fileSourceDto.getSrcHash()).isEqualTo("137f72c3708c6bd0de00a0e5a69c699b");
  }

  @Test
  public void update_sources_when_revision_is_missing() {
    // Existing sources
    long past = 150000L;
    dbClient.fileSourceDao().insert(dbTester.getSession(), new FileSourceDto()
      .setProjectUuid(PROJECT_UUID)
      .setFileUuid(FILE_UUID)
      .setDataType(Type.SOURCE)
      .setSrcHash("137f72c3708c6bd0de00a0e5a69c699b")
      .setLineHashes("137f72c3708c6bd0de00a0e5a69c699b")
      .setDataHash("8e84c0d961cfe364e43833c4cc4ddef5")
      // Revision is missing, update will be made
      .setSourceData(DbFileSources.Data.newBuilder()
        .addLines(DbFileSources.Line.newBuilder()
          .setLine(1)
          .setSource("line")
          .build())
        .build())
      .setCreatedAt(past)
      .setUpdatedAt(past));
    dbTester.getSession().commit();

    scmInfoRepository.setScmInfo(FILE_REF, Changeset.newChangesetBuilder()
      .setAuthor("john")
      .setDate(123456789L)
      .setRevision("rev-1")
      .build());

    initBasicReport(1);

    underTest.execute();

    assertThat(dbTester.countRowsOfTable("file_sources")).isEqualTo(1);
    FileSourceDto fileSourceDto = dbClient.fileSourceDao().selectSourceByFileUuid(session, FILE_UUID);
    assertThat(fileSourceDto.getCreatedAt()).isEqualTo(past);
    assertThat(fileSourceDto.getUpdatedAt()).isEqualTo(NOW);
    assertThat(fileSourceDto.getRevision()).isEqualTo("rev-1");
  }

  private void initBasicReport(int numberOfLines) {
    treeRootHolder.setRoot(ReportComponent.builder(Component.Type.PROJECT, 1).setUuid(PROJECT_UUID).setKey(PROJECT_KEY).addChildren(
      ReportComponent.builder(Component.Type.MODULE, 2).setUuid("MODULE").setKey("MODULE_KEY").addChildren(
        ReportComponent.builder(Component.Type.FILE, FILE_REF).setUuid(FILE_UUID).setKey("MODULE_KEY:src/Foo.java").build())
        .build())
      .build());

    reportReader.putComponent(BatchReport.Component.newBuilder()
      .setRef(1)
      .setType(Constants.ComponentType.PROJECT)
      .addChildRef(2)
      .build());
    reportReader.putComponent(BatchReport.Component.newBuilder()
      .setRef(2)
      .setType(Constants.ComponentType.MODULE)
      .addChildRef(FILE_REF)
      .build());
    reportReader.putComponent(BatchReport.Component.newBuilder()
      .setRef(FILE_REF)
      .setType(Constants.ComponentType.FILE)
      .build());

    for (int i = 1; i <= numberOfLines; i++) {
      fileSourceRepository.addLine(FILE_REF, "line" + i);
    }
  }

}
