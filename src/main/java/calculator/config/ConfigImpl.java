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
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.runtime.type.AviatorFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 默认配置实现
 */
public class ConfigImpl implements Config {

    private static final AviatorEvaluatorInstance DEFAULT_EVALUATOR = AviatorEvaluator.getInstance();

    private final List<AviatorFunction> functionList;

    private final AviatorEvaluatorInstance aviatorEvaluator;

    private final ObjectMapper objectMapper;

    public ConfigImpl(AviatorEvaluatorInstance aviatorEvaluator,
                      List<AviatorFunction> functionList,
                      ObjectMapper objectMapper) {
        this.aviatorEvaluator = aviatorEvaluator != null ? aviatorEvaluator : DEFAULT_EVALUATOR;
        this.functionList = functionList;
        this.objectMapper = objectMapper != null ? objectMapper : ObjectMapper.DEFAULT_MAPPER;
    }

    @Override
    public AviatorEvaluatorInstance getAviatorEvaluator() {
        return aviatorEvaluator;
    }

    @Override
    public List<AviatorFunction> functions() {
        return Collections.unmodifiableList(functionList);
    }

    @Override
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }


    public static Builder newConfig() {
        return new Builder();
    }

    public static class Builder {

        private AviatorEvaluatorInstance aviatorEvaluator;

        private final List<AviatorFunction> functionList = new ArrayList<>();

        private ObjectMapper objectMapper;

        public Builder evaluatorInstance(AviatorEvaluatorInstance aviatorEvaluator) {
            Objects.requireNonNull(aviatorEvaluator, "aviatorEvaluator can not be null.");
            this.aviatorEvaluator = aviatorEvaluator;
            return this;
        }

        public Builder function(AviatorFunction function) {
            Objects.requireNonNull(function, "function can not be null.");
            this.functionList.add(function);
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder functionList(List<AviatorFunction> functionList) {
            Objects.requireNonNull(functionList, "functionList can not be null.");
            this.functionList.addAll(functionList);
            return this;
        }

        public ConfigImpl build() {
            return new ConfigImpl(aviatorEvaluator, functionList, objectMapper);
        }
    }
}
