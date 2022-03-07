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
import calculator.engine.annotation.Internal;
import graphql.language.Directive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static calculator.common.CommonUtil.getArgumentFromDirective;
import static calculator.common.CommonUtil.getScriptEnv;
import static calculator.engine.metadata.Directives.DISTINCT;

@Internal
public class DistinctHandler implements FieldValueHandler {

    @Override
    public boolean supportDirective(Directive directive) {
        return Objects.equals(DISTINCT.getName(), directive.getName());
    }

    @Override
    public void transformListResultByDirectives(HandleEnvironment handleEnvironment) {
        String comparatorExpression = getArgumentFromDirective(handleEnvironment.getDirective(), "comparator");
        boolean emptyComparator = comparatorExpression == null;

        Function<Object, Integer> comparator = ele -> {
            if (ele == null) {
                return 0;
            }

            if (emptyComparator) {
                return System.identityHashCode(ele);
            }

            Map<String, Object> scriptEnv = new LinkedHashMap<>();
            Map<String, Object> calMap = (Map<String, Object>) getScriptEnv(handleEnvironment.getObjectMapper(),ele);
            if (calMap != null) {
                scriptEnv.putAll(calMap);
            }
            Object evaluate = handleEnvironment.getScriptEvaluator().evaluate(comparatorExpression, scriptEnv);
            return Objects.hashCode(evaluate);
        };

        CollectionUtil.distinctCollection(handleEnvironment.getResult().getData(), comparator);

    }

}
