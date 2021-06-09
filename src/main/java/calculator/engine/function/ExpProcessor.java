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

import calculator.engine.annotation.Internal;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;

import java.util.List;
import java.util.Map;


@Internal
public class ExpProcessor {

    public static Object calExp(AviatorEvaluatorInstance aviatorEvaluator,
                                String expression,
                                Map<String, Object> arguments) {
        return aviatorEvaluator.execute(expression, arguments, true);
    }

    public static boolean isValidExp(String expression) {
        if (expression == null || expression.isEmpty()) {
            return false;
        }

        try {
            AviatorEvaluator.compile(expression, true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> getExpArgument(String expression) {
        return AviatorEvaluator.compile(expression).getVariableNames();
    }
}
