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

import calculator.config.DefaultConfig;
import calculator.exception.WrapperSchemaException;
import calculator.util.TestUtil;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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

}
