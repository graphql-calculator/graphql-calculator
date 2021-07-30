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

import calculator.config.ConfigImpl;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import calculator.validation.Validator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ParseAndValidateResult;
import graphql.schema.DataFetcher;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class SkipAndIncludeExtendTest {


    @Test
    public void skipBy_case01() {
        Map<String, Map<String, DataFetcher>> dataFetcherInfoMap = GraphQLSourceHolder.defaultDataFetcherInfo();
        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(dataFetcherInfoMap);

        String query = "" +
                "query skipBy_case01($userId:Int){\n" +
                "    consumer{\n" +
                "        userInfo(userId: $userId)\n" +
                "        # the userInfo field would not be queried if 'userId>100' is true\n" +
                "        @skipBy(predicate: \"userId>100\")\n" +
                "        {\n" +
                "            userId\n" +
                "            name\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), ConfigImpl.newConfig().build());
        assert !validateResult.isFailure();

        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 3)).build();
        ExecutionResult executionResult = graphQLSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();
        assert Objects.equals(
                executionResult.getData().toString(),
                "{consumer={userInfo={userId=3, name=3_name}}}"
        );

        ExecutionInput skipInput = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 1001)).build();
        ExecutionResult skipResult = graphQLSource.getGraphQL().execute(skipInput);
        assert skipResult.getErrors().isEmpty();
        assert Objects.equals(
                skipResult.getData().toString(),
                "{consumer={userInfo=null}}"
        );
    }


    @Test
    public void includeBy_case01() {
        Map<String, Map<String, DataFetcher>> dataFetcherInfoMap = GraphQLSourceHolder.defaultDataFetcherInfo();
        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(dataFetcherInfoMap);

        String query = "" +
                "query includeBy_case01($userId:Int){\n" +
                "    consumer{\n" +
                "        userInfo(userId: $userId)\n" +
                "        # the userInfo field will be queried if 'userId>100' is true\n" +
                "        @includeBy(predicate: \"userId>100\")\n" +
                "        {\n" +
                "            userId\n" +
                "            name\n" +
                "        }\n" +
                "    }\n" +
                "}";
        System.out.println(query);
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), ConfigImpl.newConfig().build());
        assert !validateResult.isFailure();

        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 3)).build();
        ExecutionResult executionResult = graphQLSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();
        assert Objects.equals(
                executionResult.getData().toString(),
                "{consumer={userInfo=null}}"
        );

        ExecutionInput skipInput = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 1001)).build();
        ExecutionResult skipResult = graphQLSource.getGraphQL().execute(skipInput);
        assert skipResult.getErrors().isEmpty();
        assert Objects.equals(
                skipResult.getData().toString(),
                "{consumer={userInfo={userId=1001, name=1001_name}}}"
        );
    }


    @Test
    public void simpleIncludeBy_case01() {
        Map<String, Map<String, DataFetcher>> dataFetcherInfoMap = GraphQLSourceHolder.defaultDataFetcherInfo();
        GraphQLSource graphQLSource = GraphQLSourceHolder.getGraphQLByDataFetcherMap(dataFetcherInfoMap);

        String query = "" +
                "query simpleIncludeBy_case01($userId:Int){\n" +
                "    consumer{\n" +
                "        userInfo(userId: $userId){\n" +
                "            userId\n" +
                // the userId used by includeBy is '$userId', instead of userId in userInfo.
                "            name @includeBy(predicate: \"userId!=2\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphQLSource.getWrappedSchema(), ConfigImpl.newConfig().build());
        assert !validateResult.isFailure();

        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 1)).build();
        ExecutionResult executionResult = graphQLSource.getGraphQL().execute(input);
        assert executionResult.getErrors().isEmpty();
        assert Objects.equals(
                executionResult.getData().toString(),
                "{consumer={userInfo={userId=1, name=1_name}}}"
        );

        ExecutionInput skipInput = ExecutionInput.newExecutionInput(query).variables(Collections.singletonMap("userId", 2)).build();
        ExecutionResult skipResult = graphQLSource.getGraphQL().execute(skipInput);
        assert skipResult.getErrors().isEmpty();
        assert Objects.equals(
                skipResult.getData().toString(),
                "{consumer={userInfo={userId=2, name=null}}}"
        );
    }
}
