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
package calculator.engine;

import java.util.concurrent.CompletableFuture;

/**
 **/
public class TaskNode {
    private String path;
    private CompletableFuture<Object> task;

    /**
     * 虽然可能有多个子节点，但是但是对于某一个node路径、只可能有一个子节点
     *
     * todo：是否考虑使用 parentNode 或者 List<TaskNode> subNodeList，后者应该肯定是不合适的。
     */
    private TaskNode subNode;
}
