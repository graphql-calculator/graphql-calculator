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

package calculator.graphql;

import calculator.config.Config;
import calculator.engine.annotation.PublicApi;
import graphql.GraphQL;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.schema.GraphQLSchema;

import java.util.List;

@PublicApi
public interface GraphQLSource {

    GraphQLSchema wrappedSchema();

    GraphQL graphQL();

    static Builder newGraphQLSource(){
        return new DefaultGraphQLSourceBuilder();
    }

    interface Builder {

        Builder wrapperConfig(Config wrapperConfig);

        Builder originalSchema(GraphQLSchema originalSchema);

        Builder preparsedDocumentProvider(PreparsedDocumentProvider preparsedDocumentProvider);

        Builder instrumentation(Instrumentation instrumentation);

        Builder instrumentations(List<Instrumentation> instrumentations);

        GraphQLSource build();

    }

}
