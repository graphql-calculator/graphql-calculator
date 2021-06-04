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

import static calculator.engine.metadata.WrapperState.FUNCTION_KEY;

import calculator.engine.annotation.Beta;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorNil;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import calculator.engine.metadata.NodeTask;
import calculator.engine.metadata.WrapperState;
import com.googlecode.aviator.runtime.type.AviatorString;

@Beta
public class GetByNode extends AbstractFunction {

    private static final String FUNCTION_NAME = "getByNode";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
        if (env == null || env.isEmpty() || !env.containsKey(FUNCTION_KEY)) {
            return AviatorNil.NIL;
        }

        String nodeName = ((AviatorString) arg).getLexeme(Collections.emptyMap());
        WrapperState state = (WrapperState) env.get(FUNCTION_KEY);
        if (!state.getSequenceTaskByNode().containsKey(nodeName)) {
            return AviatorNil.NIL;
        }

        List<String> taskPaths = state.getSequenceTaskByNode().get(nodeName);
        String taskPath = taskPaths.get(taskPaths.size() - 1);
        NodeTask futureTask = state.getTaskByPath().get(taskPath);
        Object taskResult = futureTask.getFuture().join();

        return AviatorRuntimeJavaType.valueOf(taskResult);
    }

}
