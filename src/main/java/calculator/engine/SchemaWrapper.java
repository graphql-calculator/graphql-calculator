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

import calculator.config.Config;
import calculator.engine.annotation.Internal;
import calculator.engine.validation.CalculatorSchemaValidationError;
import calculator.engine.validation.SchemaValidator;
import calculator.exception.WrapperSchemaException;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;
import graphql.schema.SchemaTraverser;
import graphql.util.TraverserResult;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM_TYPE;
import static calculator.engine.metadata.Directives.getCalDirectiveByName;
import static calculator.engine.metadata.Directives.getCalQueryDirectiveByName;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Internal
public class SchemaWrapper {

    public static GraphQLSchema wrap(Config config, GraphQLSchema existingSchema) {
        check(config, existingSchema);

        GraphQLSchema.Builder wrappedSchemaBuilder = GraphQLSchema.newSchema(existingSchema);

        // add calculator directives to schema
        for (GraphQLDirective calDirective : getCalDirectiveByName().values()) {
            wrappedSchemaBuilder.additionalDirective(calDirective);
        }
        wrappedSchemaBuilder.additionalType(ARGUMENT_TRANSFORM_TYPE);
        GraphQLSchema resultSchema = wrappedSchemaBuilder.build();

        SchemaTraverser schemaTraverser = new SchemaTraverser();
        GraphQLCodeRegistry.Builder codeRegistry = GraphQLCodeRegistry.newCodeRegistry(resultSchema.getCodeRegistry());
        TraverserResult traverserResult = schemaTraverser.depthFirstFullSchema(
                Collections.singletonList(PartitionDataFetcher.TYPE_VISITOR),
                resultSchema,
                Collections.singletonMap(GraphQLCodeRegistry.Builder.class, codeRegistry)
        );

        return resultSchema.transform(builder -> builder.codeRegistry(codeRegistry.build()));
    }

    private static void check(Config config, GraphQLSchema existingSchema) {
        Set<String> schemaDirsName = existingSchema.getDirectives().stream().map(GraphQLDirective::getName).collect(toSet());

        List<String> duplicateDir = getCalQueryDirectiveByName()
                .keySet().stream()
                .filter(schemaDirsName::contains)
                .collect(toList());

        if (!duplicateDir.isEmpty()) {
            String errorMsg = String.format("directive named '%s' is already exist in schema.", duplicateDir);
            throw new WrapperSchemaException(errorMsg);
        }

        List<CalculatorSchemaValidationError> schemaValidationErrors = SchemaValidator.validateSchema(config, existingSchema);
        if (!schemaValidationErrors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CalculatorSchemaValidationError schemaValidationError : schemaValidationErrors) {
                String errorMsg = String.format(
                        "errorType: %s, location: %s, msg: %s\n",
                        schemaValidationError.getErrorType(), schemaValidationError.getLocation(), schemaValidationError.getDescription()
                );
                sb.append(errorMsg);
            }
            throw new WrapperSchemaException(sb.toString());
        }
    }
}
