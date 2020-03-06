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
package org.apache.ibatis.binding;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
//封装Mapper接口以及对应的映射文件中的sql
public class MapperMethod {

  private final SqlCommand command; //Sql语句
  private final MethodSignature method; //Mapper中方法的相关信息

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) { //根据SQL类型执行不同的操作
      case INSERT: {//
    	Object param = method.convertArgsToSqlCommandParam(args); //处理传入参数
        result = rowCountResult(sqlSession.insert(command.getName(), param)); //执行insert语句
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT: //处理Select语句
        if (method.returnsVoid() && method.hasResultHandler()) { //处理返回值为空确通过ResultHandler处理的方法
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) { //数组或者Collections类型
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) { //Map类型
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {//Cursor类型
          result = executeForCursor(sqlSession, args);
        } else {
          Object param = method.convertArgsToSqlCommandParam(args); //获取参数
          result = sqlSession.selectOne(command.getName(), param);
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements(); //刷新
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName() 
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) { //返回值是否为Void
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) { //返回值类型是Integer
      result = rowCount; //返回数量
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) { //返回值类型为Long
      result = (long)rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) { //返回值类型为Boolean
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());//获取对应的SQL语句 通过sql名
    if (void.class.equals(ms.getResultMaps().get(0).getType())) { //如果返回值类型为void 抛出异常
      throw new BindingException("method " + command.getName() 
          + " needs either a @ResultMap annotation, a @ResultType annotation," 
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    Object param = method.convertArgsToSqlCommandParam(args); //将传入的参数转换为 SQL对应的采纳数列表
    if (method.hasRowBounds()) {
      //如果是RowBounds对象 获取对应的参数
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args)); //执行
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));//执行
    }
  }

  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args); //转换参数
    if (method.hasRowBounds()) { //如果指定了RowBounds类型
      RowBounds rowBounds = method.extractRowBounds(args); //
      result = sqlSession.<E>selectList(command.getName(), param, rowBounds); //查询
    } else {
      result = sqlSession.<E>selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.<T>selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.<T>selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (arrayComponentType.isPrimitive()) {
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    } else {
      return list.toArray((E[])array);
    }
  }

  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  public static class SqlCommand {

    private final String name; //Sql语句名称
    private final SqlCommandType type; //sql类型

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      final String methodName = method.getName();//获取方法名
      final Class<?> declaringClass = method.getDeclaringClass(); //获取方法所属类
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
          configuration); //获取语句
      if (ms == null) { //语句为空
        if (method.getAnnotation(Flush.class) != null) { //但是有注解 @Flush
          name = null;
          type = SqlCommandType.FLUSH; //设置SQL语句类型
        } else {
          throw new BindingException("Invalid bound statement (not found): "
              + mapperInterface.getName() + "." + methodName);
        }
      } else {//初始化name和type
        name = ms.getId();
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
        Class<?> declaringClass, Configuration configuration) {
      String statementId = mapperInterface.getName() + "." + methodName; //SQL语句名称等于接口名.方法名
      if (configuration.hasStatement(statementId)) {//检测是否已经有该名称的SQL
        return configuration.getMappedStatement(statementId); //返回已有的语句
      } else if (mapperInterface.equals(declaringClass)) { //如果接口等于类直接返回
        return null;
      }
      for (Class<?> superInterface : mapperInterface.getInterfaces()) { //获取接口实现接口
        if (declaringClass.isAssignableFrom(superInterface)) { //当前类是否继承了该接口如果是
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
              declaringClass, configuration); //递归处理Map
          if (ms != null) { //如果MappedStatement不为空则返回
            return ms;
          }
        }
      }
      return null;
    }
  }

  public static class MethodSignature {

    private final boolean returnsMany;//返回值是否是集合或者是数组
    private final boolean returnsMap;//返回值是否是map类型
    private final boolean returnsVoid;//返回值是否为void
    private final boolean returnsCursor;//返回值是否为Cursor类型
    private final Class<?> returnType;//返回值类型
    private final String mapKey;//如果返回值类型是Map，则该字段记录了作为key 的列名
    private final Integer resultHandlerIndex;//用来标记该方法参数列表中ResultHandler 类型参数的位置
    private final Integer rowBoundsIndex;//用来标记该方法参数列表中RowBounds 类型参数的位置
    private final ParamNameResolver paramNameResolver;//该方法对应的ParamNameResolver对象

    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface); //获取方法的返回值类型
      if (resolvedReturnType instanceof Class<?>) { //如果是Class
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }
      this.returnsVoid = void.class.equals(this.returnType); //方法的返回值类型如果等于void
      this.returnsMany = (configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray()); //返回如果是Array或者Collection
      this.returnsCursor = Cursor.class.equals(this.returnType); //返回值是Cursor
      this.mapKey = getMapKey(method); //获取MapKey注解值
      this.returnsMap = (this.mapKey != null); //如果mapKey不空说明返回值类型是Map
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class); //初始化
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      this.paramNameResolver = new ParamNameResolver(configuration, method); //初始化ParamNameResolver对象
    }

    public Object convertArgsToSqlCommandParam(Object[] args) { //负责将args 数组（ 用户传入的实参列表）转换成SQL 语句对应的参数列表
      return paramNameResolver.getNamedParams(args);
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public String getMapKey() {
      return mapKey;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    private Integer getUniqueParamIndex(Method method, Class<?> paramType) { //查找指定类型参数在参数列表中的位置
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes(); //获取方法所有的参数类型
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) { //如果paramType是参数类型记录下标
          if (index == null) {
            index = i;
          } else {//ResultHandler和RowBounds两个参数只能出现一次
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    private String getMapKey(Method method) { //获取MapKey注解
      String mapKey = null;
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value(); //获取注解值
        }
      }
      return mapKey;
    }
  }

}
