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

package org.sonar.db.user;

import com.google.common.base.Optional;
import org.assertj.guava.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.RowNotFoundException;
import org.sonar.test.DbTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.user.UserTokenTesting.newUserToken;

@Category(DbTests.class)
public class UserTokenDaoTest {
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  DbSession dbSession;

  UserTokenDao underTest;

  @Before
  public void setUp() {
    underTest = db.getDbClient().userTokenDao();
    dbSession = db.getSession();
  }

  @Test
  public void insert_token() {
    UserTokenDto userToken = newUserToken();

    insertToken(userToken);

    UserTokenDto userTokenFromDb = underTest.selectOrFailByTokenHash(dbSession, userToken.getTokenHash());
    assertThat(userTokenFromDb).isNotNull();
    assertThat(userTokenFromDb.getName()).isEqualTo(userToken.getName());
    assertThat(userTokenFromDb.getCreatedAt()).isEqualTo(userToken.getCreatedAt());
    assertThat(userTokenFromDb.getTokenHash()).isEqualTo(userToken.getTokenHash());
    assertThat(userTokenFromDb.getLogin()).isEqualTo(userToken.getLogin());
  }

  @Test
  public void select_by_token_hash() {
    String tokenHash = "123456789";
    insertToken(newUserToken().setTokenHash(tokenHash));

    Optional<UserTokenDto> result = underTest.selectByTokenHash(dbSession, tokenHash);

    Assertions.assertThat(result).isPresent();
  }

  @Test
  public void fail_if_token_is_not_found() {
    expectedException.expect(RowNotFoundException.class);
    expectedException.expectMessage("User token with token hash 'unknown-token-hash' not found");

    underTest.selectOrFailByTokenHash(dbSession, "unknown-token-hash");
  }

  @Test
  public void select_by_login_and_name() {
    UserTokenDto userToken = newUserToken().setLogin("login").setName("name").setTokenHash("token");
    insertToken(userToken);

    Optional<UserTokenDto> optionalResultByLoginAndName = underTest.selectByLoginAndName(dbSession, userToken.getLogin(), userToken.getName());
    UserTokenDto resultByLoginAndName = optionalResultByLoginAndName.get();
    Optional<UserTokenDto> unfoundResult1 = underTest.selectByLoginAndName(dbSession, "unknown-login", userToken.getName());
    Optional<UserTokenDto> unfoundResult2 = underTest.selectByLoginAndName(dbSession, userToken.getLogin(), "unknown-name");

    Assertions.assertThat(unfoundResult1).isAbsent();
    Assertions.assertThat(unfoundResult2).isAbsent();
    assertThat(resultByLoginAndName.getLogin()).isEqualTo(userToken.getLogin());
    assertThat(resultByLoginAndName.getName()).isEqualTo(userToken.getName());
    assertThat(resultByLoginAndName.getCreatedAt()).isEqualTo(userToken.getCreatedAt());
    assertThat(resultByLoginAndName.getTokenHash()).isEqualTo(userToken.getTokenHash());
  }

  @Test
  public void delete_tokens_by_login() {
    insertToken(newUserToken().setLogin("login-to-delete"));
    insertToken(newUserToken().setLogin("login-to-delete"));
    insertToken(newUserToken().setLogin("login-to-keep"));

    underTest.deleteByLogin(dbSession, "login-to-delete");
    db.commit();

    assertThat(underTest.selectByLogin(dbSession, "login-to-delete")).isEmpty();
    assertThat(underTest.selectByLogin(dbSession, "login-to-keep")).hasSize(1);
  }

  private void insertToken(UserTokenDto userToken) {
    underTest.insert(dbSession, userToken);
    dbSession.commit();
  }
}
