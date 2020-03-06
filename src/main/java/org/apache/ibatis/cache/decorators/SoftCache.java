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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * Soft Reference cache decorator
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 *
 * @author Clinton Begin
 */
public class SoftCache implements Cache { //软引用缓存 TODO
  private final Deque<Object> hardLinksToAvoidGarbageCollection; //最近使用的不会被GC
  private final ReferenceQueue<Object> queueOfGarbageCollectedEntries; //引用队列记录已经被GC回收的缓存项所对应的的实体
  private final Cache delegate;//被装饰的cache
  private int numberOfHardLinks;//强连接个数默认值256

  public SoftCache(Cache delegate) {
    this.delegate = delegate;
    this.numberOfHardLinks = 256;
    this.hardLinksToAvoidGarbageCollection = new LinkedList<Object>();
    this.queueOfGarbageCollectedEntries = new ReferenceQueue<Object>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    removeGarbageCollectedItems();
    return delegate.getSize();
  }


  public void setSize(int size) {
    this.numberOfHardLinks = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    removeGarbageCollectedItems(); //清除已经被GC的项
    delegate.putObject(key, new SoftEntry(key, value, queueOfGarbageCollectedEntries)); //把值加入缓存中
  }

  @Override
  public Object getObject(Object key) { //获取缓存
    Object result = null;
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
    SoftReference<Object> softReference = (SoftReference<Object>) delegate.getObject(key); //获取缓存
    if (softReference != null) { //如果不为空
      result = softReference.get();//通过软引用获取对象
      if (result == null) {//如果对象为空说明被GC了
        delegate.removeObject(key); //移除
      } else { //未被GC回收
        // See #586 (and #335) modifications need more than a read lock 
        synchronized (hardLinksToAvoidGarbageCollection) {
          hardLinksToAvoidGarbageCollection.addFirst(result); //缓存项保存在hardLinksToAvoidGarbageCollection中
          if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) { //如果缓存项里的大于numberOfHardLinks
            hardLinksToAvoidGarbageCollection.removeLast();//移除最后一个
          }
        }
      }
    }
    return result;
  }

  @Override
  public Object removeObject(Object key) {
    removeGarbageCollectedItems();
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    synchronized (hardLinksToAvoidGarbageCollection) {
      hardLinksToAvoidGarbageCollection.clear();
    }
    removeGarbageCollectedItems();
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private void removeGarbageCollectedItems() {
    SoftEntry sv;
    while ((sv = (SoftEntry) queueOfGarbageCollectedEntries.poll()) != null) { //如果被GC队列有值进行移除
      delegate.removeObject(sv.key); //清除缓存项
    }
  }

  private static class SoftEntry extends SoftReference<Object> {
    private final Object key;

    SoftEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      super(value, garbageCollectionQueue); //指向value的引用是软引用且关联队列 被GC会把应用放到queueOfGarbageCollectedEntries中
      this.key = key; //强引用
    }
  }

}