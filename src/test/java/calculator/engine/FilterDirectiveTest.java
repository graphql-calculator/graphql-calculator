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
import calculator.function.ListContain;
import calculator.validate.Validator;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static calculator.engine.CalculateSchemaHolder.getCalSchema;
import static calculator.engine.ExecutionEngine.newInstance;

public class FilterDirectiveTest {

    private static final GraphQLSchema wrappedSchema = SchemaWrapper.wrap(
            ConfigImpl.newConfig().function(new ListContain()).build(), getCalSchema()
    );

    private static final GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema)
            .instrumentation(newInstance(ConfigImpl.newConfig().build()))
            .build();

    @Test
    public void unUsedNodeOnFilterTest() {
        String query = "" +
                "query {\n" +
                "    itemList(ids: [1,2,3,4])\n" +
                "    @filter(predicate: \"itemId>0\",dependencyNode: \"preferredItemIdList\")\n" +
                "    {\n" +
                "        itemId\n" +
                "        name\n" +
                "    }\n" +
                "    \n" +
                "    userInfo{\n" +
                "        preferredItemIdList @node(name:\"preferredItemIdList\")\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert validateResult.isFailure();
        assert Objects.equals(validateResult.getValidationErrors().get(0).getDescription(),
                "the node 'preferredItemIdList' do not used by {itemList}.");
    }

    @Test
    public void filterWithDependencyNodeTest() {
        String query = "" +
                "query {\n" +
                "    itemList(ids: [1,2,3,4])\n" +
                "    @filter(predicate: \"listContain(preferredItemIdList,itemId)\", dependencyNode: \"preferredItemIdList\")\n" +
                "    {\n" +
                "        itemId\n" +
                "        name\n" +
                "    }\n" +
                "\n" +
                "    userInfo(id:3){\n" +
                "        # 根据userDF逻辑，preferredItemIdList = [3,4,5]\n" +
                "        preferredItemIdList @node(name:\"preferredItemIdList\")\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
        assert !validateResult.isFailure();

        ExecutionResult result = graphQL.execute(query);
        assert result != null;
        assert result.getErrors().isEmpty();
        assert Objects.equals(
                ((Map<String, Map>) result.getData()).get("userInfo").get("preferredItemIdList"),
                Arrays.asList(3, 4, 5)
        );
        assert ((Map<String, List>) result.getData()).get("itemList").size() == 2;

        List<Integer> itemIds = new ArrayList<>();
        for (Map<String, Object> itemInfo : ((Map<String, List<Map>>) result.getData()).get("itemList")) {
            itemIds.add((Integer)itemInfo.get("itemId"));
        }
        assert itemIds.containsAll(Arrays.asList(3, 4));
    }

    @Test
    public void filterAndDependencyOnTheSameFieldTest() {
        String query = "" +
                "query{\n" +
                "    userInfoList @filter(predicate:\"func(uList)\",dependencyNode: \"uList\") @node(name: \"uList\")\n" +
                "    {\n" +
                "        userId\n" +
                "        name\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult parseAndValidateResult = Validator.validateQuery(
                query, wrappedSchema
        );

        assert parseAndValidateResult.getValidationErrors().size() == 1;
        assert parseAndValidateResult.getValidationErrors().get(0).getDescription().equals(
                "the node 'uList' and filter can not annotated on the same field {userInfoList}."
        );
    }

    @Test
    public void filterAndDependencyShareSameAncestorTest() {
        String query = "" +
                "query {\n" +
                "    itemList(ids: [1,2,3,4])\n" +
                "    {\n" +
                "        itemId @node(name:\"itemId\")\n" +
                "        name\n" +
                "        couponList @filter(predicate: \"func(itemId)\", dependencyNode: \"itemId\")\n" +
                "        {\n" +
                "            couponId\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult parseAndValidateResult = Validator.validateQuery(
                query, wrappedSchema
        );

        assert parseAndValidateResult.getValidationErrors().size() == 1;
        assert parseAndValidateResult.getValidationErrors().get(0).getDescription().equals(
                "the node 'itemId' and field 'itemList#couponList' is in the same ancestor list field 'itemList'."
        );
    }


    @Test
    public void filterCanNotDependOnAncestorNodeTest() {
        String query = "" +
                "query{\n" +
                "    userInfo @node(name: \"uInfo\")\n" +
                "    {\n" +
                "        userId\n" +
                "        name\n" +
                "        preferredItemIdList  @filter(predicate:\"func(uInfo)\",dependencyNode: \"uInfo\")\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult parseAndValidateResult = Validator.validateQuery(
                query, wrappedSchema
        );

        assert parseAndValidateResult.getValidationErrors().size() == 1;
        assert parseAndValidateResult.getValidationErrors().get(0).getDescription().equals(
                "the field {userInfo#preferredItemIdList} can not depend on its ancestor {userInfo} (node 'uInfo')."
        );
    }


}
