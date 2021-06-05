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
package calculator.validate;

import graphql.ExecutionInput;
import graphql.ParseAndValidate;
import graphql.ParseAndValidateResult;
import graphql.analysis.QueryTraverser;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Validator {

    /**
     * kp 使用场景，在第一次执行的时候检测一次即可。
     *      校验规则：
     *          1. @node是否重名;
     *          2. @link中是否有重名；
     *          3. @link使用的node不存在、node指向的参数不存在；
     *
     * @param query  查询语句
     * @param schema 上下文，在上下文中查看是否有所用的指令
     * @return 校验结果
     */
    public static ParseAndValidateResult validateQuery(String query, GraphQLSchema schema) {

        // 原始校验
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build();
        ParseAndValidateResult origPVResult = ParseAndValidate.parseAndValidate(schema, executionInput);
        if (origPVResult.isFailure()) {
            return origPVResult;
        }

        QueryTraverser traverser = QueryTraverser.newQueryTraverser()
                .schema(schema)
                .document(Parser.parse(query))
                .variables(Collections.emptyMap()).build();

        BasicRule basicRule = new BasicRule();
        traverser.visitDepthFirst(basicRule);
        if (!basicRule.getErrors().isEmpty()) {
            return ParseAndValidateResult.newResult().validationErrors(basicRule.getErrors()).build();
        }


        NodeRule nodeRule = new NodeRule(
                basicRule.getNodeWithAnnotatedField(),
                basicRule.getNodeWithTopTask(),
                basicRule.getNodeWithAncestorPath()
        );
        traverser.visitDepthFirst(nodeRule);
        // 不用在返回没有使用的节点，因为脏数据可能导致分析不够准确
        if (!nodeRule.getErrors().isEmpty()) {
            return ParseAndValidateResult.newResult().validationErrors(nodeRule.getErrors()).build();
        }

        // 是否有未使用的node节点
        if (!nodeRule.getUnusedNode().isEmpty()) {
            String errorMsg = String.format(" unused node: %s.", nodeRule.getUnusedNode().toString());
            ValidationError error = ValidationError.newValidationError().description(errorMsg).build();
            return ParseAndValidateResult.newResult().validationErrors(Collections.singletonList(error)).build();
        }

        return ParseAndValidateResult.newResult().build();
    }


}
