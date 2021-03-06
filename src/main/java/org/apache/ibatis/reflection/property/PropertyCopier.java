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
package org.apache.ibatis.reflection.property;

import java.lang.reflect.Field;

/**
 * @author Clinton Begin
 */
public final class PropertyCopier { //属性copy工具

  private PropertyCopier() {
    // Prevent Instantiation of Static Class
  }

  public static void copyBeanProperties(Class<?> type, Object sourceBean, Object destinationBean) {//
    Class<?> parent = type;//
    while (parent != null) {
      final Field[] fields = parent.getDeclaredFields(); //获取所有字段
      for(Field field : fields) {
        try {
          field.setAccessible(true); //设置可访问性
          field.set(destinationBean, field.get(sourceBean)); //copy对象的属性值
        } catch (Exception e) {
          // Nothing useful to do, will only fail on final fields, which will be ignored.
        }
      }
      parent = parent.getSuperclass(); //继续拷贝父类中的字段
    }
  }

}
