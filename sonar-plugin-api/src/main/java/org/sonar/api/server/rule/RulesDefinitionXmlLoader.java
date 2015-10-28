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
package org.sonar.api.server.rule;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.lang.StringUtils;
import org.codehaus.staxmate.SMInputFactory;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonar.check.Cardinality;

import static java.lang.String.format;

/**
 * Loads definitions of rules from a XML file.
 *
 * <h3>Usage</h3>
 * <pre>
 * public class MyJsRulesDefinition implements RulesDefinition {
 *
 *   private static final String PATH = "my-js-rules.xml";
 *   private final RulesDefinitionXmlLoader xmlLoader;
 *
 *   public MyJsRulesDefinition(RulesDefinitionXmlLoader xmlLoader) {
 *     this.xmlLoader = xmlLoader;
 *   }
 *
 *   {@literal @}Override
 *   public void define(Context context) {
 *     try (Reader reader = new InputStreamReader(getClass().getResourceAsStream(PATH), StandardCharsets.UTF_8)) {
 *       NewRepository repository = context.createRepository("my_js", "js").setName("My Javascript Analyzer");
 *       xmlLoader.load(repository, reader);
 *       repository.done();
 *     } catch (IOException e) {
 *       throw new IllegalStateException(String.format("Fail to read file %s", PATH), e);
 *     }
 *   }
 * }
 * </pre>
 *
 * <h3>XML Format</h3>
 * <pre>
 * &lt;rules&gt;
 *   &lt;rule&gt;
 *     &lt;!-- required key --&gt;
 *     &lt;key&gt;the-rule-key&lt;/key&gt;
 *
 *     &lt;!-- required name --&gt;
 *     &lt;name&gt;The purpose of the rule&lt;/name&gt;
 *
 *     &lt;!-- required description, in HTML format --&gt;
 *     &lt;description&gt;
 *       &lt;![CDATA[The HTML description]]&gt;
 *     &lt;/description&gt;
 *
 *     &lt;!-- Optional key for configuration of some rule engines --&gt;
 *     &lt;internalKey&gt;Checker/TreeWalker/LocalVariableName&lt;/internalKey&gt;
 *
 *     &lt;!-- Default severity when enabling the rule in a Quality profile.  --&gt;
 *     &lt;!-- Possible values are INFO, MINOR, MAJOR (default), CRITICAL, BLOCKER. --&gt;
 *     &lt;severity&gt;BLOCKER&lt;/severity&gt;
 *
 *     &lt;!-- Possible values are SINGLE (default) and MULTIPLE for template rules --&gt;
 *     &lt;cardinality&gt;SINGLE&lt;/cardinality&gt;
 *
 *     &lt;!-- Status displayed in rules console. Possible values are BETA, READY (default), DEPRECATED. --&gt;
 *     &lt;status&gt;BETA&lt;/status&gt;
 *
 *     &lt;!-- Optional tags. See org.sonar.api.server.rule.RuleTagFormat. --&gt;
 *     &lt;tag&gt;style&lt;/tag&gt;
 *     &lt;tag&gt;security&lt;/tag&gt;
 *
 *     &lt;param&gt;
 *       &lt;key&gt;the-param-key&lt;/key&gt;
 *       &lt;description&gt;
 *         &lt;![CDATA[the optional description, in HTML format]]&gt;
 *       &lt;/description&gt;
 *       &lt;!-- Optional default value, used when enabling the rule in a Quality profile --&gt;
 *       &lt;defaultValue&gt;42&lt;/defaultValue&gt;
 *     &lt;/param&gt;
 *     &lt;param&gt;
 *       &lt;key&gt;another-param&lt;/key&gt;
 *     &lt;/param&gt;
 *
 *     &lt;!-- SQALE debt - key of sub-characteristic --&gt;
 *     &lt;!-- See {@link org.sonar.api.server.rule.RulesDefinition.SubCharacteristics} for core supported values. Any other value can be used. --&gt;
 *     &lt;!-- Since 5.3 --&gt;
 *     &lt;debtSubCharacteristic&gt;MODULARITY&lt;/debtSubCharacteristic&gt;
 *
 *     &lt;!-- SQALE debt - type of debt remediation function --&gt;
 *     &lt;!-- See enum {@link org.sonar.api.server.debt.DebtRemediationFunction.Type} for supported values --&gt;
 *     &lt;!-- Since 5.3 --&gt;
 *     &lt;debtRemediationFunction&gt;LINEAR_OFFSET&lt;/debtRemediationFunction&gt;
 *
 *     &lt;!-- SQALE debt - raw description of the "effort to fix", used for some types of remediation functions. --&gt;
 *     &lt;!-- See {@link org.sonar.api.server.rule.RulesDefinition.NewRule#setEffortToFixDescription(String)} --&gt;
 *     &lt;!-- Since 5.3 --&gt;
 *     &lt;effortToFixDescription&gt;Effort to test one uncovered condition&lt;/effortToFixDescription&gt;
 *
 *     &lt;!-- SQALE debt - coefficient of debt remediation function. Must be defined only for some function types. --&gt;
 *     &lt;!-- See {@link org.sonar.api.server.rule.RulesDefinition.DebtRemediationFunctions} --&gt;
 *     &lt;!-- Since 5.3 --&gt;
 *     &lt;debtRemediationFunctionCoefficient&gt;10min&lt;/debtRemediationFunctionCoefficient&gt;
 *
 *     &lt;!-- SQALE debt - offset of debt remediation function. Must be defined only for some function types. --&gt;
 *     &lt;!-- See {@link org.sonar.api.server.rule.RulesDefinition.DebtRemediationFunctions} --&gt;
 *     &lt;!-- Since 5.3 --&gt;
 *     &lt;debtRemediationFunctionOffset&gt;2min&lt;/debtRemediationFunctionOffset&gt;
 *
 *     &lt;!-- deprecated field, replaced by "internalKey" --&gt;
 *     &lt;configKey&gt;Checker/TreeWalker/LocalVariableName&lt;/configKey&gt;
 *
 *     &lt;!-- deprecated field, replaced by "severity" --&gt;
 *     &lt;priority&gt;BLOCKER&lt;/priority&gt;
 *   &lt;/rule&gt;
 * &lt;/rules&gt;
 * </pre>
 *
 * <h3>XML Example</h3>
 * <pre>
 * &lt;rules&gt;
 *   &lt;rule&gt;
 *     &lt;key&gt;S1442&lt;/key&gt;
 *     &lt;name&gt;"alert(...)" should not be used&lt;/name&gt;
 *     &lt;description&gt;alert(...) can be useful for debugging during development, but ...&lt;/description&gt;
 *     &lt;tag&gt;cwe&lt;/tag&gt;
 *     &lt;tag&gt;security&lt;/tag&gt;
 *     &lt;tag&gt;user-experience&lt;/tag&gt;
 *     &lt;debtSubCharacteristic&gt;SECURITY_FEATURES&lt;/debtSubCharacteristic&gt;
 *     &lt;debtRemediationFunction&gt;CONSTANT&lt;/debtRemediationFunction&gt;
 *     &lt;debtRemediationFunctionOffset&gt;10min&lt;/debtRemediationFunctionOffset&gt;
 *   &lt;/rule&gt;
 *
 *   &lt;!-- another rules... --&gt;
 * &lt;/rules&gt;
 * </pre>
 *
 * @see org.sonar.api.server.rule.RulesDefinition
 * @since 4.3
 */
