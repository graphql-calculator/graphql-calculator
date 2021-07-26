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
import calculator.config.ConfigImpl;
import calculator.engine.ExecutionEngine;
import calculator.graphql.DefaultGraphQLSourceBuilder;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import calculator.engine.SchemaWrapper;
import calculator.engine.script.AviatorScriptEvaluator;
import calculator.validation.Validator;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Map;
import java.util.Objects;

public class FetchSourceTest {
    private static final GraphQLSchema originalSchema = GraphQLSourceHolder.getDefaultSchema();
    private static final Config wrapperConfig = ConfigImpl.newConfig().scriptEvaluator(AviatorScriptEvaluator.getDefaultInstance()).build();
    private static final GraphQLSource graphqlSource = new DefaultGraphQLSourceBuilder().wrapperConfig(wrapperConfig).originalSchema(originalSchema).build();

    @Test
    public void sourceOnAncestorPath_case01() {
        String query = "" +
                "query sourceOnAncestorPath_case01{\n" +
                "    consumer{\n" +
                "        userInfo(userId: 2)\n" +
                "        @fetchSource(name: \"userInfo\")\n" +
                "        {\n" +
                "            userId @fetchSource(name: \"userId\")\n" +
                "        }\n" +
                "    }\n" +
                "    \n" +
                "    commodity{\n" +
                "        item(itemId: 1){\n" +
                "            itemId\n" +
                "            userId: itemId @map(mapper: \"userId\",dependencySources: \"userId\")\n" +
                "            userIdInUserInfo: itemId @map(mapper: \"userInfo.userId\",dependencySources: \"userInfo\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphqlSource.getWrappedSchema(), wrapperConfig);
        assert !validateResult.isFailure();

        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(query);
        assert executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();
        assert Objects.equals(data.get("consumer").get("userInfo").toString(), "{userId=2}");
        assert Objects.equals(data.get("commodity").get("item").toString(), "{itemId=1, userId=2, userIdInUserInfo=2}");
    }

    @Test
    public void sourceOnAncestorPath_case02() {
        String query = "" +
                "query sourceOnAncestorPath_case02{\n" +
                "    consumer{\n" +
                "        userInfo\n" +
                "        @fetchSource(name: \"userInfo\")\n" +
                "        {\n" +
                "            userId @fetchSource(name: \"userId\")\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    commodity{\n" +
                "        item(itemId: 1){\n" +
                "            itemId\n" +
                "            userId: itemId @map(mapper: \"userId\",dependencySources: \"userId\")\n" +
                "            userIdInUserInfo: itemId @map(mapper: \"userInfo == nil?nil:(userInfo.userId)\",dependencySources: \"userInfo\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, graphqlSource.getWrappedSchema(), wrapperConfig);
        assert !validateResult.isFailure();

        ExecutionResult executionResult = graphqlSource.getGraphQL().execute(query);
        assert executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();
        assert Objects.equals(data.get("commodity").get("item").toString(), "{itemId=1, userId=null, userIdInUserInfo=null}");
    }
}
