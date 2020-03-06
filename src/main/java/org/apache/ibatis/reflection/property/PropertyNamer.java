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

import java.util.Locale;

import org.apache.ibatis.reflection.ReflectionException;

/**
 * @author Clinton Begin
 */
public final class PropertyNamer { //

  private PropertyNamer() {
    // Prevent Instantiation of Static Class
  }

  public static String methodToProperty(String name) { //方法名到属性的转换
    if (name.startsWith("is")) { //从is后截取
      name = name.substring(2);
    } else if (name.startsWith("get") || name.startsWith("set")) { //从get 、set后截取
      name = name.substring(3);
    } else {
      throw new ReflectionException("Error parsing property name '" + name + "'.  Didn't start with 'is', 'get' or 'set'.");
    }

    if (name.length() == 1 || (name.length() > 1 && !Character.isUpperCase(name.charAt(1)))) { //名字长度大于1并且首字母大写
      name = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1); //将名称首字母变为小写重新拼接
    }

    return name;
  }

  public static boolean isProperty(String name) { //检测方法是否对应属性
    return name.startsWith("get") || name.startsWith("set") || name.startsWith("is");
  }

  public static boolean isGetter(String name) { //检查是不是为get方法
    return name.startsWith("get") || name.startsWith("is");
  }

  public static boolean isSetter(String name) { //检测是否为set方法
    return name.startsWith("set");
  }

}
