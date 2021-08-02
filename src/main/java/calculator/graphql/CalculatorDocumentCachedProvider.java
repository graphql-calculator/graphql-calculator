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
import calculator.validation.Validator;
import graphql.ExecutionInput;
import graphql.ParseAndValidateResult;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.schema.GraphQLSchema;

import java.util.function.Function;

@PublicApi
public abstract class CalculatorDocumentCachedProvider implements PreparsedDocumentProvider {

    private Config wrapperConfig;
    private GraphQLSchema wrappedSchema;

    public CalculatorDocumentCachedProvider() {
    }

    void setWrapperConfig(Config wrapperConfig) {
        this.wrapperConfig = wrapperConfig;
    }

    void setWrappedSchema(GraphQLSchema wrappedSchema) {
        this.wrappedSchema = wrappedSchema;
    }

    @Override
    public final PreparsedDocumentEntry getDocument(ExecutionInput executionInput,
                                                    Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        PreparsedDocumentEntry cacheValue = getDocumentFromCache(executionInput, parseAndValidateFunction);
        if (cacheValue != null) {
            return cacheValue;
        }

        ParseAndValidateResult validateResult = Validator.validateQuery(
                executionInput.getQuery(), wrappedSchema, wrapperConfig
        );

        PreparsedDocumentEntry preparsedDocumentEntry;
        if (validateResult.isFailure()) {
            preparsedDocumentEntry = new PreparsedDocumentEntry(validateResult.getErrors());
        } else {
            preparsedDocumentEntry = new PreparsedDocumentEntry(validateResult.getDocument());
        }

        setDocumentCache(executionInput, preparsedDocumentEntry);
        return preparsedDocumentEntry;
    }


    public abstract PreparsedDocumentEntry getDocumentFromCache(ExecutionInput executionInput,
                                                                Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction);

    public abstract void setDocumentCache(ExecutionInput executionInput,
                                          PreparsedDocumentEntry cachedValue);

}
