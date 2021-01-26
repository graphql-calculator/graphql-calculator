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

import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.SourceLocation;
import graphql.util.TraverserContext;
import graphql.validation.ValidationError;

import java.util.LinkedList;
import java.util.List;

public abstract class QueryValidationVisitor implements QueryVisitor {

    // fixme 校验结果
    private List<ValidationError> errors;


    public QueryValidationVisitor() {
        this.errors = new LinkedList<>();
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void addValidError(SourceLocation location, String errorMsg) {
        ValidationError error = ValidationError.newValidationError()
                .sourceLocation(location)
                .description(errorMsg)
                .build();
        errors.add(error);
    }
}
