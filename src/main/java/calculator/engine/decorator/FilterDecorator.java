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

import calculator.common.CollectionUtil;
import calculator.common.GraphQLUtil;
import calculator.engine.annotation.Internal;
import calculator.engine.metadata.DataFetcherDefinition;
import graphql.language.Directive;
import graphql.schema.DataFetcher;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import static calculator.engine.metadata.Directives.FILTER;
import static graphql.schema.AsyncDataFetcher.async;

@Internal
public class FilterDecorator extends AbstractDecorator {

    @Override
    public boolean supportDirective(Directive directive, WrapperEnvironment environment) {
        return Objects.equals(FILTER.getName(), environment.getDirective().getName());
    }

    @Override
    public DataFetcher<?> decorate(Directive directive, WrapperEnvironment wrapperEnvironment) {
        DataFetcherDefinition dataFetcherDefinition = GraphQLUtil.getDataFetcherDefinition(
                wrapperEnvironment.getOriginalDataFetcher()
        );

        DataFetcher<?> wrappedFetcher = (DataFetcher<Object>) environment -> {
            Object originalResult = dataFetcherDefinition.getActionFetcher().get(environment);
            if (originalResult instanceof CompletionStage) {
                originalResult = ((CompletionStage<?>) originalResult).toCompletableFuture().join();
            }
            Object unWrappedData = unWrapDataFetcherResult(originalResult, wrapperEnvironment.getValueUnboxer());
            if (CollectionUtil.arraySize(unWrappedData) == 0) {
                return originalResult;
            }

            List<Object> listResult = CollectionUtil.arrayToList(unWrappedData);
            return wrapResult(originalResult, listResult);
        };

        if (dataFetcherDefinition.isAsyncFetcher()) {
            return async(wrappedFetcher, dataFetcherDefinition.getExecutor());
        }
        return wrappedFetcher;
    }
}
