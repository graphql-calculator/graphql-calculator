/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package calculator.engine.cache;

/**
 * @param <K> 缓存key
 * @param <V> 缓存value
 */
public interface Cache<K, V> {

    String getName();

    /**
     * 缓存没有数据、或者数据失效的时候，使用loader加载数据(
     *
     * @return 加载结果，可能为null（@Nullable）
     */
    CacheLoader<K, V> getCacheLoader();

    //  get抛出异常时，对异常的处理
    CacheErrorsHandle<K> getCacheErrorsHandle();

    // 对错误进行处理
    V get(K key);
}
