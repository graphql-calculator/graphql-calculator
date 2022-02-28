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

import calculator.engine.ObjectMapper;
import calculator.engine.script.ScriptEvaluator;
import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.language.Directive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class FieldValueHandlerComposite implements FieldValueHandler {

    private final List<FieldValueHandler> fieldValueHandlers = new ArrayList<>();

    private final Map<String, FieldValueHandler> HANDLER_CACHE = new ConcurrentHashMap<>(256);

    public void addFieldValueHandler(FieldValueHandler fieldValueHandler) {
        Objects.requireNonNull(fieldValueHandler, "fieldValueHandler can not be null.");
        fieldValueHandlers.add(fieldValueHandler);
    }

    @Override
    public boolean supportDirective(Directive directive) {
        return getFieldValueHandler(directive) != null;
    }

    @Override
    public void transformListResultByDirectives(ExecutionResult result,
                                                Directive directive,
                                                InstrumentationFieldCompleteParameters parameters,
                                                Executor executor,
                                                ObjectMapper objectMapper,
                                                ScriptEvaluator scriptEvaluator) {

        FieldValueHandler fieldValueHandler = getFieldValueHandler(directive);
        fieldValueHandler.transformListResultByDirectives(
                result, directive, parameters,
                executor, objectMapper, scriptEvaluator
        );
    }


    private FieldValueHandler getFieldValueHandler(Directive directive) {
        String directiveName = directive.getName();
        FieldValueHandler fieldValueHandler = HANDLER_CACHE.get(directiveName);
        if (fieldValueHandler != null) {
            return fieldValueHandler;
        }

        for (FieldValueHandler valueHandler : fieldValueHandlers) {
            if (valueHandler.supportDirective(directive)) {
                HANDLER_CACHE.put(directiveName, valueHandler);
                return valueHandler;
            }
        }

        return null;
    }
}
