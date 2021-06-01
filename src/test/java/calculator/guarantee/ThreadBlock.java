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

package calculator.guarantee;

import static calculator.engine.CalculateSchemaHolder.getCalSchema;
import static calculator.engine.ExecutionEngine.newInstance;

import java.util.Arrays;
import java.util.Collections;

import calculator.config.Config;
import calculator.engine.ExecutionEngine;
import org.junit.Test;

import calculator.engine.SchemaWrapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;

public class ThreadBlock {
    private static GraphQLSchema wrappedSchema = SchemaWrapper.wrap(Config.DEFAULT_CONFIG, getCalSchema());
    private static ExecutionEngine executionEngine = newInstance(Config.DEFAULT_CONFIG);
    private static GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema)
            .instrumentation(executionEngine)
            .build();
    @Test
    public void threadBlockOnScheduleThread() {
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