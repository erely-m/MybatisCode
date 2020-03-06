/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public interface ObjectWrapper {//对象包装类

  Object get(PropertyTokenizer prop); //获取对象

  void set(PropertyTokenizer prop, Object value);//设置对象

  String findProperty(String name, boolean useCamelCaseMapping);//通过名称，以及是否忽略表达中的下划线查找属性

  String[] getGetterNames(); //所有可读属性集合

  String[] getSetterNames(); //所有可写属性集合

  Class<?> getSetterType(String name);//通过属性表达式获取set方法的参数类型

  Class<?> getGetterType(String name);//通过属性表达式获取get方法的参数类型

  boolean hasSetter(String name); //指定属性是否有set方法

  boolean hasGetter(String name);//指定属性是否有get方法

  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory); //为表达式创建MetaClass
  
  boolean isCollection(); //是否是集合类
  
  void add(Object element);//添加元素至集合
  
  <E> void addAll(List<E> element); //添加元素至集合

}
