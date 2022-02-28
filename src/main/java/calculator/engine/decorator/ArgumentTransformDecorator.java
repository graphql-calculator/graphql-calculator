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

import calculator.common.GraphQLUtil;
import calculator.engine.metadata.DataFetcherDefinition;
import calculator.engine.metadata.Directives;
import calculator.engine.metadata.FetchSourceTask;
import graphql.language.Directive;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.common.CommonUtil.getDependenceSourceFromDirective;
import static calculator.engine.metadata.Directives.ARGUMENT_TRANSFORM;
import static graphql.schema.AsyncDataFetcher.async;
import static java.util.stream.Collectors.toList;

public class ArgumentTransformDecorator extends AbstractDecorator {
    @Override
    public boolean supportDirective(Directive directive, WrapperEnvironment environment) {
        return Objects.equals(ARGUMENT_TRANSFORM.getName(), directive.getName());
    }

    @Override
    public DataFetcher<?> wrap(Directive directive, WrapperEnvironment environment) {
        Supplier<Directives.ParamTransformType> defaultOperateType = () -> (Directives.ParamTransformType) ARGUMENT_TRANSFORM
                .getArgument("operateType")
                .getArgumentDefaultValue()
                .getValue();
        String operateType = getArgumentFromDirective(directive, "operateType");
        final String finalOperateType = operateType != null ? operateType : defaultOperateType.get().name();

        String argumentName = getArgumentFromDirective(directive, "argumentName");
        String expression = getArgumentFromDirective(directive, "expression");
        List<String> dependencySources = getDependenceSourceFromDirective(directive);


        DataFetcherDefinition dataFetcherDefinition = GraphQLUtil.getDataFetcherDefinition(environment.getOriginalDataFetcher());

        DataFetcher<?> wrappedDataFetcher = fetchingEnvironment -> {
            Map<String, Object> sourceEnv = new LinkedHashMap<>();
            if (dependencySources != null && !dependencySources.isEmpty()) {
                for (String dependencySource : dependencySources) {
                    FetchSourceTask sourceTask = getFetchSourceFromState(environment.getEngineState(), dependencySource);
                    if (sourceTask.getTaskFuture().isCompletedExceptionally()) {
                        sourceEnv.put(dependencySource, null);
                    } else {
                        sourceEnv.put(dependencySource, sourceTask.getTaskFuture().join());
                    }
                }
            }

            // filter list element of list argument
            if (Objects.equals(finalOperateType, Directives.ParamTransformType.FILTER.name())) {
                List<Object> argument = fetchingEnvironment.getArgument(argumentName);
                if (argument == null || argument.isEmpty()) {
                    return dataFetcherDefinition.getActionFetcher().get(fetchingEnvironment);
                }

                argument = argument.stream().filter(ele -> {
                            Map<String, Object> filterEnv = new LinkedHashMap<>(fetchingEnvironment.getVariables());
                            filterEnv.put("ele", ele);
                            filterEnv.putAll(sourceEnv);
                            return (Boolean) environment.getScriptEvaluator().evaluate(expression, filterEnv);
                        }
                ).collect(toList());

                Map<String, Object> newArguments = new LinkedHashMap<>(fetchingEnvironment.getArguments());
                newArguments.put(argumentName, argument);
                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(fetchingEnvironment).arguments(newArguments).build();
                Object innerResult = dataFetcherDefinition.getActionFetcher().get(newEnvironment);
                if (innerResult instanceof CompletionStage
                        && (dataFetcherDefinition.isAsyncFetcher() || (dependencySources != null && dependencySources.size() > 0))
                ) {
                    return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
                }
                return innerResult;
            }

            // map each element of list argument
            if (Objects.equals(finalOperateType, Directives.ParamTransformType.LIST_MAP.name())) {
                List<Object> argument = fetchingEnvironment.getArgument(argumentName);
                if (argument == null || argument.isEmpty()) {
                    return dataFetcherDefinition.getActionFetcher().get(fetchingEnvironment);
                }

                argument = argument.stream().map(ele -> {
                    Map<String, Object> transformEnv = new LinkedHashMap<>(fetchingEnvironment.getVariables());
                    transformEnv.put("ele", ele);
                    transformEnv.putAll(sourceEnv);
                    return environment.getScriptEvaluator().evaluate(expression, transformEnv);
                }).collect(toList());

                Map<String, Object> newArguments = new LinkedHashMap<>(fetchingEnvironment.getArguments());
                newArguments.put(argumentName, argument);
                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(fetchingEnvironment).arguments(newArguments).build();

                Object innerResult = dataFetcherDefinition.getActionFetcher().get(newEnvironment);
                if (innerResult instanceof CompletionStage
                        && (dataFetcherDefinition.isAsyncFetcher() || (dependencySources != null && dependencySources.size() > 0))
                ) {
                    return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
                }
                return innerResult;
            }

            // map argument by expression
            if (Objects.equals(finalOperateType, Directives.ParamTransformType.MAP.name())) {

                Map<String, Object> transformEnv = new LinkedHashMap<>(fetchingEnvironment.getVariables());
                transformEnv.putAll(sourceEnv);
                Object newParam = environment.getScriptEvaluator().evaluate(expression, transformEnv);

                Map<String, Object> newArguments = new LinkedHashMap<>(fetchingEnvironment.getArguments());
                newArguments.put(argumentName, newParam);

                DataFetchingEnvironment newEnvironment = DataFetchingEnvironmentImpl
                        .newDataFetchingEnvironment(fetchingEnvironment).arguments(newArguments).build();

                Object innerResult = dataFetcherDefinition.getActionFetcher().get(newEnvironment);
                if (innerResult instanceof CompletionStage
                        && (dataFetcherDefinition.isAsyncFetcher() || (dependencySources != null && dependencySources.size() > 0))
                ) {
                    return ((CompletionStage<?>) innerResult).toCompletableFuture().join();
                }
                return innerResult;
            }

            throw new RuntimeException("can not invoke here.");
        };

        if (dataFetcherDefinition.isAsyncFetcher()) {
            return async(wrappedDataFetcher, dataFetcherDefinition.getExecutor());
        }

        // e.g. PropertyDataFetcher with @map, and dependencies is not empty.
        if (dependencySources != null && dependencySources.size() > 0) {
            return async(wrappedDataFetcher, environment.getExecutor());
        }

        return wrappedDataFetcher;
    }
}
