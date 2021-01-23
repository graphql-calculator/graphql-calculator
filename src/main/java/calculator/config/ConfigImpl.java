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

import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.runtime.type.AviatorFunction;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 默认配置实现
 */
public class ConfigImpl implements Config {

    private boolean isScheduleEnable;

    private List<AviatorFunction> functionList;

    private AviatorEvaluatorInstance evaluatorInstance;

    public ConfigImpl(boolean isScheduleEnable, AviatorEvaluatorInstance evaluatorInstance, List<AviatorFunction> functionList) {
        this.isScheduleEnable = isScheduleEnable;
        this.evaluatorInstance = Optional.ofNullable(evaluatorInstance).orElse(Config.DEFAULT_EVALUATOR);
        this.functionList = functionList;
    }

    @Override
    public boolean isScheduleEnable() {
        return isScheduleEnable;
    }

    @Override
    public List<AviatorFunction> calFunctions() {
        return Collections.unmodifiableList(functionList);
    }

    @Override
    public AviatorEvaluatorInstance getAviatorEvaluator() {
        return evaluatorInstance;
    }

    public static Builder newConfig() {
        return new Builder();
    }

    public static class Builder {

        private boolean isScheduleEnable;

        private AviatorEvaluatorInstance evaluatorInstance;

        private List<AviatorFunction> functionList = new LinkedList<>();

        public Builder isScheduleEnable(boolean val) {
            isScheduleEnable = val;
            return this;
        }


        public Builder evaluatorInstance(AviatorEvaluatorInstance evaluatorInstance) {
            this.evaluatorInstance = evaluatorInstance;
            return this;
        }

        public Builder function(AviatorFunction function) {
            Objects.requireNonNull(function, "function can't be null.");
            this.functionList.add(function);
            return this;
        }

        public Builder functionList(List<AviatorFunction> functionList) {
            Objects.requireNonNull(functionList, "functionList can't be null.");
            for (AviatorFunction function : functionList) {
                function(function);
            }
            return this;
        }


        public ConfigImpl build() {
            return new ConfigImpl(isScheduleEnable, evaluatorInstance, functionList);
        }
    }
}