@ServerSide
public class RulesDefinitionXmlLoader {

  /**
   * Loads rules by reading the XML input stream. The input stream is not always closed by the method, so it
   * should be handled by the caller.
   * @since 4.3
   */
  public void load(RulesDefinition.NewRepository repo, InputStream input, String encoding) {
    load(repo, input, Charset.forName(encoding));
  }

  /**
   * @since 5.1
   */
  public void load(RulesDefinition.NewRepository repo, InputStream input, Charset charset) {
    try (Reader reader = new InputStreamReader(input, charset)) {
      load(repo, reader);
    } catch (IOException e) {
      throw new IllegalStateException("Error while reading XML rules definition for repository " + repo.key(), e);
    }
  }

  /**
   * Loads rules by reading the XML input stream. The reader is closed by the method, so it
   * should be handled by the caller.
   * @since 4.3
   */
  public void load(RulesDefinition.NewRepository repo, Reader reader) {
    XMLInputFactory xmlFactory = XMLInputFactory.newInstance();
    xmlFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
    xmlFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
    // just so it won't try to load DTD in if there's DOCTYPE
    xmlFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    xmlFactory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
    SMInputFactory inputFactory = new SMInputFactory(xmlFactory);
    try {
      SMHierarchicCursor rootC = inputFactory.rootElementCursor(reader);
      rootC.advance(); // <rules>

      SMInputCursor rulesC = rootC.childElementCursor("rule");
      while (rulesC.getNext() != null) {
        // <rule>
        processRule(repo, rulesC);
      }

    } catch (XMLStreamException e) {
      throw new IllegalStateException("XML is not valid", e);
    }
  }

