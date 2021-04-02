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

package calculator;

import static calculator.directives.CalculateSchemaHolder.getCalSchema;
import static calculator.engine.ExecutionEngineWrapper.getEngineWrapper;
import static com.googlecode.aviator.AviatorEvaluator.execute;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.Test;

import calculator.config.ConfigImpl;
import calculator.engine.Wrapper;
import calculator.engine.function.FindOneFunction;
import calculator.engine.function.NodeFunction;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;

public class Issues {

    private static ConfigImpl scheduleConfig = ConfigImpl.newConfig()
            // 是否需要支持调度
            .isScheduleEnabled(true)
            // todo 这两个应该是自动添加的
            .function(new NodeFunction())
            .function(new FindOneFunction())
            .build();
    private static GraphQLSchema wrappedSchema = Wrapper.wrap(scheduleConfig, getCalSchema());

    @Test
    public void threadBlockOnScheduleThread() {
        GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema).instrumentation(getEngineWrapper()).build();
        String query = "" +
                "query($userIds: [Int]){\n" +
                "    itemList(ids: 1)@link(argument:\"ids\",node:\"itemIds\"){\n" +
                "        itemId\n" +
                "        name\n" +
                "    }\n" +
                "    userInfoList(ids:$userIds){\n" +
                "        userId  \n" +
                "        name\n" +
                "        favoriteItemId @node(name:\"itemIds\")\n" +
                "    }\n" +
                "}";

        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userIds", Arrays.asList(1, 2, 3))).build();
        ExecutionResult result = graphQL.execute(input);
        assert result != null;
        assert result.getErrors().isEmpty();
    }
}