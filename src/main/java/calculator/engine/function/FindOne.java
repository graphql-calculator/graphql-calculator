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

package calculator.engine.function;

import calculator.engine.annotation.Beta;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import com.googlecode.aviator.runtime.type.AviatorString;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Beta
public class FindOne extends AbstractFunction {
    private static final String FUNCTION_NAME = "findOne";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    // 调用了三次
    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject listElement, AviatorObject elementFieldName, AviatorObject envKey) {

        String elementKey = ((AviatorString) elementFieldName).getLexeme(Collections.emptyMap());

        String envLexeme = ((AviatorString) envKey).getLexeme(Collections.emptyMap());
        Object targetValue = env.get(envLexeme);

        List<Map> listValue = (List)listElement.getValue(Collections.emptyMap());
        Map result = listValue.stream().filter(map -> Objects.equals(map.get(elementKey), targetValue)).findFirst().orElse(null);

        return AviatorRuntimeJavaType.valueOf(result);
    }
}
