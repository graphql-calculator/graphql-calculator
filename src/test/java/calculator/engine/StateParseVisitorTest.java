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
import calculator.engine.metadata.WrapperState;
import graphql.analysis.QueryTraverser;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static calculator.engine.TestUtil.listsWithSameElements;
import static calculator.engine.CalculateSchemaHolder.getCalSchema;

public class StateParseVisitorTest {

    private static ConfigImpl baseConfig = ConfigImpl.newConfig().build();


    @Test
    public void testParseNestedNodeListTask() {
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

        WrapperState state = new WrapperState();
        StateParser visitor = StateParser.newInstanceWithState(state);
        traverser.visitDepthFirst(visitor);

        assert state.getTaskByPath().size() == 6;
        assert state.getSequenceTaskByNode().size() == 3;
        assert state.getSequenceTaskByNode().get("itemCIdS").equals(Arrays.asList("item#couponList", "item#couponList#couponId"));
        assert listsWithSameElements(state.getSequenceTaskByNode().get("itemLimitationList"),(Arrays.asList("item#couponList", "item#couponList#limitation")));
        assert state.getSequenceTaskByNode().get("itemListCIdS").equals(Arrays.asList("itemList", "itemList#couponList", "itemList#couponList#couponId"));

        assert state.getTaskByPath().get("itemList").getSubTaskList().size() == 1;
        assert state.getTaskByPath().get("itemList#couponList").getSubTaskList().size() == 1;
        assert state.getTaskByPath().get("item#couponList").getSubTaskList().size() == 2;
    }

}
