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

package calculator.engine.decorator;

import calculator.engine.ExecutionEngineState;
import calculator.engine.ObjectMapper;
import calculator.engine.annotation.Internal;
import calculator.engine.script.ScriptEvaluator;
import graphql.execution.ValueUnboxer;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;

import java.util.List;
import java.util.concurrent.Executor;

@Internal
public class WrapperEnvironment {
    private final Field field;
    private final DataFetcher<?> originalDataFetcher;
    private final GraphQLFieldDefinition fieldDefinition;
    private final Directive directive;
    private final List<GraphQLDirective> directivesOnFieldDefinition;
    private final DataFetchingEnvironment environment;
    private final ExecutionEngineState engineState;
    private final ValueUnboxer valueUnboxer;
    private final Executor executor;
    private final ObjectMapper objectMapper;
    private final ScriptEvaluator scriptEvaluator;

    public WrapperEnvironment(Field field,
                              DataFetcher<?> originalDataFetcher,
                              GraphQLFieldDefinition fieldDefinition,
                              Directive directive,
                              List<GraphQLDirective> directivesOnFieldDefinition,
                              DataFetchingEnvironment environment,
                              ExecutionEngineState engineState,
                              ValueUnboxer valueUnboxer,
                              Executor executor, ObjectMapper objectMapper, ScriptEvaluator scriptEvaluator
    ) {
        this.field = field;
        this.originalDataFetcher = originalDataFetcher;
        this.fieldDefinition = fieldDefinition;
        this.directive = directive;
        this.directivesOnFieldDefinition = directivesOnFieldDefinition;
        this.environment = environment;
        this.engineState = engineState;
        this.valueUnboxer = valueUnboxer;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.scriptEvaluator = scriptEvaluator;
    }

    public Field getField() {
        return field;
    }

    public DataFetcher<?> getOriginalDataFetcher() {
        return originalDataFetcher;
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public Directive getDirective() {
        return directive;
    }

    public List<GraphQLDirective> getDirectivesOnFieldDefinition() {
        return directivesOnFieldDefinition;
    }

    public DataFetchingEnvironment getEnvironment() {
        return environment;
    }

    public ExecutionEngineState getEngineState() {
        return engineState;
    }

    public ValueUnboxer getValueUnboxer() {
        return valueUnboxer;
    }

    public Executor getExecutor() {
        return executor;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public ScriptEvaluator getScriptEvaluator() {
        return scriptEvaluator;
    }
}
