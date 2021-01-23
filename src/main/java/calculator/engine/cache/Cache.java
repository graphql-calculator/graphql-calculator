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

import graphql.ExecutionResult;

public interface Cache<K, V, R extends ExecutionResult> {

    String getName();

    // 对错误进行处理
    V get(K key, GraphQLErrorsHandle<K, V, R> errorsHandle);

    // V包含错误
    V get(K key);

    // 执行一次实际的请求
    R load(K key);
}
