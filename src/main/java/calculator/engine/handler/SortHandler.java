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
import calculator.engine.annotation.Internal;
import calculator.engine.script.ScriptEvaluator;
import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.language.Directive;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.common.CommonUtil.getScriptEnv;
import static calculator.engine.metadata.Directives.SORT;
import static java.util.Comparator.nullsLast;

@Internal
public class SortHandler implements FieldValueHandler{

    @Override
    public boolean supportDirective(Directive directive) {
        return Objects.equals(SORT.getName(), directive.getName());
    }

    @Override
    public void transformListResultByDirectives(ExecutionResult result,
                                                Directive directive,
                                                InstrumentationFieldCompleteParameters parameters,
                                                Executor executor,
                                                ObjectMapper objectMapper,
                                                ScriptEvaluator scriptEvaluator) {
        Supplier<Boolean> defaultReversed = () -> (Boolean) SORT.getArgument("reversed").getArgumentDefaultValue().getValue();
        String sortKey = getArgumentFromDirective(directive, "key");
        Boolean reversed = getArgumentFromDirective(directive, "reversed");
        final boolean finalReversed = reversed != null ? reversed : defaultReversed.get();

        Comparator<Object> comparator = Comparator.comparing(
                ele -> {
                    Map<String, Comparable<Object>> calMap = (Map<String, Comparable<Object>>) getScriptEnv(objectMapper, ele);
                    return calMap.get(sortKey);
                },
                // always nullLast
                nullsLast((v1, v2) -> {
                            if (finalReversed) {
                                return v2.compareTo(v1);
                            } else {
                                return v1.compareTo(v2);
                            }
                        }
                )
        );

        CollectionUtil.sortListOrArray(result.getData(), comparator);
    }

}
