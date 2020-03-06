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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  private String name; //当前表达式的名称
  private final String indexedName; //当前表达式的索引名
  private String index;//索引下标
  private final String children;//子表达式

  public PropertyTokenizer(String fullname) {
    int delim = fullname.indexOf('.'); //查找第一个.的位置
    if (delim > -1) { //如果存在
      name = fullname.substring(0, delim); //初始化name
      children = fullname.substring(delim + 1);//初始化children
    } else { //否则
      name = fullname; //
      children = null;//孩子为空
    }
    indexedName = name;//索引名为name
    delim = name.indexOf('[');//查找名字[的位置
    if (delim > -1) {//
      index = name.substring(delim + 1, name.length() - 1); //设置下标值
      name = name.substring(0, delim);//设置名字
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
