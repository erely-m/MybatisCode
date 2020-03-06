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
package org.apache.ibatis.session;

import java.sql.Connection;

/**
 * @author Clinton Begin
 */
public enum TransactionIsolationLevel {
  NONE(Connection.TRANSACTION_NONE),
  READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED), //保证一个事务修改的数据提交后才能被另外一个事务读取。另外一个事务不能读取该事务未提交的数据
  READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED), //这是事务最低的隔离级别，它充许令外一个事务可以看到这个事务未提交的数据
  REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),//这种事务隔离级别可以防止脏读，不可重复读。但是可能出现幻像读。
  SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);//这是花费最高代价但是最可靠的事务隔离级别。事务被处理为顺序执行。

  private final int level;

  private TransactionIsolationLevel(int level) {
    this.level = level;
  }

  public int getLevel() {
    return level;
  }
}
