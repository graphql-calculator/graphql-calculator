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

import calculator.config.ConfigImpl;
import calculator.engine.metadata.NodeTask;
import graphql.analysis.QueryTraverser;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static calculator.engine.TestUtil.listsWithSameElements;
import static calculator.engine.SchemaHolder.getCalSchema;

public class EngineStateParserTest {

    private static ConfigImpl baseConfig = ConfigImpl.newConfig().build();


    // 解析任务: todo 父亲节点都要解析
    @Test
    public void parseNestedNodeListTaskTest() {
        String query = "query {\n" +
                "    item{\n" +
                "        couponList{\n" +
                "            # 预期不将 item 注册为任务\n" +
                "            couponId @node(name: \"itemCIdS\")\n" +
                "            limitation @node(name: \"itemLimitationList\")\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    itemList{\n" +
                "        couponList{\n" +
                "            # 全路径三个节点都会注册为任务\n" +
                "            couponId @node(name:\"itemListCIdS\")\n" +
                "        }\n" +
                "    }\n" +
                "}";

        GraphQLSchema wrappedSchema = SchemaWrapper.wrap(baseConfig, getCalSchema());

        QueryTraverser traverser = QueryTraverser.newQueryTraverser()
                .schema(wrappedSchema)
                .document(Parser.parse(query))
                .variables(Collections.emptyMap()).build();

        ExecutionEngineStateParser visitor = new ExecutionEngineStateParser();
        traverser.visitDepthFirst(visitor);
        ExecutionEngineState executionEngineState = visitor.getExecutionEngineState();

        assert executionEngineState.getTaskByPath().size() == 6;
        assert executionEngineState.getSequenceTaskByNode().size() == 3;
        assert executionEngineState.getSequenceTaskByNode().get("itemCIdS").equals(Arrays.asList("item#couponList", "item#couponList#couponId"));
        assert listsWithSameElements(executionEngineState.getSequenceTaskByNode().get("itemLimitationList"), (Arrays.asList("item#couponList", "item#couponList#limitation")));
        assert executionEngineState.getSequenceTaskByNode().get("itemListCIdS").equals(Arrays.asList("itemList", "itemList#couponList", "itemList#couponList#couponId"));

        assert executionEngineState.getTaskByPath().get("itemList").getSubTaskList().size() == 1;
        assert executionEngineState.getTaskByPath().get("itemList#couponList").getSubTaskList().size() == 1;
        assert executionEngineState.getTaskByPath().get("item#couponList").getSubTaskList().size() == 2;
    }


    @Test(expected = UnsupportedOperationException.class)
    public void unModifyExecutionStateTest() {
        ExecutionEngineState.Builder builder = ExecutionEngineState.newExecutionState();
        builder.sequenceTaskByNode("itemId", Arrays.asList("itemInfo.itemId"));
        ExecutionEngineState engineState = builder.build();

        Map<String, List<String>> sequenceTaskByNode = engineState.getSequenceTaskByNode();
        sequenceTaskByNode.put("newKey", Arrays.asList("ele_1", "ele_2"));
    }


    @Test(expected = UnsupportedOperationException.class)
    public void unModifyExecutionStateTest2() {
        ExecutionEngineState.Builder builder = ExecutionEngineState.newExecutionState();
        NodeTask task = NodeTask.newBuilder()
                .future(CompletableFuture.completedFuture(1991))
                .isTopTaskNode(false)
                .isList(false)
                .path("itemInfo.itemId")
                .isAnnotated(true)
                .build();
        builder.taskByPath("itemInfo.itemId", task);
        ExecutionEngineState engineState = builder.build();

        NodeTask nodeTask = NodeTask.newBuilder()
                .isTopTaskNode(false)
                .isList(false)
                .path("path")
                .isAnnotated(false)
                .future(new CompletableFuture<>())
                .build();
        Map<String, NodeTask> taskByPath = engineState.getTaskByPath();
        taskByPath.put("newTask", nodeTask);
    }

}
