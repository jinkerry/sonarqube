/*
 * Copyright (C) 2009-2014 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package upgradetest;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.OrchestratorBuilder;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.config.Configuration;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.version.Version;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Test;
import org.sonar.wsclient.services.ResourceQuery;

import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeTest {

  public static final String PROJECT_KEY = "org.apache.struts:struts-parent";

  private Orchestrator orchestrator;

  @After
  public void stop() {
    if (orchestrator != null) {
      orchestrator.stop();
      orchestrator = null;
    }
  }

  @Test
  public void test_upgrade_from_LTS() {
    testDatabaseUpgrade(Version.create("4.5.1"));
  }

  @Test
  public void test_upgrade_from_5_0() {
    testDatabaseUpgrade(Version.create("5.0.1"));
  }

  private void testDatabaseUpgrade(Version fromVersion) {
    startServer(fromVersion, false);
    scanProject();
    int files = countFiles(PROJECT_KEY);
    assertThat(files).isGreaterThan(0);

    stopServer();
    // latest version
    startServer(Version.create(Orchestrator.builderEnv().getSonarVersion()), true);
    checkSystemStatus(ServerStatusResponse.Status.DB_MIGRATION_NEEDED);
    upgradeDatabase();
    checkSystemStatus(ServerStatusResponse.Status.UP);

    assertThat(countFiles(PROJECT_KEY)).isEqualTo(files);
    browseWebapp();
    scanProject();
    assertThat(countFiles(PROJECT_KEY)).isEqualTo(files);
    browseWebapp();
  }

  private void checkSystemStatus(ServerStatusResponse.Status serverStatus) {
    ServerStatusResponse serverStatusResponse = new ServerStatusCall(orchestrator).call();

    assertThat(serverStatusResponse.getStatus()).isEqualTo(serverStatus);
  }

  private void browseWebapp() {
    testUrl("/");
    testUrl("/issues/index");
    testUrl("/dashboard/index/org.apache.struts:struts-parent");
    testUrl("/components/index/org.apache.struts:struts-parent");
    testUrl("/issues/search");
    testUrl("/component/index?id=org.apache.struts%3Astruts-core%3Asrc%2Fmain%2Fjava%2Forg%2Fapache%2Fstruts%2Fchain%2Fcommands%2Fgeneric%2FWrappingLookupCommand.java");
    testUrl("/profiles");
  }

  private void upgradeDatabase() {
    ServerMigrationResponse serverMigrationResponse = new ServerMigrationCall(orchestrator).callAndWait();

    assertThat(serverMigrationResponse.getStatus()).isEqualTo(ServerMigrationResponse.Status.MIGRATION_SUCCEEDED);
  }

  private void startServer(Version sqVersion, boolean keepDatabase) {
    OrchestratorBuilder builder = Orchestrator.builderEnv().setSonarVersion(sqVersion.toString());
    String jdbcUrl = MssqlConfig.fixUrl(Configuration.createEnv(), sqVersion);
    if (jdbcUrl != null) {
      builder.setOrchestratorProperty("sonar.jdbc.url", jdbcUrl);
    }
    builder.setOrchestratorProperty("orchestrator.keepDatabase", String.valueOf(keepDatabase));
    if (!keepDatabase) {
      builder.restoreProfileAtStartup(FileLocation.ofClasspath("/sonar-way-5.1.xml"));
    }
    builder.setOrchestratorProperty("javaVersion", "OLDEST_COMPATIBLE").addPlugin("java");
    orchestrator = builder.build();
    orchestrator.start();
  }

  private void stopServer() {
    if (orchestrator != null) {
      orchestrator.stop();
    }
  }

  private void scanProject() {
    MavenBuild build = MavenBuild.create(new File("../projects/struts-1.3.9-lite/pom.xml"))
      .setCleanPackageSonarGoals()
      .setProperty("sonar.dynamicAnalysis", "false")
      .setProperty("sonar.scm.disabled", "true")
      .setProperty("sonar.cpd.cross_project", "true")
      .setProperty("sonar.profile", "sonar-way-5.1");
    orchestrator.executeBuild(build);
  }

  private int countFiles(String key) {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(key, "files")).getMeasureIntValue("files");
  }

  private void testUrl(String path) {
    HttpURLConnection connection = null;
    try {
      URL url = new URL(orchestrator.getServer().getUrl() + path);
      connection = (HttpURLConnection) url.openConnection();
      connection.connect();
      assertThat(connection.getResponseCode()).as("Fail to load " + path).isEqualTo(HttpURLConnection.HTTP_OK);

      // Error HTML pages generated by Ruby on Rails
      String content = IOUtils.toString(connection.getInputStream());
      assertThat(content).as("Fail to load " + path).doesNotContain("something went wrong");
      assertThat(content).as("Fail to load " + path).doesNotContain("The page you were looking for doesn't exist");
      assertThat(content).as("Fail to load " + path).doesNotContain("Unauthorized access");

    } catch (IOException e) {
      throw new IllegalStateException("Error with " + path, e);

    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
}
