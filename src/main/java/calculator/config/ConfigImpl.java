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
package calculator.config;

import calculator.engine.ObjectMapper;
import calculator.engine.DefaultObjectMapper;
import calculator.engine.script.AviatorScriptEvaluator;
import calculator.engine.script.ScriptEvaluator;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * The default implementation of {@link Config}.
 */
public class ConfigImpl implements Config {

    private final Executor threadPool;

    private final ObjectMapper objectMapper;

    private final ScriptEvaluator scriptEvaluator;

    private static final ObjectMapper DEFAULT_MAPPER = new DefaultObjectMapper();

    private static final Executor DEFAULT_EXECUTOR = ForkJoinPool.commonPool();

    private static final AviatorScriptEvaluator DEFAULT_EVALUATOR = new AviatorScriptEvaluator();

    private ConfigImpl(Executor threadPool,
                       ObjectMapper objectMapper,
                       ScriptEvaluator scriptEvaluator) {
        this.threadPool = threadPool != null ? threadPool : DEFAULT_EXECUTOR;
        this.objectMapper = objectMapper != null ? objectMapper : DEFAULT_MAPPER;
        this.scriptEvaluator = scriptEvaluator != null ? scriptEvaluator : DEFAULT_EVALUATOR;
    }

    @Override
    public ScriptEvaluator getScriptEvaluator() {
        return scriptEvaluator;
    }

    @Override
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public Executor getExecutor() {
        return threadPool;
    }

    public static Builder newConfig() {
        return new Builder();
    }

    public static class Builder {

        private Executor threadPool;

        private ObjectMapper objectMapper;

        private ScriptEvaluator scriptEvaluator;

        public Builder threadPool(Executor threadPool) {
            Objects.requireNonNull(threadPool, "threadPool can not be null.");
            this.threadPool = threadPool;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            Objects.requireNonNull(objectMapper, "objectMapper can not be null.");
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder scriptEvaluator(ScriptEvaluator scriptEvaluator) {
            Objects.requireNonNull(scriptEvaluator, "scriptEvaluator can not be null.");
            this.scriptEvaluator = scriptEvaluator;
            return this;
        }

        public ConfigImpl build() {
            return new ConfigImpl(threadPool, objectMapper, scriptEvaluator);
        }
    }
}
