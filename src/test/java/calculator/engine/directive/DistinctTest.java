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

package calculator.engine.directive;

import calculator.config.Config;
import calculator.config.DefaultConfig;
import calculator.engine.script.AviatorScriptEvaluator;
import calculator.graphql.DefaultGraphQLSourceBuilder;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import calculator.validation.Validator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class DistinctTest {

    private static final GraphQLSchema originalSchema = GraphQLSourceHolder.getDefaultSchema();
    private static final Config wrapperConfig = DefaultConfig.newConfig().scriptEvaluator(AviatorScriptEvaluator.getDefaultInstance()).build();
    private static final GraphQLSource graphqlSource = new DefaultGraphQLSourceBuilder().wrapperConfig(wrapperConfig).originalSchema(originalSchema).build();


    @Test
    public void simpleTest_case01() {
        String query = "" +
                "query distinctSimpleCase_01($userIds:[Int]){\n" +
                "    consumer{\n" +
                "        distinctUserInfoList: userInfoList(userIds: $userIds)\n" +
                "        @distinct(comparator: \"age\")\n" +
                "        {\n" +
                "            userId\n" +
                "            age\n" +
                "        }\n" +
                "\n" +
                "        userInfoList(userIds: $userIds)\n" +
                "        {\n" +
                "            userId\n" +
                "            age\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(
                query, graphqlSource.getWrappedSchema(), DefaultConfig.newConfig().build()
        );
        assert !validateResult.isFailure();

        ExecutionInput input = ExecutionInput.newExecutionInput(query)
                .variables(Collections.singletonMap("userIds", Arrays.asList(3, 2, 1, 4, 1, 5, 3, 0, 0)))
                .build();

        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();

        assert Objects.equals(
                data.get("consumer").get("distinctUserInfoList").toString(),
                "[{userId=3, age=30}, {userId=2, age=20}, {userId=1, age=10}, {userId=4, age=40}, {userId=5, age=50}, null]"
        );

        assert Objects.equals(
                data.get("consumer").get("userInfoList").toString(),
                "[{userId=3, age=30}, {userId=2, age=20}, {userId=1, age=10},"
                        + " {userId=4, age=40}, {userId=1, age=10}, {userId=5, age=50}, {userId=3, age=30}, null, null]"
        );
    }

    @Test
    public void distinctSimpleCase_notSetComparator() {
        String query = "" +
                "query distinctSimpleCase_notSetComparator($userIds:[Int]){\n" +
                "    consumer{\n" +
                "        distinctUserInfoList: userInfoList(userIds: $userIds)\n" +
                "        @distinct\n" +
                "        {\n" +
                "            userId\n" +
                "            age\n" +
                "        }\n" +
                "\n" +
                "        userInfoList(userIds: $userIds)\n" +
                "        {\n" +
                "            userId\n" +
                "            age\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(
                query, graphqlSource.getWrappedSchema(), DefaultConfig.newConfig().build()
        );
        assert !validateResult.isFailure();

        ExecutionInput input = ExecutionInput.newExecutionInput(query)
                .variables(Collections.singletonMap("userIds", Arrays.asList(3, 2, 1, 4, 1, 5, 3, 0, 0)))
                .build();

        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();

        System.out.println(data.get("consumer").get("distinctUserInfoList"));
        System.out.println(data.get("consumer").get("userInfoList"));

        assert Objects.equals(
                data.get("consumer").get("distinctUserInfoList").toString(),
                "[{userId=3, age=30}, {userId=2, age=20}, {userId=1, age=10}, {userId=4, age=40}, "
                        + "{userId=1, age=10}, {userId=5, age=50}, {userId=3, age=30}, null]"
        );

        assert Objects.equals(
                data.get("consumer").get("userInfoList").toString(),
                "[{userId=3, age=30}, {userId=2, age=20}, {userId=1, age=10}, {userId=4, age=40}, "
                        + "{userId=1, age=10}, {userId=5, age=50}, {userId=3, age=30}, null, null]"
        );
    }
}
