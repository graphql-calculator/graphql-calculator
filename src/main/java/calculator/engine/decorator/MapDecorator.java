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

import calculator.common.CommonUtil;
import calculator.common.GraphQLUtil;
import calculator.engine.ObjectMapper;
import calculator.engine.annotation.Internal;
import calculator.engine.metadata.DataFetcherDefinition;
import calculator.engine.metadata.FetchSourceTask;
import graphql.language.Directive;
import graphql.schema.DataFetcher;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.common.CommonUtil.getDependenceSourceFromDirective;
import static calculator.engine.metadata.Directives.MAP;
import static graphql.schema.AsyncDataFetcher.async;

@Internal
public class MapDecorator extends AbstractDecorator {

    @Override
    public boolean supportDirective(Directive directive, WrapperEnvironment environment) {
        return Objects.equals(MAP.getName(), environment.getDirective().getName());
    }

    @Override
    public DataFetcher<?> decorate(Directive directive, WrapperEnvironment environment) {
        String mapper = getArgumentFromDirective(environment.getDirective(), "mapper");
        List<String> dependencySources = getDependenceSourceFromDirective(environment.getDirective());

        DataFetcherDefinition dataFetcherDefinition = GraphQLUtil.getDataFetcherDefinition(
                environment.getOriginalDataFetcher()
        );

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

            // new Map, do not alter original Map info.
            Map<String, Object> expEnv = new LinkedHashMap<>();
            Object sourceInfo = getScriptEnv(environment.getObjectMapper(), fetchingEnvironment.getSource());
            if (sourceInfo != null) {
                expEnv.putAll((Map) sourceInfo);
            }

            expEnv.putAll(sourceEnv);

            return environment.getScriptEvaluator().evaluate(mapper, expEnv);
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

    private Object getScriptEnv(ObjectMapper objectMapper, Object res) {
        if (res == null) {
            return null;
        }

        if (CommonUtil.isBasicType(res)) {
            return Collections.singletonMap("ele", res);
        } else {
            return objectMapper.toSimpleCollection(res);
        }
    }
}
