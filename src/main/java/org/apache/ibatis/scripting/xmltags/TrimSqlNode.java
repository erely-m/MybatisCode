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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class TrimSqlNode implements SqlNode {

  private final SqlNode contents; // trim节点的子节点
  private final String prefix; //记录前缀字符串
  private final String suffix; //记录后缀字符串
  private final List<String> prefixesToOverride;//如果<trim>节点包含的是空语句则删除指定的前缀 如Where ？？TODO
  private final List<String> suffixesToOverride; //如果<trim>节点包含的是空语句则删除指定的后缀如逗号
  private final Configuration configuration;//全局Configuration属性

  public TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, String prefixesToOverride, String suffix, String suffixesToOverride) {
    this(configuration, contents, prefix, parseOverrides(prefixesToOverride), suffix, parseOverrides(suffixesToOverride));
  }

  protected TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, List<String> prefixesToOverride, String suffix, List<String> suffixesToOverride) {
    this.contents = contents;
    this.prefix = prefix;
    this.prefixesToOverride = prefixesToOverride;
    this.suffix = suffix;
    this.suffixesToOverride = suffixesToOverride;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
    FilteredDynamicContext filteredDynamicContext = new FilteredDynamicContext(context);
    boolean result = contents.apply(filteredDynamicContext);//调用子节点的解析方法
    filteredDynamicContext.applyAll();
    return result;
  }

  private static List<String> parseOverrides(String overrides) { //处理分割条件
    if (overrides != null) {
      final StringTokenizer parser = new StringTokenizer(overrides, "|", false); //按照 | 进行拆分
      final List<String> list = new ArrayList<String>(parser.countTokens());
      while (parser.hasMoreTokens()) {
        list.add(parser.nextToken().toUpperCase(Locale.ENGLISH)); //转换为大写并添加到集合中
      }
      return list;
    }
    return Collections.emptyList();
  }

  private class FilteredDynamicContext extends DynamicContext {
    private DynamicContext delegate; //DynamicContext属性
    private boolean prefixApplied;//是否处理过前缀
    private boolean suffixApplied;//是否处理过后缀
    private StringBuilder sqlBuffer;//记录解析结果

    public FilteredDynamicContext(DynamicContext delegate) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefixApplied = false;
      this.suffixApplied = false;
      this.sqlBuffer = new StringBuilder();
    }

    public void applyAll() {
      sqlBuffer = new StringBuilder(sqlBuffer.toString().trim()); //获取子节点解析的Buffer
      String trimmedUppercaseSql = sqlBuffer.toString().toUpperCase(Locale.ENGLISH); //转换 为大写
      if (trimmedUppercaseSql.length() > 0) {//如果长度大于0
        applyPrefix(sqlBuffer, trimmedUppercaseSql); //处理前缀
        applySuffix(sqlBuffer, trimmedUppercaseSql);//处理后缀
      }
      delegate.appendSql(sqlBuffer.toString()); //将解析的结果加入上下文中
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

    @Override
    public void appendSql(String sql) {
      sqlBuffer.append(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    private void applyPrefix(StringBuilder sql, String trimmedUppercaseSql) {
      if (!prefixApplied) { //是否已经处理
        prefixApplied = true;
        if (prefixesToOverride != null) {
          for (String toRemove : prefixesToOverride) { //遍历所有的前缀集合
            if (trimmedUppercaseSql.startsWith(toRemove)) {//如果sql是以某项开头则去除
              sql.delete(0, toRemove.trim().length());
              break;
            }
          }
        }
        if (prefix != null) { //添加前缀 空格
          sql.insert(0, " ");
          sql.insert(0, prefix);
        }
      }
    }

    private void applySuffix(StringBuilder sql, String trimmedUppercaseSql) {
      if (!suffixApplied) {
        suffixApplied = true;
        if (suffixesToOverride != null) {
          for (String toRemove : suffixesToOverride) {
            if (trimmedUppercaseSql.endsWith(toRemove) || trimmedUppercaseSql.endsWith(toRemove.trim())) {
              int start = sql.length() - toRemove.trim().length();
              int end = sql.length();
              sql.delete(start, end);
              break;
            }
          }
        }
        if (suffix != null) { //添加后缀空格
          sql.append(" ");
          sql.append(suffix);
        }
      }
    }

  }

}
