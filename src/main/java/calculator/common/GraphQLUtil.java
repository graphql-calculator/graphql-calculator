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
import calculator.engine.metadata.DataFetcherDefinition;
import calculator.graphql.AsyncDataFetcherInterface;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.schema.AsyncDataFetcher;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnmodifiedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;


@Internal
public class GraphQLUtil {

    public static final String PATH_SEPARATOR = ".";

    /**
     * Whether this field is GraphQLList type.
     *
     * @param visitorEnv the visitor Environment of this field
     * @return true if this field is  GraphQLList
     */
    public static boolean isListNode(QueryVisitorFieldEnvironment visitorEnv){
        GraphQLType innerType = GraphQLTypeUtil.unwrapNonNull(
                visitorEnv.getFieldDefinition().getType()
        );

        return innerType instanceof GraphQLList;
    }

    /**
     * Get the set of ancestor path from top to bottom, excluding current field.
     *
     * @param visitorEnv the visitor environment representing current field
     * @return the set of ancestor path
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


    /**
     * Get the arrayList of ancestor path from top to bottom, excluding current field.
     *
     * @param visitorEnv the visitor environment representing current field
     * @return the set of ancestor path
     */
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
     * The full result path of current field, separating by '.'.
     *
     * @param environment the visitor environment representing current field
     * @return the full result path of current field
     */
    public static String pathForTraverse(QueryVisitorFieldEnvironment environment) {
        StringBuilder sb = new StringBuilder();
        QueryVisitorFieldEnvironment tmpEnv = environment;
        while (tmpEnv != null) {
            if (sb.length() == 0) {
                sb.append(tmpEnv.getField().getResultKey());
            } else {
                sb.insert(0, tmpEnv.getField().getResultKey() + PATH_SEPARATOR);
            }

            tmpEnv = tmpEnv.getParentEnvironment();
        }

        return sb.toString();
    }


    /**
     * Whether the current field is in list path.
     *
     * @param visitorEnv the visitor environment representing current field
     * @return true if the current field is in list path
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
     * Get the top task field of current field.
     *
     * @param visitorEnv the visitor environment representing current field
     * @return top task field
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

    /**
     * Determine whether the field is leaf field.
     *
     * @param fieldDefinition fieldDefinition
     * @return true if the field is leaf field
     */
    public static boolean isLeafField(GraphQLFieldDefinition fieldDefinition) {
        GraphQLOutputType type = fieldDefinition.getType();
        GraphQLUnmodifiedType unwrapAllType = GraphQLTypeUtil.unwrapAll(type);
        return unwrapAllType instanceof GraphQLScalarType;
    }


    /**
     * Get the definition information about the dataFetcher.
     *
     * @param dataFetcher dataFetcher
     * @return the definition information about the dataFetcher
     */
    public static DataFetcherDefinition getDataFetcherDefinition(DataFetcher<?> dataFetcher) {
        DataFetcherDefinition.Builder builder = new DataFetcherDefinition.Builder();
        if (dataFetcher instanceof AsyncDataFetcher) {
            builder.isGraphqlAsyncFetcher(true);
            builder.originalFetcher(dataFetcher);

            DataFetcher<?> wrappedDataFetcher = ((AsyncDataFetcher<?>) dataFetcher).getWrappedDataFetcher();
            builder.wrappedDataFetcher(wrappedDataFetcher);
            builder.actionFetcher(wrappedDataFetcher);

            builder.executor(((AsyncDataFetcher<?>) dataFetcher).getExecutor());
            return builder.build();
        }

        if (dataFetcher instanceof AsyncDataFetcherInterface) {
            builder.isCalculatorAsyncFetcher(true);
            builder.originalFetcher(dataFetcher);

            DataFetcher<?> wrappedDataFetcher = ((AsyncDataFetcherInterface<?>) dataFetcher).getWrappedDataFetcher();
            builder.wrappedDataFetcher(wrappedDataFetcher);
            builder.actionFetcher(wrappedDataFetcher);

            builder.executor(((AsyncDataFetcherInterface<?>) dataFetcher).getExecutor());
            return builder.build();
        }

        return builder.originalFetcher(dataFetcher)
                .actionFetcher(dataFetcher)
                .executor(ForkJoinPool.commonPool())
                .build();
    }

}
