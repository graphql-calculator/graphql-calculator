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

package calculator.engine.metadata;


import calculator.engine.annotation.Internal;
import graphql.schema.DataFetcher;

import java.util.concurrent.Executor;


@Internal
public class DataFetcherDefinition {

    private final boolean isAsyncFetcher;

    private final boolean isGraphqlAsyncFetcher;

    private final boolean isCalculatorAsyncFetcher;

    private final DataFetcher<?> originalFetcher;

    private final DataFetcher<?> wrappedDataFetcher;

    /**
     * If {@link #isAsyncFetcher}  is true, {@code actionFetcher} and {@code wrappedDataFetcher} are the same DataFetcher.
     * <p>
     * Otherwise, {@code actionFetcher} and {@code originalFetcher} are the same DataFetcher.
     */
    private final DataFetcher<?> actionFetcher;

    private final Executor executor;


    private DataFetcherDefinition(boolean isAsyncFetcher,
                                  boolean isGraphqlAsyncFetcher,
                                  boolean isCalculatorAsyncFetcher,
                                  DataFetcher<?> originalFetcher,
                                  DataFetcher<?> wrappedDataFetcher,
                                  DataFetcher<?> actionFetcher,
                                  Executor executor) {
        this.isAsyncFetcher = isAsyncFetcher;
        this.isGraphqlAsyncFetcher = isGraphqlAsyncFetcher;
        this.isCalculatorAsyncFetcher = isCalculatorAsyncFetcher;
        this.originalFetcher = originalFetcher;
        this.wrappedDataFetcher = wrappedDataFetcher;
        this.actionFetcher = actionFetcher;
        this.executor = executor;
    }

    public boolean isAsyncFetcher() {
        return isAsyncFetcher;
    }

    public boolean isGraphqlAsyncFetcher() {
        return isGraphqlAsyncFetcher;
    }

    public boolean isCalculatorAsyncFetcher() {
        return isCalculatorAsyncFetcher;
    }

    public DataFetcher<?> getOriginalFetcher() {
        return originalFetcher;
    }

    public DataFetcher<?> getWrappedDataFetcher() {
        return wrappedDataFetcher;
    }

    public DataFetcher<?> getActionFetcher() {
        return actionFetcher;
    }

    /**
     * Get the Executor this DataFetcher used.
     *
     * <br>Note: do not use this Executor execute other task.
     */
    public Executor getExecutor() {
        return executor;
    }

    public static class Builder {

        private boolean isAsyncFetcher;

        private boolean isGraphqlAsyncFetcher;

        private boolean isCalculatorAsyncFetcher;

        private DataFetcher<?> originalFetcher;

        private DataFetcher<?> wrappedDataFetcher;

        private DataFetcher<?> actionFetcher;

        private Executor executor;


        public Builder isGraphqlAsyncFetcher(boolean isGraphqlAsyncFetcher) {
            this.isAsyncFetcher = true;
            this.isGraphqlAsyncFetcher = isGraphqlAsyncFetcher;
            return this;
        }

        public Builder isCalculatorAsyncFetcher(boolean isCalculatorAsyncFetcher) {
            this.isAsyncFetcher = true;
            this.isCalculatorAsyncFetcher = isCalculatorAsyncFetcher;
            return this;
        }


        public Builder originalFetcher(DataFetcher<?> originalFetcher) {
            this.originalFetcher = originalFetcher;
            return this;
        }

        public Builder wrappedDataFetcher(DataFetcher<?> wrappedDataFetcher) {
            this.wrappedDataFetcher = wrappedDataFetcher;
            return this;
        }

        public Builder actionFetcher(DataFetcher<?> actionFetcher) {
            this.actionFetcher = actionFetcher;
            return this;
        }

        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public DataFetcherDefinition build() {
            return new DataFetcherDefinition(
                    isAsyncFetcher, isGraphqlAsyncFetcher, isCalculatorAsyncFetcher,
                    originalFetcher, wrappedDataFetcher, actionFetcher, executor
            );
        }

    }

}
