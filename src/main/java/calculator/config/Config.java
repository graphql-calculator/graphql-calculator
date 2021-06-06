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


import calculator.engine.ObjectMapper;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.runtime.type.AviatorFunction;

import java.util.List;
import java.util.concurrent.Executor;

public interface Config {

    // 指定 执行器 是为了防止公用的用问题，例如同名函数
    AviatorEvaluatorInstance getAviatorEvaluator();

    List<AviatorFunction> functions();


    ObjectMapper getObjectMapper();

    /**
     * @return Get the thread pool which used in {@link calculator.engine.ExecutionEngine}.
     */
    Executor getExecutor();
}
