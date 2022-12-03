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

package calculator.generator;


import calculator.engine.PartitionDataFetcher;
import graphql.ExecutionInput;
import graphql.ParseAndValidate;
import graphql.ParseAndValidateResult;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.InvalidSyntaxException;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.validation.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * NotNull信息也需要打印。
 *
 * todo
 * 1. 打印schema对应的持久化对象；或者schema对应的对象；
 * 2. 打印query对应的持久化对象：考虑别名；或者query对应的对象
 * 3. 生成的语言选择：java、golang。
 * 4. 根据java对象或者golang对象生成schema。
 * 5. 根据json/java/go对象生成xx。
 * 6. 注释
 */
public class CodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(CodeGenerator.class);


    // --------------- String + GraphQLSchema

    public static String generator(String query, GraphQLSchema graphQLSchema) throws GeneratorException {
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build();
        ParseAndValidateResult parseAndValidateResult = ParseAndValidate.parseAndValidate(graphQLSchema, executionInput);

        if (parseAndValidateResult.getSyntaxException() != null) {
            InvalidSyntaxException syntaxException = parseAndValidateResult.getSyntaxException();
            String errorMsg = String.format("generate error type: InvalidSyntaxException ,message:%s, sourcePreview:%s, offendingToken:%s, location:%s",
                    syntaxException.getLocation(), syntaxException.getSourcePreview(), syntaxException.getOffendingToken(), syntaxException.getLocation().toString()
            );
            throw new GeneratorException(errorMsg);
        }

        if (parseAndValidateResult.getValidationErrors() != null && !parseAndValidateResult.getValidationErrors().isEmpty()) {
            List<String> errorInfo = new ArrayList<>();
            errorInfo.add("generate error type: ValidationError\n");
            for (ValidationError validationError : parseAndValidateResult.getValidationErrors()) {
                errorInfo.add(validationError.toString() + "\n");
            }
            throw new GeneratorException(errorInfo.toString());
        }

        Document queryDocument = parseAndValidateResult.getDocument();
        // todo 应该根据配置，选择生成java还是golang
        Map<String, Map<String, String>> classInfo = parseQueryDocument(queryDocument, graphQLSchema);

        return classInfoDesc(classInfo);
    }

    /**
     * @param classInfo  <typeName,<fieldName,fieldType>>
     * @return
     */
    private static String classInfoDesc(Map<String, Map<String, String>> classInfo) {
        if (classInfo == null || classInfo.isEmpty()) {
            return "";
        }

        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, Map<String, String>> clzInfo : classInfo.entrySet()) {
            sb.append(String.format("public class %s {\n", clzInfo.getKey()));
            // field definition
            for (Map.Entry<String, String> fieldsEntry : clzInfo.getValue().entrySet()) {
                sb.append(String.format("\tprivate %s %s;\n", xxxType(fieldsEntry.getValue()), fieldsEntry.getKey()));
            }
            sb.append("\n");
            // getter and setter
            // public ObjectMapper getObjectMapper() {
            //        return objectMapper;
            // }
            for (Map.Entry<String, String> fieldsEntry : clzInfo.getValue().entrySet()) {
                sb.append(String.format("\tpublic %s get%s() {\n", xxxType(fieldsEntry.getValue()), upperFirstCharactor(fieldsEntry.getKey())));
                sb.append(String.format("\t\treturn %s;\n", fieldsEntry.getKey()));
                sb.append("\t}\n\n");
            }

            // public void setXxxx(ClassType xxx) {
            //      this.xxx = xxx;
            // }
            for (Map.Entry<String, String> fieldsEntry : clzInfo.getValue().entrySet()) {
                sb.append(String.format("\tpublic void set%s(%s %s) {\n",upperFirstCharactor(fieldsEntry.getKey()), xxxType(fieldsEntry.getValue()),fieldsEntry.getKey()));
                sb.append(String.format("\t\tthis.%s = %s;\n", fieldsEntry.getKey(), fieldsEntry.getKey()));
                sb.append("\t}\n\n");
            }

            sb.append("}\n");
        }

        return sb.toString();
    }

    private static String xxxType(String originalType) {
        if ("Int".equals(originalType)) {
            return "Integer";
        }
        return originalType;
    }

    private static String upperFirstCharactor(String key) {
        return key.substring(0, 1).toUpperCase() + (key.length() > 1 ? key.substring(1) : "");
    }


    /**
     * @param queryDocument
     * @param graphQLSchema
     * @return  <typeName,<fieldName,fieldType>>
     */
    private static Map<String, Map<String, String>> parseQueryDocument(Document queryDocument, GraphQLSchema graphQLSchema) {
        Objects.requireNonNull(queryDocument);
        Objects.requireNonNull(queryDocument.getDefinitions());

        if(queryDocument.getDefinitions().isEmpty()){
            throw new IllegalArgumentException("document root definitions is empty");
        }

        OperationDefinition rootDefinition = (OperationDefinition)queryDocument.getDefinitions().get(0);
        String rootClassName = rootDefinition.getName();
        if(rootClassName==null){
            rootClassName = "GenerateRootClassName";
        }

        Map<String, FragmentDefinition> fragmentDefByName = parseFragmentDef(queryDocument);

        // <typeName,<fieldName,fieldType>>
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Selection selection : rootDefinition.getSelectionSet().getSelections()) {
            parseSelections(fragmentDefByName, selection, graphQLSchema, "Query", result);
        }

        return result;
    }

    private static Map<String, FragmentDefinition> parseFragmentDef(Document queryDocument) {
        return queryDocument.getDefinitions().stream()
                .filter(def -> def instanceof FragmentDefinition)
                .map(def -> (FragmentDefinition) def)
                .collect(Collectors.toMap(
                        FragmentDefinition::getName,
                        Function.identity(),
                        (x, y) -> {
                            logger.warn("duplicate FragmentDefinition:\n"+x.toString()+"\n"+y.toString()+"\n return first value");
                            return x;
                        }
                ));
    }

    private static void parseSelections(Map<String, FragmentDefinition> fragmentDefByName, Selection selection, GraphQLSchema graphQLSchema, String typeName, Map<String, Map<String, String>> result) {
        if (selection instanceof Field) {
            Field field =  (Field)selection;

            GraphQLObjectType objectType = graphQLSchema.getObjectType(typeName);
            GraphQLFieldDefinition fieldDefinition = objectType.getFieldDefinition(field.getName());

            Map<String, String> typeDef = result.get(typeName);
            if (typeDef == null){
                typeDef = new LinkedHashMap<>();
                result.put(typeName,typeDef);
            }
            String fieldTypeDesc = parseTypeDesc(fieldDefinition.getType());
            typeDef.put(field.getResultKey(), fieldTypeDesc);

            GraphQLType metaType = GraphQLTypeUtil.unwrapAllAs(fieldDefinition.getType());

            SelectionSet selectionSet = field.getSelectionSet();
            if (selectionSet != null && selectionSet.getSelections() != null) {
                for (Selection sel : selectionSet.getSelections()) {
                    parseSelections(fragmentDefByName, sel, graphQLSchema, parseTypeDesc(metaType), result);
                }
            }

        } else if (selection instanceof FragmentSpread) {
            String fragmentName = ((FragmentSpread) selection).getName();

            FragmentDefinition fragmentDefinition = fragmentDefByName.get(fragmentName);
            if (fragmentDefinition ==null){
                logger.warn("FragmentDefinition named "+fragmentName+" can not find");
                return;
            }

            SelectionSet selectionSet = fragmentDefinition.getSelectionSet();
            if (selectionSet != null && selectionSet.getSelections() != null) {
                for (Selection sel : selectionSet.getSelections()) {
                    parseSelections(fragmentDefByName, sel, graphQLSchema, typeName, result);
                }
            }

            System.out.println(fragmentName);

//            List<Definition> collect = queryDocument.getDefinitions().stream()
//                    .filter(s -> s.getClass() == FragmentSpread.class && Objects.equals(((FragmentSpread) s).getName(), fragmentName))
//                    .collect(Collectors.toList());

        } else if (selection instanceof InlineFragment) {
            SelectionSet selectionSet = ((InlineFragment) selection).getSelectionSet();
            if (selectionSet != null && selectionSet.getSelections() != null) {
                for (Selection sel : selectionSet.getSelections()) {
                    parseSelections(fragmentDefByName, sel, graphQLSchema, typeName, result);
                }
            }
        }
    }

    private static String parseTypeDesc(GraphQLType type) {
        if (type instanceof GraphQLObjectType) {
            return ((GraphQLObjectType) type).getName();
        } else if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).getName();
        } else if (type instanceof GraphQLList) {
            return "List<" + parseTypeDesc(((GraphQLList) type).getWrappedType()) + ">";
        } else if (type instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) type).getName();
        } else {
            throw new RuntimeException("un-support type");
        }
    }
}
