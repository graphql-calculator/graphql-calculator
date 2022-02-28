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

package calculator.engine.handler;

import calculator.common.CollectionUtil;
import calculator.engine.ObjectMapper;
import calculator.engine.script.ScriptEvaluator;
import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.language.Directive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.common.CommonUtil.getScriptEnv;
import static calculator.engine.metadata.Directives.FILTER;

public class FilterHandler implements FieldValueHandler{

    @Override
    public boolean supportDirective(Directive directive) {
        return Objects.equals(FILTER.getName(), directive.getName());
    }

    @Override
    public void transformListResultByDirectives(ExecutionResult result,
                                                Directive directive,
                                                InstrumentationFieldCompleteParameters parameters,
                                                Executor executor,
                                                ObjectMapper objectMapper,
                                                ScriptEvaluator scriptEvaluator) {
        String predicate = getArgumentFromDirective(directive, "predicate");

        Predicate<Object> willKeep = ele -> {
            Map<String, Object> fieldMap = (Map<String, Object>) getScriptEnv(objectMapper, ele);
            Map<String, Object> sourceEnv = new LinkedHashMap<>();
            fieldMap.putAll(sourceEnv);
            return (Boolean) scriptEvaluator.evaluate(predicate, fieldMap);
        };

        CollectionUtil.filterCollection(result.getData(), willKeep);
    }

}
