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
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static calculator.engine.CalculateSchemaHolder.getCalSchema;
import static calculator.engine.ExecutionEngine.newInstance;

public class ParamTransformDirectiveTest {


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


//    @Test
//    public void filterListParam() {
//        String query = "" +
//                "query ($itemIds:[Int]){\n" +
//                "            itemList(ids: $itemIds) @paramTransform(type: FILTER,name: \"ids\",exp: \"param>5\")\n" +
//                "            {\n" +
//                "                itemId\n" +
//                "                        name\n" +
//                "                stockAmount\n" +
//                "            }\n" +
//                "        }";
//
////        todo 校验
////        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
////        assert !validateResult.isFailure();
//
//        ExecutionInput input = ExecutionInput.newExecutionInput()
//                .query(query)
//                .variables(Collections.singletonMap("itemIds", Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
//                .build();
//
//        ExecutionResult result = graphQL.execute(input);
//        assert result != null;
//        assert result.getErrors().isEmpty();
//        assert ((Map<String, List>) result.getData()).get("couponList").size() == 3;
//
//    }

}
