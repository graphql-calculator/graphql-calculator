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

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static graphql.Assert.assertNotNull;

public class AsyncDataFetcherWithGetter<T> implements DataFetcher<CompletableFuture<T>> {

    public static <T> graphql.schema.AsyncDataFetcher<T> async(DataFetcher<T> wrappedDataFetcher) {
        return new graphql.schema.AsyncDataFetcher<>(wrappedDataFetcher);
    }

    public static <T> graphql.schema.AsyncDataFetcher<T> async(DataFetcher<T> wrappedDataFetcher, Executor executor) {
        return new graphql.schema.AsyncDataFetcher<>(wrappedDataFetcher, executor);
    }

    private final DataFetcher<T> wrappedDataFetcher;
    private final Executor executor;


    public DataFetcher<T> getWrappedDataFetcher() {
        return wrappedDataFetcher;
    }

    public Executor getExecutor() {
        return executor;
    }

    public AsyncDataFetcherWithGetter(DataFetcher<T> wrappedDataFetcher) {
        this(wrappedDataFetcher, ForkJoinPool.commonPool());
    }

    public AsyncDataFetcherWithGetter(DataFetcher<T> wrappedDataFetcher, Executor executor) {
        this.wrappedDataFetcher = assertNotNull(wrappedDataFetcher, () -> "wrappedDataFetcher can't be null");
        this.executor = assertNotNull(executor, () -> "executor can't be null");
    }

    @Override
    public CompletableFuture<T> get(DataFetchingEnvironment environment) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return wrappedDataFetcher.get(environment);
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException(e);
                }
            }
        }, executor);
    }

}

