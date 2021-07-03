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
import calculator.engine.SchemaHolder;
import calculator.engine.SchemaWrapper;
import calculator.function.ListContain;
import calculator.validate.Validator;
import com.googlecode.aviator.AviatorEvaluator;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Map;
import java.util.Objects;

public class FetchSourceTest {
    private static final GraphQLSchema originalSchema = SchemaHolder.getCalSchema();
    private static final Config wrapConfig = ConfigImpl.newConfig().evaluatorInstance(AviatorEvaluator.newInstance()).function(new ListContain()).build();
    private static final GraphQLSchema wrappedSchema = SchemaWrapper.wrap(wrapConfig, originalSchema);
    private static final GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema).instrumentation(ExecutionEngine.newInstance(wrapConfig)).build();


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
                "            userId: itemId @mapper(expression: \"userId\",dependencySource: \"userId\")\n" +
                "            userIdInUserInfo: itemId @mapper(expression: \"userInfo.userId\",dependencySource: \"userInfo\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        ExecutionResult executionResult = graphQL.execute(query);
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
                "            userId: itemId @mapper(expression: \"userId\",dependencySource: \"userId\")\n" +
                "            userIdInUserInfo: itemId @mapper(expression: \"userInfo == nil?nil:(userInfo.userId)\",dependencySource: \"userInfo\")\n" +
                "        }\n" +
                "    }\n" +
                "}";
        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        ExecutionResult executionResult = graphQL.execute(query);
        assert executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();
        assert Objects.equals(data.get("commodity").get("item").toString(), "{itemId=1, userId=null, userIdInUserInfo=null}");
    }
}
