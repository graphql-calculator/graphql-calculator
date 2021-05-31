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
import java.util.stream.Collectors;

/**
 * 将list按照指定的key转换为map，重复的key则忽略
 *
 * 示例见  calculator.directives.CalculateDirectivesTest#testNodeFunction
 */
@Beta
public class ToMap extends AbstractFunction {

    private static final String FUNCTION_NAME = "toMap";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    // 调用了三次
    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject listElement, AviatorObject fieldNameObj) {
        String fieldName = ((AviatorString) fieldNameObj).getLexeme(Collections.emptyMap());
        // todo 假设这里是map，不对的
        List<Map> list = (List<Map>) listElement.getValue(Collections.emptyMap());
        if (list == null || list.isEmpty()) {
            return AviatorRuntimeJavaType.valueOf(Collections.emptyMap());
        }
        Map<Object, Map> mapByKey = list.stream().collect(Collectors.toMap(
                map -> map.get(fieldName),
                map -> map,
                (v1, v2) -> v1
        ));

        return AviatorRuntimeJavaType.valueOf(mapByKey);
    }
}
