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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * The 2nd level cache transactional buffer.
 * 
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back. 
 * Blocking cache support has been added. Therefore any get() that returns a cache miss 
 * will be followed by a put() so any lock associated with the key can be released. 
 * 
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  private final Cache delegate;//底层cache对象
  private boolean clearOnCommit;//当该字段为true 时，则表示当前TransactionalCache 不可查询， 且提交事务时会将底层Cache 清空
  private final Map<Object, Object> entriesToAddOnCommit; //暂时记录添加到TransactionalCache 中的数据。在事务提交时，会将其中的数据添加到二级後存中
  private final Set<Object> entriesMissedInCache; //记录缓存未命中的CacheKey对象

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<Object, Object>();
    this.entriesMissedInCache = new HashSet<Object>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // issue #116
    Object object = delegate.getObject(key); //查询二级缓存
    if (object == null) { //没有查询到添加进entriesMissedInCache中
      entriesMissedInCache.add(key);
    }
    // issue #146
    if (clearOnCommit) {//如采clearOnCommit 为true ，则当前TransactionalCache 不可查询，始终返回null
      return null;
    } else {
      return object; //返回查询的对象
    }
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  @Override
  public void putObject(Object key, Object object) { //暂存查询到的数据
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  public void commit() {
    if (clearOnCommit) {//是否清空二级缓存
      delegate.clear();//
    }
    flushPendingEntries(); //将暂存的数据放入二级缓存中
    reset();//重直clearOnCommit 为false ，并清空entriesToAddOnCommit 、entriesMissedinCache 集合
  }

  public void rollback() {
    unlockMissedEntries();
    reset(); //重置
  }

  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) { //查询暂存的二级缓存
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) { //查询没有查到的key
      if (!entriesToAddOnCommit.containsKey(entry)) { //如果暂存的二级缓存中不包含 将其存入二级缓存 返回值为null
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) { //
      try {
        delegate.removeObject(entry); //移除entriesMissedInCache中的记录
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter."
            + "Consider upgrading your cache adapter to the latest version.  Cause: " + e);
      }
    }
  }

}
