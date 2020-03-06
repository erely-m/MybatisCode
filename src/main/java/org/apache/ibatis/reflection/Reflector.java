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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector { //反射类的基本单元，每个反射类对应一个该实例

  private final Class<?> type; //对应的class类型
  private final String[] readablePropertyNames; //可读属性的名称集合，可读属性就是存在相应getter方法的属性，初始值为空数纽
  private final String[] writeablePropertyNames;//可写属性对应setter方法属性
  private final Map<String, Invoker> setMethods = new HashMap<String, Invoker>(); //set方法
  private final Map<String, Invoker> getMethods = new HashMap<String, Invoker>(); //get方法
  private final Map<String, Class<?>> setTypes = new HashMap<String, Class<?>>(); //set方法参数类型
  private final Map<String, Class<?>> getTypes = new HashMap<String, Class<?>>(); //get方法参数类型
  private Constructor<?> defaultConstructor;//默认构造方法

  private Map<String, String> caseInsensitivePropertyMap = new HashMap<String, String>();//所有属性名称的集合

  public Reflector(Class<?> clazz) {
    type = clazz; //设置类
    addDefaultConstructor(clazz); //设置默认的构造方法
    addGetMethods(clazz); //设置get方法
    addSetMethods(clazz); //设置set方法
    addFields(clazz);//添加字段
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]); //所有可读属性的集合及get方法
    writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);//所有可写属性集合及set方法
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName); //加入所有属性名称集合
    }
    for (String propName : writeablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName); //加入属性名称集合
    }
  }

  private void addDefaultConstructor(Class<?> clazz) { //添加默认的构造方法
    Constructor<?>[] consts = clazz.getDeclaredConstructors(); //获取构造方法
    for (Constructor<?> constructor : consts) {
      if (constructor.getParameterTypes().length == 0) { //如果构造方法的参数长度为0
        if (canAccessPrivateMethods()) { //
          try {
            constructor.setAccessible(true); //设置构造函数可访问
          } catch (Exception e) {
            // Ignored. This is only a final precaution, nothing we can do.
          }
        }
        if (constructor.isAccessible()) {
          this.defaultConstructor = constructor; //设置默认的构造方法
        }
      }
    }
  }

  private void addGetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingGetters = new HashMap<String, List<Method>>(); //名称和方法列表一个名称可能对应多个方法 多参数
    Method[] methods = getClassMethods(cls); //获取类及父类和接口的所有方法
    for (Method method : methods) {
      if (method.getParameterTypes().length > 0) { //如果方法参数个数大于0返回 说明是set或者其他设置方法
        continue;
      }
      String name = method.getName();
      if ((name.startsWith("get") && name.length() > 3) //如果名称已get* is*开头
          || (name.startsWith("is") && name.length() > 2)) {
        name = PropertyNamer.methodToProperty(name); //通过方法获取属性名
        addMethodConflict(conflictingGetters, name, method); //添加方法进Map
      }
    }
    resolveGetterConflicts(conflictingGetters); //处理get方法
  }

  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) { //迭代获取所有方法信息
      Method winner = null;
      String propName = entry.getKey(); //获取属性名
      for (Method candidate : entry.getValue()) {//获取方法
        if (winner == null) {
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType(); //获取方法返回值
        Class<?> candidateType = candidate.getReturnType();//获取当前方法返回值
        if (candidateType.equals(winnerType)) { //如果两者返回值相同
          if (!boolean.class.equals(candidateType)) {//返回值类型如果不是boolean抛出异常
            throw new ReflectionException(
                "Illegal overloaded getter method with ambiguous type for property "
                    + propName + " in class " + winner.getDeclaringClass()
                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
          } else if (candidate.getName().startsWith("is")) { //如果方法是is*开头的
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
        } else {
          throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
                  + propName + " in class " + winner.getDeclaringClass()
                  + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      addGetMethod(propName, winner); //添加方法值Map
    }
  }

  private void addGetMethod(String name, Method method) { //添加get方法
    if (isValidPropertyName(name)) { //校验属性名
      getMethods.put(name, new MethodInvoker(method));//将方法放入Map集合中
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);//获取返回值 TODO
      getTypes.put(name, typeToClass(returnType));
    }
  }

  private void addSetMethods(Class<?> cls) { //添加set方法
    Map<String, List<Method>> conflictingSetters = new HashMap<String, List<Method>>();
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      String name = method.getName();
      if (name.startsWith("set") && name.length() > 3) { //如果以set*开头的方法
        if (method.getParameterTypes().length == 1) { //参数等于1个说明是set某个属性值
          name = PropertyNamer.methodToProperty(name); //
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    List<Method> list = conflictingMethods.get(name); //获取名字
    if (list == null) {
      list = new ArrayList<Method>();
      conflictingMethods.put(name, list); //创建方法列表并放入Map中
    }
    list.add(method);//添加方法
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) { //处理set方法
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName); //获取属性名
      Class<?> getterType = getTypes.get(propName);//
      Method match = null;
      ReflectionException exception = null;
      for (Method setter : setters) {
        Class<?> paramType = setter.getParameterTypes()[0];
        if (paramType.equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (exception == null) {
          try {
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      if (match == null) {
        throw exception;
      } else {
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
        + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
        + paramType2.getName() + "'.");
  }

  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance((Class<?>) componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields(); //获取类中声明的字段
    for (Field field : fields) {
      if (canAccessPrivateMethods()) { //检查权限
        try {
          field.setAccessible(true);//设置可访问
        } catch (Exception e) {
          // Ignored. This is only a final precaution, nothing we can do.
        }
      }
      if (field.isAccessible()) { //如果可访问
        if (!setMethods.containsKey(field.getName())) { //set方法中是否包含了该字段 如果包含了不处理
          // issue #379 - removed the check for final because JDK 1.5 allows
          // modification of final fields through reflection (JSR-133). (JGB)
          // pr #16 - final static can only be set by the classloader
          int modifiers = field.getModifiers(); //获取字段描述
          if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) { //如果字段不是Final 和static 添加SET字段
            addSetField(field);
          }
        }
        if (!getMethods.containsKey(field.getName())) { //如果get方法里面没有包含该字段
          addGetField(field); //添加get字段
        }
      }
    }
    if (clazz.getSuperclass() != null) { //获取类的父类
      addFields(clazz.getSuperclass()); //添加父类字段
    }
  }

  private void addSetField(Field field) { //添加字段
    if (isValidPropertyName(field.getName())) { //校验字段
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /*
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler Class.getMethods(),
   * because we want to look for private methods as well.
   *
   * @param cls The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> cls) {
    Map<String, Method> uniqueMethods = new HashMap<String, Method>();
    Class<?> currentClass = cls;
    while (currentClass != null) {
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods()); //添加当前类中的方法

      // we also need to look for interface methods -
      // because the class may be abstract
      Class<?>[] interfaces = currentClass.getInterfaces(); //获取接口
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods()); //添加接口类中的方法
      }

      currentClass = currentClass.getSuperclass(); //获取父类
    }

    Collection<Method> methods = uniqueMethods.values(); //获取所有的方法

    return methods.toArray(new Method[methods.size()]); //转换为方法数组返回
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) { //遍历所有方法
      if (!currentMethod.isBridge()) { //是否桥接方法 如果是桥接方法不做处理
        String signature = getSignature(currentMethod); //获取当前方法签名
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        if (!uniqueMethods.containsKey(signature)) {  //如果当前Map没有包含方法
          if (canAccessPrivateMethods()) { //检查是否可以访问私有方法
            try {
              currentMethod.setAccessible(true); //设置方法可访问
            } catch (Exception e) {
              // Ignored. This is only a final precaution, nothing we can do.
            }
          }

          uniqueMethods.put(signature, currentMethod); //存入方法key为签名值，value为方法
        }
      }
    }
  }

  private String getSignature(Method method) { //获取方法签名
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType(); //获取方法返回类型
    if (returnType != null) {
      sb.append(returnType.getName()).append('#'); //返回类型+#
    }
    sb.append(method.getName());//最佳方法名
    Class<?>[] parameters = method.getParameterTypes(); //方法传入参数
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    return sb.toString(); //
  }

  private static boolean canAccessPrivateMethods() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /*
   * Gets the name of the class the instance provides information for
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /*
   * Gets the type for a property setter
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets the type for a property getter
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName); //获取参数类型
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets an array of the readable properties for an object
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /*
   * Gets an array of the writeable properties for an object
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writeablePropertyNames;
  }

  /*
   * Check to see if a class has a writeable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writeable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /*
   * Check to see if a class has a readable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
