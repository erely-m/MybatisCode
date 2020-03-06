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

import java.util.Map;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class ForEachSqlNode implements SqlNode { //处理foreach
  public static final String ITEM_PREFIX = "__frch_"; //指定前缀

  private final ExpressionEvaluator evaluator; //用于判断循环的终止条件
  private final String collectionExpression; //迭代集合表达式
  private final SqlNode contents; //记录该foreach的子节点
  private final String open;//循环开始加入的字符串
  private final String close; //循环结束添加的字符串
  private final String separator; //每项之间的分隔符
  //index 是当前迭代的次数， item 的值是本次选代的元素。若迭代集合是Map ，则index 是键， item 是值
  private final String item;
  private final String index;
  private final Configuration configuration;  //全局配置

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
    Map<String, Object> bindings = context.getBindings(); //获取上下文中所有参数
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);//解析集合表达式对应的参数
    if (!iterable.iterator().hasNext()) {
      return true;
    }
    boolean first = true;
    applyOpen(context); //追加前缀
    int i = 0;
    for (Object o : iterable) { //依次迭代
      DynamicContext oldContext = context; //获取原context
      if (first || separator == null) { //创建PrefixedContext上下文装饰类
        context = new PrefixedContext(context, "");
      } else {
        context = new PrefixedContext(context, separator);
      }
      int uniqueNumber = context.getUniqueNumber(); //获取唯一序列号 用于生成新的占位符
      // Issue #709 
      if (o instanceof Map.Entry) { //如果类型为Map
        @SuppressWarnings("unchecked") 
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      context = oldContext;
      i++;
    }
    applyClose(context); //追加后缀
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      context.bind(index, o);//key 为index, value 是集合元素
      context.bind(itemizeItem(index, i), o); //为key添加前缀形成新的key
    }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
      context.bind(item, o);//key 为item , value 是集合项
      context.bind(itemizeItem(item, i), o);//为key添加前缀
    }
  }

  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open); //追加前缀至上下文
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  private static String itemizeItem(String item, int i) { //添加ITEM_PREFIX前缀以及后缀
    return new StringBuilder(ITEM_PREFIX).append(item).append("_").append(i).toString();
  }

  private static class FilteredDynamicContext extends DynamicContext {
    private final DynamicContext delegate; //被装饰的底层DynamicContext
    private final int index;//对应集合项在集合中的索引位置
    private final String itemIndex; ///对应集合项的index
    private final String item;//对应集合项的item

    public FilteredDynamicContext(Configuration configuration,DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
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
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
      GenericTokenParser parser = new GenericTokenParser("#{", "}", new TokenHandler() { //处理占位符
        @Override
        public String handleToken(String content) {
          String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
          if (itemIndex != null && newContent.equals(content)) {
            newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
          }
          return new StringBuilder("#{").append(newContent).append("}").toString(); //将原始值替换为#{__frch_item_1}
        }
      });

      delegate.appendSql(parser.parse(sql)); //将解析后的SQL追加进上下文中
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }


  private class PrefixedContext extends DynamicContext { //上下文装饰类
    private final DynamicContext delegate; //被装饰的基础DynamicContext
    private final String prefix; //指定的前缀
    private boolean prefixApplied;//是否处理了前缀

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
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
    public void appendSql(String sql) {
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        delegate.appendSql(prefix); //追加前缀
        prefixApplied = true;
      }
      delegate.appendSql(sql);//追加sql
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
