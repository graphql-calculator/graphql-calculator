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


import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorJavaType;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;

import java.util.Map;

import static java.lang.String.format;

public class IgnoreConsumer extends AbstractFunction {

    private static final String FUNCTION_NAME = "ignore";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject element) {

        String msg = format("ignored function, env = %s , name = %s",
                env != null ? env.toString() : null, ((AviatorJavaType) element).getName()
        );
        System.out.println(msg);

        return AviatorRuntimeJavaType.valueOf(null);
    }
}
