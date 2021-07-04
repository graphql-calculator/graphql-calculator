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
package calculator.validation;

import calculator.config.Config;
import calculator.engine.annotation.PublicApi;
import graphql.ExecutionInput;
import graphql.ParseAndValidate;
import graphql.ParseAndValidateResult;
import graphql.analysis.QueryTraverser;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;

import java.util.Collections;

@PublicApi
public class Validator {

    public static ParseAndValidateResult validateQuery(String query, GraphQLSchema wrappedSchema, Config wrapperConfig) {

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build();
        ParseAndValidateResult origPVResult = ParseAndValidate.parseAndValidate(wrappedSchema, executionInput);
        if (origPVResult.isFailure()) {
            return origPVResult;
        }

        Document document = Parser.parse(query);
        QueryTraverser traverser = QueryTraverser.newQueryTraverser()
                .schema(wrappedSchema)
                .document(document)
                .variables(Collections.emptyMap()).build();

        BasicRule basicRule = new BasicRule(wrapperConfig.getScriptEvaluator());
        traverser.visitDepthFirst(basicRule);
        if (!basicRule.getErrors().isEmpty()) {
            return ParseAndValidateResult.newResult().validationErrors(basicRule.getErrors()).build();
        }


        SourceRule nodeRule = new SourceRule(
                wrapperConfig.getScriptEvaluator(),
                basicRule.getSourceWithAnnotatedField(),
                basicRule.getSourceWithTopTask(),
                basicRule.getSourceWithAncestorPath(),
                basicRule.getFieldWithTopTask(),
                basicRule.getSourceUsedByField(),
                basicRule.getFieldWithAncestorPath()
        );
        traverser.visitDepthFirst(nodeRule);
        // 不用在返回没有使用的节点，因为脏数据可能导致分析不够准确
        if (!nodeRule.getErrors().isEmpty()) {
            return ParseAndValidateResult.newResult().validationErrors(nodeRule.getErrors()).build();
        }

        // 是否有未使用的node节点
        if (!nodeRule.getUnusedSource().isEmpty()) {
            String errorMsg = String.format(" unused node: %s.", nodeRule.getUnusedSource().toString());
            ValidationError error = ValidationError.newValidationError().description(errorMsg).build();
            return ParseAndValidateResult.newResult().validationErrors(Collections.singletonList(error)).build();
        }

        return ParseAndValidateResult.newResult().document(document).build();
    }


}
