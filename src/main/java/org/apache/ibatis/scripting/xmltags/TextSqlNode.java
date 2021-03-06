/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.scripting.xmltags;

import java.util.regex.Pattern;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.scripting.ScriptingException;
import org.apache.ibatis.type.SimpleTypeRegistry;

/**
 * @author Clinton Begin
 */
public class TextSqlNode implements SqlNode { //组合模式中叶子节点
  private final String text; //
  private final Pattern injectionFilter; //

  public TextSqlNode(String text) {
    this(text, null);
  }
  
  public TextSqlNode(String text, Pattern injectionFilter) {
    this.text = text;
    this.injectionFilter = injectionFilter;
  }
  
  public boolean isDynamic() {
    DynamicCheckerTokenParser checker = new DynamicCheckerTokenParser();
    GenericTokenParser parser = createParser(checker);
    parser.parse(text);
    return checker.isDynamic();
  }

  @Override
  public boolean apply(DynamicContext context) {
    GenericTokenParser parser = createParser(new BindingTokenParser(context, injectionFilter)); //创建GenericTokenParser解析器
    context.appendSql(parser.parse(text)); //将解析后的SQL放入DynamicContext中
    return true;
  }
  
  private GenericTokenParser createParser(TokenHandler handler) {
    return new GenericTokenParser("${", "}", handler); //解析${}占位符
  }

  private static class BindingTokenParser implements TokenHandler {

    private DynamicContext context; //
    private Pattern injectionFilter;//

    public BindingTokenParser(DynamicContext context, Pattern injectionFilter) {
      this.context = context;
      this.injectionFilter = injectionFilter;
    }

    @Override
    public String handleToken(String content) { //此时content已经将${}去掉了直接使用ognl表达式就能获取到对应的值
      Object parameter = context.getBindings().get("_parameter"); //获取用户提供的实参
      if (parameter == null) {
        context.getBindings().put("value", null);
      } else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
        context.getBindings().put("value", parameter);
      }
      Object value = OgnlCache.getValue(content, context.getBindings()); //ognl获取值
      String srtValue = (value == null ? "" : String.valueOf(value)); // issue #274 return "" instead of "null"
      checkInjection(srtValue); //检测合法性
      return srtValue;
    }

    private void checkInjection(String value) {
      if (injectionFilter != null && !injectionFilter.matcher(value).matches()) {
        throw new ScriptingException("Invalid input. Please conform to regex" + injectionFilter.pattern());
      }
    }
  }
  
  private static class DynamicCheckerTokenParser implements TokenHandler {

    private boolean isDynamic;

    public DynamicCheckerTokenParser() {
      // Prevent Synthetic Access
    }

    public boolean isDynamic() {
      return isDynamic;
    }

    @Override
    public String handleToken(String content) {
      this.isDynamic = true;
      return null;
    }
  }
  
}