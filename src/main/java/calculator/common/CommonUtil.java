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
package calculator.common;

import calculator.engine.annotation.Internal;
import graphql.Assert;
import graphql.execution.ResultPath;
import graphql.language.Argument;
import graphql.language.BooleanValue;
import graphql.language.Directive;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;

import java.lang.reflect.Array;
import java.util.Collection;

import static calculator.common.VisitorUtil.PATH_SEPARATOR;


@Internal
public class CommonUtil {


    /**
     * Get argument value on directive by argument name.
     *
     * @param directive    dir
     * @param argumentName argument name
     * @param <T> the type of argument value
     * @return the argument value
     */
    public static <T> T getArgumentFromDirective(Directive directive, String argumentName) {
        Argument argument = directive.getArgument(argumentName);
        if (argument == null) {
            return null;
        }

        return (T) parseValue(argument.getValue());
    }


    public static Object parseValue(Value value) {
        if (value instanceof StringValue) {
            return ((StringValue) value).getValue();
        }

        if (value instanceof BooleanValue) {
            return ((BooleanValue) value).isValue();
        }

        if (value instanceof IntValue) {
            return ((IntValue) value).getValue();
        }

        if (value instanceof FloatValue) {
            return ((FloatValue) value).getValue();
        }

        if (value instanceof EnumValue) {
            return ((EnumValue) value).getName();
        }

        throw new RuntimeException("can not invoke here.");
    }


    public static boolean isValidEleName(String name) {
        try {
            Assert.assertValidName(name);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    // 获取当前字段的查询路径，使用 '.' 分割
    public static String fieldPath(final ResultPath stepInfo) {
        StringBuilder sb = new StringBuilder();
        ResultPath tmpEnv = stepInfo;
        while (tmpEnv != null) {
            if (!tmpEnv.isNamedSegment()) {
                tmpEnv = tmpEnv.getParent();
                continue;
            }

            String segmentName = tmpEnv.getSegmentName();
            if (segmentName == null || segmentName.length() == 0) {
                tmpEnv = tmpEnv.getParent();
                continue;
            }

            if (sb.length() == 0) {
                sb.append(segmentName);
            } else {
                sb.insert(0, segmentName + PATH_SEPARATOR);
            }
            tmpEnv = tmpEnv.getParent();
        }

        return sb.toString();
    }


    public static int arraySize(Object object) {
        if (object instanceof Collection) {
            return ((Collection<?>) object).size();
        }

        if (object.getClass().isArray()) {
            return Array.getLength(object);
        }

        return 0;
    }

}
