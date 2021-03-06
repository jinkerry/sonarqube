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
package it.measureHistory;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarRunner;
import com.sonar.orchestrator.selenium.Selenese;
import it.Category1Suite;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import util.selenium.SeleneseTest;

import static util.ItUtils.projectDir;

public class TimeMachineUiTest {

  @ClassRule
  public static Orchestrator orchestrator = Category1Suite.ORCHESTRATOR;

  @Before
  public void setUp() throws Exception {
    orchestrator.resetData();
    orchestrator.getServer().provisionProject("sample", "sample");
    orchestrator.getServer().associateProjectToQualityProfile("sample", "xoo", "empty");
  }

  private static void analyzeProject(String date, String version) {
    orchestrator.executeBuild(SonarRunner.create(projectDir("shared/xoo-sample"))
        .setProperties("sonar.projectDate", date)
        .setProperties("sonar.projectVersion", version)
    );
  }

  // SONAR-3006
  @Test
  public void test_time_machine_dashboard() {
    analyzeProject("2012-09-01", "0.7");
    analyzeProject("2012-10-15", "0.8");
    analyzeProject("2012-11-30", "0.9");
    analyzeProject("2012-12-31", "1.0-SNAPSHOT");

    new SeleneseTest(Selenese.builder().setHtmlTestsInClasspath("timemachine",
      "/measureHistory/TimeMachineUiTest/should-display-timemachine-dashboard.html"
      ).build()).runOn(orchestrator);
  }

}
