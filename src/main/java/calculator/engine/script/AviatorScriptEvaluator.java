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
package calculator.engine.script;

import calculator.engine.annotation.PublicApi;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.runtime.function.AbstractFunction;

import java.util.List;
import java.util.Map;


@PublicApi
public class AviatorScriptEvaluator implements ScriptEvaluator {

    private static final AviatorScriptEvaluator DEFAULT_INSTANCE = new AviatorScriptEvaluator();

    public static AviatorScriptEvaluator getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    @Override
    public Object evaluate(String script, Map<String, Object> arguments) {
        return AviatorEvaluator.execute(script, arguments, true);
    }

    @Override
    public ValidateInfo isValidScript(String expression) {
        if (expression == null) {
            return new ValidateInfo(false, "script is null.");
        }

        if (expression.isEmpty()) {
            return new ValidateInfo(false, "script is empty.");
        }

        try {
            AviatorEvaluator.compile(expression, true);
            return new ValidateInfo(true);
        } catch (Exception e) {
            return new ValidateInfo(false, e.getMessage());
        }
    }

    @Override
    public List<String> getScriptArgument(String expression) {
        return AviatorEvaluator.compile(expression, true).getVariableNames();
    }

    public void addFunction(AbstractFunction function) {
        AviatorEvaluator.addFunction(function);
    }
}
