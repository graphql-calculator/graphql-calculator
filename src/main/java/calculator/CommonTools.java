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
package calculator;

import graphql.Assert;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.execution.ResultPath;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.IntValue;
import graphql.language.NamedNode;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnmodifiedType;

import java.util.List;
import java.util.Objects;

public class CommonTools {

    public static final String PATH_SEPARATOR = "#";

    public static <T> T getArgumentFromDirective(Directive directive, String argName) {
        Argument argument = directive.getArgument(argName);
        if (argument == null) {
            return null;
        }

        return (T) parseValue(argument.getValue());
    }

    /**
     * todo 解析Value：这个方法不太安全，因为没新增一种Value类型，这里都要新增一种情况
     *
     * @param value val
     * @return val
     */
    public static Object parseValue(Value value) {
        if (value instanceof StringValue) {
            return ((StringValue) value).getValue();
        }

        if (value instanceof IntValue) {
            return ((IntValue) value).getValue();
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

    public static String pathForTraverse(QueryVisitorFieldEnvironment environment) {
        StringBuilder sb = new StringBuilder();
        QueryVisitorFieldEnvironment tmpEnv = environment;
        while (tmpEnv != null) {
            String pathSeg = tmpEnv.getField().getResultKey();
            if (sb.length() == 0) {
                sb.append(pathSeg);
            } else {
                sb.insert(0, pathSeg + PATH_SEPARATOR);
            }

            tmpEnv = tmpEnv.getParentEnvironment();
        }

        return sb.toString();
    }


    public static String visitPath(QueryVisitorFieldEnvironment environment) {
        if (environment == null) {
            return "";
        }

        if (environment.getParentEnvironment() == null) {
            return environment.getField().getResultKey();
        }

        return visitPath(environment.getParentEnvironment()) + PATH_SEPARATOR + environment.getField().getResultKey();
    }

}
