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
import calculator.function.IgnoreConsumer;
import calculator.validate.Validator;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import static calculator.engine.ExecutionEngine.newInstance;
import static calculator.engine.SchemaHolder.getCalSchema;

public class NodeDirectiveTest {

    private static final GraphQLSchema wrappedSchema;
    private static final GraphQL graphQL;
    static {
        wrappedSchema = SchemaWrapper.wrap(ConfigImpl.newConfig().build(), getCalSchema());
        graphQL = GraphQL.newGraphQL(wrappedSchema)
                .instrumentation(
                        newInstance(
                                ConfigImpl.newConfig().function(new IgnoreConsumer()).build()
                        )
                ).build();
    }


    @Test
    public void nestedNodeValueTest() {
        // @node 放到itemList上、获取的数据是否包括 itemList.couponList
        String query = "" +
                "query { \n" +
                "    itemList(ids: [1,2])\n" +
                "    @node(name: \"itemInfos\")\n" +
                "    {\n" +
                "        itemId\n" +
                "        # couponList 也配置了异步fetcher\n" +
                "        couponList(couponIds: [3,4]){\n" +
                "            couponId\n" +
                "            couponText\n" +
                "        }\n" +
                "    }\n" +
                "    userInfoList(ids: [5,6])\n" +
                "    @filter(predicate:\"ignore(itemInfos)\",dependencyNode: \"itemInfos\")\n" +
                "    {\n" +
                "        userId\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        ExecutionResult result = graphQL.execute(query);
        assert result != null;
        assert result.getErrors().isEmpty();
    }



}
