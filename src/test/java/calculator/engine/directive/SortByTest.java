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

import static com.googlecode.aviator.AviatorEvaluator.execute;

public class SortByTest {
//    private final GraphQLSchema wrappedSchema;
//    private final GraphQL graphQL;
//
//    {
//        wrappedSchema = SchemaWrapper.wrap(ConfigImpl.newConfig().build(), getCalSchema());
//        graphQL = GraphQL.newGraphQL(wrappedSchema)
//                .instrumentation(newInstance(ConfigImpl.newConfig().build()))
//                .build();
//    }
//
//    @Test
//    public void sortMustOnListField() {
//        String query = "" +
//                "query {\n" +
//                "    userInfo(id: 1)\n" +
//                "    @sort(key:\"userId\")\n" +
//                "    {\n" +
//                "        userId\n" +
//                "        name\n" +
//                "    }\n" +
//                "}";
//
//        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
//        assert validateResult.isFailure();
//        assert validateResult.getErrors().size() == 1;
//        assert Objects.equals(validateResult.getErrors().get(0).getMessage(),
//                "Validation error of type null: sort must annotated on list type, instead of {userInfo}.");
//    }
//
//    @Test
//    public void sortDirective() {
//        String query = "query {\n" +
//                "    itemList(ids:[3,2,1,4,5]) @sort(key:\"itemId\"){\n" +
//                "        itemId\n" +
//                "        name\n" +
//                "    }\n" +
//                "}";
//
//        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
//        assert !validateResult.isFailure();
//
//        ExecutionResult result = graphQL.execute(query);
//        assert result != null;
//        assert result.getErrors().isEmpty();
//        assert Objects.equals(execute("seq.get(seq.get(itemList,0),'itemId')", result.getData()), 1);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,1),'itemId')", result.getData()), 2);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,2),'itemId')", result.getData()), 3);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,3),'itemId')", result.getData()), 4);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,4),'itemId')", result.getData()), 5);
//    }
//
//    @Test
//    public void reversedSortDirective() {
//        String query = "query {\n" +
//                "    itemList(ids:[3,2,1,4,5]) @sort(key:\"itemId\",reversed:true){\n" +
//                "        itemId\n" +
//                "        name\n" +
//                "    }\n" +
//                "}";
//
//        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
//        assert !validateResult.isFailure();
//
//        ExecutionResult result = graphQL.execute(query);
//        assert result != null;
//        assert result.getErrors().isEmpty();
//        assert Objects.equals(execute("seq.get(seq.get(itemList,0),'itemId')", result.getData()), 5);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,1),'itemId')", result.getData()), 4);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,2),'itemId')", result.getData()), 3);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,3),'itemId')", result.getData()), 2);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,4),'itemId')", result.getData()), 1);
//    }
//
//
//    @Test
//    public void sortByMustOnListField() {
//        String query = "" +
//                "query {\n" +
//                "    userInfo(id: 1)\n" +
//                "    @sortBy(exp:\"userId\")\n" +
//                "    {\n" +
//                "        userId\n" +
//                "        name\n" +
//                "    }\n" +
//                "}";
//
//        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
//        assert validateResult.isFailure();
//        assert validateResult.getErrors().size() == 1;
//        assert Objects.equals(validateResult.getErrors().get(0).getMessage(),
//                "Validation error of type null: sortBy must annotated on list type, instead of {userInfo}.");
//    }
//
//
//    @Test
//    public void sortByDirective() {
//        String query = "query {\n" +
//                "    itemList(ids:[3,2,1,4,5]) @sortBy(exp:\"itemId%2\"){\n" +
//                "        itemId\n" +
//                "        name\n" +
//                "    }\n" +
//                "}";
//
//        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
//        assert !validateResult.isFailure();
//
//        ExecutionResult result = graphQL.execute(query);
//        assert result != null;
//        assert result.getErrors().isEmpty();
//        assert Objects.equals(execute("seq.get(seq.get(itemList,0),'itemId')", result.getData()), 2);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,1),'itemId')", result.getData()), 4);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,2),'itemId')", result.getData()), 3);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,3),'itemId')", result.getData()), 1);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,4),'itemId')", result.getData()), 5);
//    }
//
//    @Test
//    public void reversedSortByDirective() {
//        String query = "query {\n" +
//                // [{2,4}, {3,1,5}] -> [{3,1,5},{2,4}]
//                "    itemList(ids:[3,2,1,4,5]) @sortBy(exp:\"itemId%2\",reversed: true){\n" +
//                "        itemId\n" +
//                "        name\n" +
//                "    }\n" +
//                "}";
//
//        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
//        assert !validateResult.isFailure();
//
//        ExecutionResult result = graphQL.execute(query);
//        assert result != null;
//        assert result.getErrors().isEmpty();
//        assert Objects.equals(execute("seq.get(seq.get(itemList,0),'itemId')", result.getData()), 3);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,1),'itemId')", result.getData()), 1);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,2),'itemId')", result.getData()), 5);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,3),'itemId')", result.getData()), 2);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,4),'itemId')", result.getData()), 4);
//    }
//
//    @Test
//    public void sortByWithDependencyNodeDirective() {
//        String query = "" +
//                "query{\n" +
//                "    userInfo(id:2){\n" +
//                "        userId @node(name:\"uId\")\n" +
//                "    }\n" +
//                "\n" +
//                "    itemList(ids: [1,2,3,4]) @sortBy(exp:\"itemId%uId\", dependencyNode:\"uId\")\n" +
//                "    {\n" +
//                "        itemId\n" +
//                "        name\n" +
//                "    }\n" +
//                "}";
//
//        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema);
//        assert !validateResult.isFailure();
//
//        ExecutionResult result = graphQL.execute(query);
//        assert result != null;
//        assert result.getErrors().isEmpty();
//        assert Objects.equals(execute("seq.get(seq.get(itemList,0),'itemId')", result.getData()), 2);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,1),'itemId')", result.getData()), 4);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,2),'itemId')", result.getData()), 1);
//        assert Objects.equals(execute("seq.get(seq.get(itemList,3),'itemId')", result.getData()), 3);
//    }

}
