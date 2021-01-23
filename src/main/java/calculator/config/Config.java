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


import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.runtime.type.AviatorFunction;

import java.util.List;

public interface Config {

    AviatorEvaluatorInstance DEFAULT_EVALUATOR = AviatorEvaluator.newInstance();

    boolean isScheduleEnable();

    List<AviatorFunction> calFunctions();

    /**
     * todo 使用指定的执行器？
     * <p>
     *     因为已经设定list-aviatorFunction了，所以 'BaseAviatorEvaluator + list-aviatorFunction 就是我们的'AviatorEvaluator'，
     *     可以不设定，也可以使用默认全局的、也可以使用新建的一个
     * <p>
     *     最好别混淆、隔离开，防止公用的用问题。
     * <p>
     *     也需要考虑到使用者就想公用，所以还是指定的好。
     * @return value
     */
    AviatorEvaluatorInstance getAviatorEvaluator();
}
