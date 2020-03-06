/**
 *    Copyright 2009-2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser { //属性解析器


  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * The special property key that indicate whether enable a default value on placeholder.
   * <p>
   *   The default value is {@code false} (indicate disable a default value on placeholder)
   *   If you specify the {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
   * </p>
   * @since 3.4.2
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value"; //指定占位符的默认值的key

  /**
   * The special property key that specify a separator for key and default value on placeholder.
   * <p>
   *   The default separator is {@code ":"}.
   * </p>
   * @since 3.4.2
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator"; //默认分隔符

  private static final String ENABLE_DEFAULT_VALUE = "false";//默认情况关闭分隔符功能
  private static final String DEFAULT_VALUE_SEPARATOR = ":";//占位符和默认值之间的分隔符

  private PropertyParser() {
    // Prevent Instantiation
  }

  public static String parse(String string, Properties variables) { //
    VariableTokenHandler handler = new VariableTokenHandler(variables); //占位符解析器
    //创建GenericTokenParser指定占位符为${ }
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    return parser.parse(string);
  }
  //处理占位符类
  private static class VariableTokenHandler implements TokenHandler {
    private final Properties variables; //所有参数
    private final boolean enableDefaultValue; //是否支持默认值
    private final String defaultValueSeparator; //指定站位符合默认值之前的分割符

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

    private String getPropertyValue(String key, String defaultValue) {
      return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
    }

    @Override
    public String handleToken(String content) {
      if (variables != null) { //判断属性是否为空
        String key = content;
        if (enableDefaultValue) { //是否开启了可使用占位置默认值功能
          final int separatorIndex = content.indexOf(defaultValueSeparator); //获取分割符位置
          String defaultValue = null;
          if (separatorIndex >= 0) {
            key = content.substring(0, separatorIndex); //获取属性名称
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length()); //获取默认值
          }
          if (defaultValue != null) {
            return variables.getProperty(key, defaultValue); //设置属性
          }
        }
        if (variables.containsKey(key)) {//如果没有开启默认值功能直接返回值
          return variables.getProperty(key);
        }
      }
      return "${" + content + "}"; //如果属性里面没有找到，原样返回
    }
  }

}
