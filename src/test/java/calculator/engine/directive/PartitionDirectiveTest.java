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

import calculator.config.DefaultConfig;
import calculator.engine.SchemaWrapper;
import calculator.exception.WrapperSchemaException;
import calculator.graphql.DefaultGraphQLSourceBuilder;
import calculator.graphql.GraphQLSource;
import calculator.util.GraphQLSourceHolder;
import calculator.util.TestUtil;
import calculator.validation.Validator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class PartitionDirectiveTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void partitionMustDefineOnListTypeField() throws Exception {
        expectedException.expect(WrapperSchemaException.class);
        expectedException.expectMessage("errorType: InvalidAppliedDirectiveArgument, "
                + "location: SourceLocation{line=5, column=5}, "
                + "msg: @partition must be used on list type field, instead of {Query.singleField}.\n");

        GraphQLSchema graphQLSchema = TestUtil.schemaBySpec(
                "directive @partition(size: Int!) on ARGUMENT_DEFINITION\n" +
                        "\n" +
                        "type Query {\n" +
                        "\n" +
                        "    singleField(arg: Int @partition(size:5)): Int\n" +
                        "\n" +
                        "    listField(arg: [Int] @partition(size:5)): [Int]\n" +
                        "}",
                RuntimeWiring.newRuntimeWiring().build()
        );
        GraphQLSchema wrappedSchema = SchemaWrapper.wrap(DefaultConfig.newConfig().build(), graphQLSchema);
    }

    @Test
    public void partitionMustDefineOnListTypeArgument() throws Exception {
        expectedException.expect(WrapperSchemaException.class);
        expectedException.expectMessage("errorType: InvalidAppliedDirectiveArgument, "
                + "location: SourceLocation{line=7, column=5}, "
                + "msg: @partition must be used on list type argument, instead of {Query.listField.arg}.\n");

        GraphQLSchema graphQLSchema = TestUtil.schemaBySpec(
                "directive @partition(size: Int!) on ARGUMENT_DEFINITION\n" +
                        "\n" +
                        "type Query {\n" +
                        "\n" +
                        "    singleField(arg: Int): Int\n" +
                        "\n" +
                        "    listField(arg: Int @partition(size:5)): [Int]\n" +
                        "}",
                RuntimeWiring.newRuntimeWiring().build()
        );
        GraphQLSchema wrappedSchema = SchemaWrapper.wrap(DefaultConfig.newConfig().build(), graphQLSchema);
    }

    @Test
    public void moreThanOneArgumentsUsePartitionOnOneField() throws Exception {
        expectedException.expect(WrapperSchemaException.class);
        expectedException.expectMessage("errorType: InvalidAppliedDirectiveArgument, "
                + "location: SourceLocation{line=7, column=5}, "
                + "msg: more than one argument on {Query.listField} use @partition.\n");

        GraphQLSchema graphQLSchema = TestUtil.schemaBySpec(
                "directive @partition(size: Int!) on ARGUMENT_DEFINITION\n" +
                        "\n" +
                        "type Query {\n" +
                        "\n" +
                        "    singleField(arg: Int): Int\n" +
                        "\n" +
                        "    listField(argX: Int @partition(size:5),argY: Int @partition(size:5)): [Int]\n" +
                        "}",
                RuntimeWiring.newRuntimeWiring().build()
        );
        GraphQLSchema wrappedSchema = SchemaWrapper.wrap(DefaultConfig.newConfig().build(), graphQLSchema);
    }

    @Test
    public void sizeMustBePositiveNumber() throws Exception {
        expectedException.expect(WrapperSchemaException.class);
        expectedException.expectMessage("errorType: InvalidAppliedDirectiveArgument, "
                + "location: SourceLocation{line=7, column=5}, "
                + "msg: the size value of @partition on {Query.listField} must be positive number.\n");

        GraphQLSchema graphQLSchema = TestUtil.schemaBySpec(
                "directive @partition(size: Int!) on ARGUMENT_DEFINITION\n" +
                        "\n" +
                        "type Query {\n" +
                        "\n" +
                        "    singleField(arg: Int): Int\n" +
                        "\n" +
                        "    listField(arg: [Int] @partition(size:0)): [Int]\n" +
                        "}",
                RuntimeWiring.newRuntimeWiring().build()
        );
        GraphQLSchema wrappedSchema = SchemaWrapper.wrap(DefaultConfig.newConfig().build(), graphQLSchema);
    }


    private static final GraphQLSchema originalSchema = GraphQLSourceHolder.getSchemaWithPartition();
    private static final GraphQLSource graphqlSource = new DefaultGraphQLSourceBuilder()
            .wrapperConfig(DefaultConfig.newConfig().build()).originalSchema(originalSchema).build();

    @Test
    public void distinctSimpleCase_notSetComparator() {
        String query = "" +
                "query($userIds: [Int]){\n" +
                "    userInfoList(userIds: $userIds){\n" +
                "        userId\n" +
                "        age\n" +
                "        name\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(
                query, graphqlSource.getWrappedSchema(), DefaultConfig.newConfig().build()
        );
        assert !validateResult.isFailure();


        ExecutionInput emptyInput = ExecutionInput.newExecutionInput(query)
                .build();
        ExecutionResult emptyResult = graphqlSource.getGraphQL().execute(emptyInput);
        assert emptyResult.getErrors().isEmpty();
        assert Objects.equals(
                ((Map) emptyResult.getData()).get("userInfoList").toString(), "[]"
        );

        ExecutionInput lessThanSize = ExecutionInput.newExecutionInput(query)
                .variables(Collections.singletonMap("userIds", Arrays.asList(1, 2, 3, 4)))
                .build();
        ExecutionResult lessThanSizeResult = graphqlSource.getGraphQL().execute(lessThanSize);
        assert lessThanSizeResult.getErrors().isEmpty();
        assert Objects.equals(
                ((Map) lessThanSizeResult.getData()).get("userInfoList").toString(),
                "[{userId=1, age=10, name=1_name}, {userId=2, age=20, name=2_name}, {userId=3, age=30, name=3_name}, {userId=4, age=40, name=4_name}]"
        );

        ExecutionInput equalToSize = ExecutionInput.newExecutionInput(query)
                .variables(Collections.singletonMap("userIds", Arrays.asList(1, 2, 3, 4, 5)))
                .build();
        ExecutionResult equalToSizeResult = graphqlSource.getGraphQL().execute(equalToSize);
        assert equalToSizeResult.getErrors().isEmpty();
        assert Objects.equals(
                ((Map) equalToSizeResult.getData()).get("userInfoList").toString(),
                "[{userId=1, age=10, name=1_name}, {userId=2, age=20, name=2_name}, {userId=3, age=30, name=3_name}, {userId=4, age=40, name=4_name}, {userId=5, age=50, name=5_name}]"
        );

        ExecutionInput grateThanSize = ExecutionInput.newExecutionInput(query)
                .variables(Collections.singletonMap("userIds", Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8)))
                .build();
        ExecutionResult grateThanSizeResult = graphqlSource.getGraphQL().execute(grateThanSize);
        assert grateThanSizeResult.getErrors().isEmpty();
        assert Objects.equals(
                ((Map) grateThanSizeResult.getData()).get("userInfoList").toString(),
                "[{userId=1, age=10, name=1_name}, {userId=2, age=20, name=2_name}, " +
                        "{userId=3, age=30, name=3_name}, {userId=4, age=40, name=4_name}, " +
                        "{userId=5, age=50, name=5_name}, {userId=6, age=60, name=6_name}, " +
                        "{userId=7, age=70, name=7_name}, {userId=8, age=80, name=8_name}]"
        );

        ExecutionInput doubleSize = ExecutionInput.newExecutionInput(query)
                .variables(Collections.singletonMap("userIds", Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
                .build();
        ExecutionResult doubleSizeResult = graphqlSource.getGraphQL().execute(doubleSize);
        assert doubleSizeResult.getErrors().isEmpty();
        assert Objects.equals(
                ((Map) doubleSizeResult.getData()).get("userInfoList").toString(),
                "[{userId=1, age=10, name=1_name}, {userId=2, age=20, name=2_name}, " +
                        "{userId=3, age=30, name=3_name}, {userId=4, age=40, name=4_name}, " +
                        "{userId=5, age=50, name=5_name}, {userId=6, age=60, name=6_name}, " +
                        "{userId=7, age=70, name=7_name}, {userId=8, age=80, name=8_name}, " +
                        "{userId=9, age=90, name=9_name}, {userId=10, age=0, name=10_name}]"
        );
    }

}
