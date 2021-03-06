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
package org.apache.ibatis.cache.decorators;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * Simple blocking decorator 
 * 
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 * 
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache { //阻塞版缓存

  private long timeout; //超时时间
  private final Cache delegate; //被装饰类
  private final ConcurrentHashMap<Object, ReentrantLock> locks; //读写锁集合

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<Object, ReentrantLock>();
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
  public void putObject(Object key, Object value) { //存储数据至缓存
    try {
      delegate.putObject(key, value);
    } finally {
      releaseLock(key); //释放锁
    }
  }

  @Override
  public Object getObject(Object key) { //获取数据
    acquireLock(key); //锁定资源
    Object value = delegate.getObject(key); //获取数据
    if (value != null) { //值不为null释放？？？
      releaseLock(key); //释放锁
    }        
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }
  
  private ReentrantLock getLockForKey(Object key) { //获取锁
    ReentrantLock lock = new ReentrantLock();
    ReentrantLock previous = locks.putIfAbsent(key, lock); //添加锁进入Map中
    return previous == null ? lock : previous;
  }
  
  private void acquireLock(Object key) {
    Lock lock = getLockForKey(key); //获取锁
    if (timeout > 0) { //超时时间大于0
      try {
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS); //带超时参数获取锁
        if (!acquired) {
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " +  key + " at the cache " + delegate.getId());  
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else {
      lock.lock(); //锁定
    }
  }
  
  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key); //从Map中获取锁
    if (lock.isHeldByCurrentThread()) {//如果锁属于当前线程进行释放
      lock.unlock();
    }
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }  
}