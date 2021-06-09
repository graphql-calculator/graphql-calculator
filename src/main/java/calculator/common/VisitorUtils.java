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

import calculator.engine.ExecutionEngineStateParserOld;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class VisitorUtils {

    public static final String PATH_SEPARATOR = "#";

    /**
     * whether this field is GraphQLList type.
     *
     * @param visitorEnv the visitor Environment of this field
     * @return true if this field is  GraphQLList.
     */
    public static boolean isListNode(QueryVisitorFieldEnvironment visitorEnv){
        GraphQLType innerType = GraphQLTypeUtil.unwrapNonNull(
                visitorEnv.getFieldDefinition().getType()
        );

        return innerType instanceof GraphQLList;
    }

    /**
     * 获取祖先节点路径集合，不包括当前节点
     *
     * @param visitorEnv 当前节点。
     * @return 祖先节点路径集合
     */
    public static Set<String> parentPathSet(QueryVisitorFieldEnvironment visitorEnv) {
        Set<String> parentPathSet = new LinkedHashSet<>();

        QueryVisitorFieldEnvironment parentEnv = visitorEnv.getParentEnvironment();
        while (parentEnv != null) {
            parentPathSet.add(pathForTraverse(parentEnv));
            parentEnv = parentEnv.getParentEnvironment();
        }
        return parentPathSet;
    }


    // 返回当前节点的父节点：从query的子节点开始。
    public static ArrayList<String> parentPathList(QueryVisitorFieldEnvironment visitorEnv) {
        ArrayList<String> parentPathList = new ArrayList<>();

        QueryVisitorFieldEnvironment parentEnv = visitorEnv.getParentEnvironment();
        while (parentEnv != null) {
            parentPathList.add(pathForTraverse(parentEnv));
            parentEnv = parentEnv.getParentEnvironment();
        }
        Collections.reverse(parentPathList);
        return(parentPathList);
    }

    /**
     * 当前节点路径，使用 # 分割
     *
     * @param environment 当前节点
     * @return 当前节点路径
     */
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

    /**
     * 当前节点是否是列表字段中的元素
     *
     * @param visitorEnv 当前节点
     * @return 是列表字段中的元素则为true
     */
    public static boolean isInList(QueryVisitorFieldEnvironment visitorEnv) {
        QueryVisitorFieldEnvironment parentEnv = visitorEnv.getParentEnvironment();
        while (parentEnv != null) {
            GraphQLType innerType = GraphQLTypeUtil.unwrapNonNull(
                    parentEnv.getFieldDefinition().getType()
            );

            if (innerType instanceof GraphQLList) {
                return true;
            }
            parentEnv = parentEnv.getParentEnvironment();
        }

        return false;
    }

    /**
     * 获取当前任务节点的所依赖的顶层任务节点，
     * 顶层任务节点查找逻辑见 {@link ExecutionEngineStateParserOld#handle}
     *
     * @param visitorEnv 当前任务节点
     * @return 当前任务节点的所依赖的顶层任务节点
     */
    public static QueryVisitorFieldEnvironment getTopTaskEnv(QueryVisitorFieldEnvironment visitorEnv) {
        QueryVisitorFieldEnvironment result = visitorEnv;
        QueryVisitorFieldEnvironment tmpEnv = visitorEnv;
        while (tmpEnv != null) {
            GraphQLType innerType = GraphQLTypeUtil.unwrapNonNull(
                    tmpEnv.getFieldDefinition().getType()
            );

            if (innerType instanceof GraphQLList) {
                result = tmpEnv;
            }
            tmpEnv = tmpEnv.getParentEnvironment();
        }

        return result;
    }

}
