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

import calculator.engine.metadata.NodeTask;
import graphql.Assert;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.execution.ResultPath;
import graphql.language.Argument;
import graphql.language.BooleanValue;
import graphql.language.Directive;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;

import static calculator.common.VisitorUtils.PATH_SEPARATOR;

public class Tools {


    public static <T> T getArgumentFromDirective(Directive directive, String argName) {
        Argument argument = directive.getArgument(argName);
        if (argument == null) {
            return null;
        }

        return (T) parseValue(argument.getValue());
    }

    /**
     * 该放放只用于当前组件，场景有限。
     *
     * @param value val
     * @return val
     */
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

        // todo 绑定
        if (value instanceof EnumValue) {
            return ((EnumValue) value).getName();
        }

        return null;
    }


    public static boolean isValidEleName(String name) {
        try {
            Assert.assertValidName(name);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 返回当前字段的查询路径名称，路径segment别名优先
     *
     * <p>
     * todo 确定 list 是否有影响。有，需要确认下具体影响
     *
     * @param stepInfo val
     * @return val
     */
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

    /**
     * 获取 environment 表示的当前节点的结果路径
     *
     * @param environment 被访问的Field节点环境变量
     * @return 当前节点的绝对路径，用 {@link #PATH_SEPARATOR} 分割
     */
    public static String visitPath(QueryVisitorFieldEnvironment environment) {
        if (environment == null) {
            return "";
        }

        if (environment.getParentEnvironment() == null) {
            return environment.getField().getResultKey();
        }

        return visitPath(environment.getParentEnvironment()) + PATH_SEPARATOR + environment.getField().getResultKey();
    }


    // 当前任务是否嵌套在list路径中
    public static boolean isInListPath(NodeTask task) {
        // Query root
        if (task.getParent() == null) {
            return false;
        }

        NodeTask parentNode = task.getParent();
        while (parentNode != null) {
            if (parentNode.isList()) {
                return true;
            }

            parentNode = parentNode.getParent();
        }

        return false;
    }

}
