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
import calculator.validate.Validator;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static calculator.engine.CalculateSchemaHolder.getCalSchema;
import static calculator.engine.ExecutionEngine.newInstance;

public class ArgumentTransformDirectiveTest {


    private static final GraphQLSchema wrappedSchema;
    private static final GraphQL graphQL;

    static {
        wrappedSchema = SchemaWrapper.wrap(ConfigImpl.newConfig().build(), getCalSchema());
        graphQL = GraphQL.newGraphQL(wrappedSchema)
                .instrumentation(newInstance(ConfigImpl.newConfig().build()))
                .build();
    }


    @Test
    public void mapBasicParam() {

    }

    @Test
    public void mapListParam() {

    }


    @Test
    public void filterListParam() {
        String query = "" +
                "query{\n" +
                "    userInfo(id:3){\n" +
                "        preferredItemIdList @node(name: \"itemIds\")\n" +
                "    }\n" +
                "\n" +
                // todo 表达式环境变量为原来的所有参数+依赖的节点。TODO source
                // todo 依赖的节点名称不能根参数名称一样
                "    itemList(ids:[])\n" +
                "    @argumentTransform(operateType: MAP,argument: \"ids\",exp: \"itemIds\",dependencyNode: \"itemIds\")\n" +
                "    {\n" +
                "        itemId\n" +
                "        name\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        ExecutionResult result = graphQL.execute(query);
        assert result != null;
        assert result.getErrors().isEmpty();
        assert Objects.equals(((Map<String, Map>) result.getData()).get("userInfo").get("preferredItemIdList"), Arrays.asList(3, 4, 5));

        Map<Integer, String> itemNameById = ((Map<String, List<Map>>) result.getData()).get("itemList").stream().collect(Collectors.toMap(
                entry -> (Integer) entry.get("itemId"),
                entry -> (String) entry.get("name")
        ));

        assert Objects.equals(itemNameById.get(3), "3_item_name");
        assert Objects.equals(itemNameById.get(4), "4_item_name");
        assert Objects.equals(itemNameById.get(5), "5_item_name");

    }

}
