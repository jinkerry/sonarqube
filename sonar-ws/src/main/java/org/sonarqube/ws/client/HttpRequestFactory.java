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
package org.sonarqube.ws.client;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.common.base.Throwables;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import java.io.InputStream;
import java.util.Arrays;
import javax.annotation.Nullable;
import org.sonarqube.ws.MediaTypes;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Not an API. Please do not use this class, except maybe for unit tests.
 */
public class HttpRequestFactory {

  private static final int[] RESPONSE_SUCCESS = {HTTP_OK, HTTP_CREATED, HTTP_NO_CONTENT};

  private final String baseUrl;
  private String login;
  private String password;
  private String proxyHost;
  private String proxyLogin;
  private String proxyPassword;
  private int proxyPort;
  private int connectTimeoutInMilliseconds;
  private int readTimeoutInMilliseconds;

  public HttpRequestFactory(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public HttpRequestFactory setLogin(@Nullable String login) {
    this.login = login;
    return this;
  }

  public HttpRequestFactory setPassword(@Nullable String password) {
    this.password = password;
    return this;
  }

  public HttpRequestFactory setProxyHost(@Nullable String proxyHost) {
    this.proxyHost = proxyHost;
    return this;
  }

  public HttpRequestFactory setProxyLogin(@Nullable String proxyLogin) {
    this.proxyLogin = proxyLogin;
    return this;
  }

  public HttpRequestFactory setProxyPassword(@Nullable String proxyPassword) {
    this.proxyPassword = proxyPassword;
    return this;
  }

  public HttpRequestFactory setProxyPort(int proxyPort) {
    this.proxyPort = proxyPort;
    return this;
  }

  public HttpRequestFactory setConnectTimeoutInMilliseconds(int connectTimeoutInMilliseconds) {
    this.connectTimeoutInMilliseconds = connectTimeoutInMilliseconds;
    return this;
  }

  public HttpRequestFactory setReadTimeoutInMilliseconds(int readTimeoutInMilliseconds) {
    this.readTimeoutInMilliseconds = readTimeoutInMilliseconds;
    return this;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getLogin() {
    return login;
  }

  public String getPassword() {
    return password;
  }

  public String getProxyHost() {
    return proxyHost;
  }

  public String getProxyLogin() {
    return proxyLogin;
  }

  public String getProxyPassword() {
    return proxyPassword;
  }

  public int getProxyPort() {
    return proxyPort;
  }

  public int getConnectTimeoutInMilliseconds() {
    return connectTimeoutInMilliseconds;
  }

  public int getReadTimeoutInMilliseconds() {
    return readTimeoutInMilliseconds;
  }

  public String execute(WsRequest wsRequest) {
    HttpRequest httpRequest = wsRequestToHttpRequest(wsRequest);
    return execute(httpRequest);
  }

  public <T extends Message> T execute(WsRequest wsRequest, Parser<T> protobufParser) {
    HttpRequest httpRequest = wsRequestToHttpRequest(wsRequest);
    InputStream response = executeWithStream(httpRequest);
    try {
      return protobufParser.parseFrom(response);
    } catch (InvalidProtocolBufferException e) {
      Throwables.propagate(e);
    }

    throw new IllegalStateException("Uncatched exception when parsing protobuf response");
  }

  private HttpRequest wsRequestToHttpRequest(WsRequest wsRequest) {
    HttpRequest httpRequest = WsRequest.Method.GET.equals(wsRequest.getMethod())
      ? HttpRequest.get(buildUrl(wsRequest.getEndpoint()), wsRequest.getParams(), true)
      : HttpRequest.post(buildUrl(wsRequest.getEndpoint()), wsRequest.getParams(), true);
    httpRequest = prepare(httpRequest);
    switch (wsRequest.getMediaType()) {
      case PROTOBUF:
        httpRequest.accept(MediaTypes.PROTOBUF);
        break;
      case JSON:
        httpRequest.accept(MediaTypes.JSON);
        break;
      case TEXT:
        httpRequest.accept(MediaTypes.TXT);
        break;
      default:
        httpRequest.accept(MediaTypes.DEFAULT);
        break;
    }

    return httpRequest;
  }

  private String buildUrl(String part) {
    StringBuilder url = new StringBuilder();
    url.append(baseUrl);
    if (!part.startsWith("/")) {
      url.append('/');
    }
    url.append(part);
    return url.toString();
  }

  private static String execute(HttpRequest request) {
    try {
      checkSuccess(request);
      return request.body(HttpRequest.CHARSET_UTF8);
    } catch (HttpRequest.HttpRequestException e) {
      throw new IllegalStateException("Fail to request " + request.url(), e);
    }
  }

  private static InputStream executeWithStream(HttpRequest request) {
    try {
      checkSuccess(request);
      return request.stream();
    } catch (HttpRequest.HttpRequestException e) {
      throw new IllegalStateException("Fail to request " + request.url(), e);
    }
  }

  private static void checkSuccess(HttpRequest request) {
    boolean isSuccess = Arrays.binarySearch(RESPONSE_SUCCESS, request.code()) >= 0;
    if (!isSuccess) {
      throw new HttpException(request.url().toString(), request.code(), request.body());
    }
  }

  private HttpRequest prepare(HttpRequest request) {
    if (proxyHost != null) {
      request.useProxy(proxyHost, proxyPort);
      if (proxyLogin != null) {
        request.proxyBasic(proxyLogin, proxyPassword);
      }
    }
    request
      .acceptGzipEncoding()
      .uncompress(true)
      .acceptJson()
      .acceptCharset(HttpRequest.CHARSET_UTF8)
      .connectTimeout(connectTimeoutInMilliseconds)
      .readTimeout(readTimeoutInMilliseconds)
      .trustAllCerts()
      .trustAllHosts();
    if (login != null) {
      request.basic(login, password);
    }
    return request;
  }
}
