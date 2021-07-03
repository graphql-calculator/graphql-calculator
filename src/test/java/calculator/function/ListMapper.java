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

package calculator.function;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorNil;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import com.googlecode.aviator.runtime.type.AviatorString;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transform list to map with assigned value.
 */
public class ListMapper extends AbstractFunction {

    private static final String FUNCTION_NAME = "list2MapWithAssignedValue";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }


    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject listNameObj, AviatorObject expressionObj) {
        String expression = ((AviatorString) expressionObj).getLexeme(Collections.emptyMap());
        if (expression == null || expression.isEmpty()) {
            return AviatorNil.NIL;
        }

        String listName = ((AviatorString) listNameObj).getLexeme(Collections.emptyMap());
        if (!env.containsKey(listName)) {
            return AviatorNil.NIL;
        }

        List<Object> listValue = (List) env.get(listName);
        if (listValue == null) {
            return AviatorNil.NIL;
        }

        Map<Object, Object> result = listValue.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        ele -> {
                            HashMap<String, Object> expEnv = new HashMap<>(env);
                            expEnv.put("ele", ele);
                            return AviatorEvaluator.execute(expression, expEnv, true);
                        }
                ));

        return AviatorRuntimeJavaType.valueOf(result);
    }
}