  private void processRule(RulesDefinition.NewRepository repo, SMInputCursor ruleC) throws XMLStreamException {
    String key = null;
    String name = null;
    String description = null;
    String internalKey = null;
    String severity = Severity.defaultSeverity();
    RuleStatus status = RuleStatus.defaultStatus();
    boolean template = false;
    String effortToFixDescription = null;
    String debtSubCharacteristic = null;
    String debtRemediationFunction = null;
    String debtRemediationFunctionOffset = null;
    String debtRemediationFunctionCoeff = null;
    List<ParamStruct> params = new ArrayList<>();
    List<String> tags = new ArrayList<>();

    /* BACKWARD COMPATIBILITY WITH VERY OLD FORMAT */
    String keyAttribute = ruleC.getAttrValue("key");
    if (StringUtils.isNotBlank(keyAttribute)) {
      key = StringUtils.trim(keyAttribute);
    }
    String priorityAttribute = ruleC.getAttrValue("priority");
    if (StringUtils.isNotBlank(priorityAttribute)) {
      severity = StringUtils.trim(priorityAttribute);
    }

    SMInputCursor cursor = ruleC.childElementCursor();
    while (cursor.getNext() != null) {
      String nodeName = cursor.getLocalName();

      if (StringUtils.equalsIgnoreCase("name", nodeName)) {
        name = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("description", nodeName)) {
        description = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("key", nodeName)) {
        key = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("configKey", nodeName)) {
        // deprecated field, replaced by internalKey
        internalKey = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("internalKey", nodeName)) {
        internalKey = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("priority", nodeName)) {
        // deprecated field, replaced by severity
        severity = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("severity", nodeName)) {
        severity = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("cardinality", nodeName)) {
        template = Cardinality.MULTIPLE == Cardinality.valueOf(StringUtils.trim(cursor.collectDescendantText(false)));

      } else if (StringUtils.equalsIgnoreCase("effortToFixDescription", nodeName)) {
        effortToFixDescription = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("debtRemediationFunction", nodeName)) {
        debtRemediationFunction = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("debtRemediationFunctionOffset", nodeName)) {
        debtRemediationFunctionOffset = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("debtRemediationFunctionCoefficient", nodeName)) {
        debtRemediationFunctionCoeff = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("debtSubCharacteristic", nodeName)) {
        debtSubCharacteristic = StringUtils.trim(cursor.collectDescendantText(false));

      } else if (StringUtils.equalsIgnoreCase("status", nodeName)) {
        String s = StringUtils.trim(cursor.collectDescendantText(false));
        if (s != null) {
          status = RuleStatus.valueOf(s);
        }

      } else if (StringUtils.equalsIgnoreCase("param", nodeName)) {
        params.add(processParameter(cursor));

      } else if (StringUtils.equalsIgnoreCase("tag", nodeName)) {
        tags.add(StringUtils.trim(cursor.collectDescendantText(false)));
      }
    }

    try {
      RulesDefinition.NewRule rule = repo.createRule(key)
          .setHtmlDescription(description)
          .setSeverity(severity)
          .setName(name)
          .setInternalKey(internalKey)
          .setTags(tags.toArray(new String[tags.size()]))
          .setTemplate(template)
          .setStatus(status)
          .setEffortToFixDescription(effortToFixDescription)
          .setDebtSubCharacteristic(debtSubCharacteristic);
      fillRemediationFunction(rule, debtRemediationFunction, debtRemediationFunctionOffset, debtRemediationFunctionCoeff);
      fillParams(rule, params);
    } catch (Exception e) {
      throw new IllegalArgumentException(format("Fail to load the rule with key [%s:%s]", repo.key(), key), e);
    }
  }

  private void fillRemediationFunction(RulesDefinition.NewRule rule, @Nullable String debtRemediationFunction, @Nullable String functionOffset, @Nullable String functionCoeff) {
    if (StringUtils.isNotBlank(debtRemediationFunction)) {
      DebtRemediationFunction.Type functionType = DebtRemediationFunction.Type.valueOf(debtRemediationFunction);
      rule.setDebtRemediationFunction(rule.debtRemediationFunctions().create(functionType, functionCoeff, functionOffset));
    }
  }

  private void fillParams(RulesDefinition.NewRule rule, List<ParamStruct> params) {
    for (ParamStruct param : params) {
      rule.createParam(param.key)
          .setDefaultValue(param.defaultValue)
          .setType(param.type)
          .setDescription(param.description);
    }
  }

  private static class ParamStruct {
    String key;
    String description;
    String defaultValue;
    RuleParamType type = RuleParamType.STRING;
  }

  private ParamStruct processParameter(SMInputCursor ruleC) throws XMLStreamException {
    ParamStruct param = new ParamStruct();

    // BACKWARD COMPATIBILITY WITH DEPRECATED FORMAT
    String keyAttribute = ruleC.getAttrValue("key");
    if (StringUtils.isNotBlank(keyAttribute)) {
      param.key = StringUtils.trim(keyAttribute);
    }

    // BACKWARD COMPATIBILITY WITH DEPRECATED FORMAT
    String typeAttribute = ruleC.getAttrValue("type");
    if (StringUtils.isNotBlank(typeAttribute)) {
      param.type = RuleParamType.parse(typeAttribute);
    }

    SMInputCursor paramC = ruleC.childElementCursor();
    while (paramC.getNext() != null) {
      String propNodeName = paramC.getLocalName();
      String propText = StringUtils.trim(paramC.collectDescendantText(false));
      if (StringUtils.equalsIgnoreCase("key", propNodeName)) {
        param.key = propText;

      } else if (StringUtils.equalsIgnoreCase("description", propNodeName)) {
        param.description = propText;

      } else if (StringUtils.equalsIgnoreCase("type", propNodeName)) {
        param.type = RuleParamType.parse(propText);

      } else if (StringUtils.equalsIgnoreCase("defaultValue", propNodeName)) {
        param.defaultValue = propText;
      }
    }
    return param;
  }
}
