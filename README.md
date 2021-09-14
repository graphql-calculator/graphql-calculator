# graphql-calculator

![Build and Publish](https://github.com/dugenkui03/graphql-java-calculator/workflows/Build%20and%20Publish/badge.svg)
[![Latest Release](https://maven-badges.herokuapp.com/maven-central/com.graphql-java-calculator/graphql-java-calculator/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.graphql-java-calculator/graphql-java-calculator)

📖 English Documentation | [📖 中文文档](README_ZH.md) 

----------------------------------------

GraphQL Calculator is a lightweight graphql query calculation engine. 
Based on [directive](https://spec.graphql.org/draft/#sec-Language.Directives), graphql-calculator provide graphql query with the ability of field transform, field skip and orchestration.

# Features

The name and semantics of directives are inspired from the [`java.util.stream.Stream`](https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html), so they are easy to understand and use. 

The argument of directive must be static string value. query variables can't be used, because variable will make the semantics and validity of query ambiguous.

- **processing field value**: processing the fetched values and transform them to a new value; 
- **processing list field**: the collection can be easily filtered, sorted and de-duplicated;
- **processing field argument**: transform field arguments to new values before use them to fetch the field value, and the arguments of transform operation can be query variables and the fetched value of other fields;
- **flow control**: provide the extended version of `@skip` and `@include`, and the value of expression decided whether skip the operation of field-fetched;
- **data orchestration**: a field's fetched value will always could be the arguments of other fields.

# Getting Started

#### 1. Add dependency

```
implementation group: 'com.graphql-java-calculator', name: 'graphql-java-calculator', version: {version}
```

#### 2. Wrap graphql engine

##### 2.1 Create `GraphQLSource`

Create `GraphQLSource` by `DefaultGraphQLSourceBuilder`, which including wrapped graphql schema and graphql execution engine `GraphQL`.

You can config script engine through `Config`, and the default script engine is [`aviatorscript`](https://github.com/killme2008/aviatorscript.

##### 2.2 Validation

Validate the query by `Validator`, which including graphql syntax validation.

It is recommend to create `PreparsedDocumentProvider` by implementing `CalculatorDocumentCachedProvider`.

*More details in [`Example.java`](/src/test/java/calculator/example/Example.java) and [examples.graphql](/src/test/resources/examples.graphql)*

**Note**: If customized async `DataFetcher` is used, then make it implements `AsyncDataFetcherInterface`, 
and return the wrapped `DataFetcher` and the `Executor` used in async `DataFetcher` by override method.
If `graphql.Schema.Asyncdatafetcher` of 'graphql-java' is used, this operation can be ignored.

# Feature Showcase

There are all the definitions of calculation directives:
```graphql

# determine whether the field would be skipped by expression, taking query variable as script arguments
directive @skipBy(predicate: String!) on FIELD | INLINE_FRAGMENT | FRAGMENT_SPREAD

# determine whether the field would be queried by expression, taking query variable as script arguments.
directive @includeBy(predicate: String!) on FIELD | INLINE_FRAGMENT | FRAGMENT_SPREAD

# reset the annotated field by '@mock', just work for primitive type. it's easily replaced by '@map(expression)'
directive @mock(value: String!) on FIELD

# filter the list by predicate
directive @filter(predicate: String!) on FIELD

# returns a list consisting of the distinct elements of the annotated list
directive @distinct(comparator:String) on FIELD

# sort the list by specified key
directive @sort(key: String!,reversed: Boolean = false) on FIELD

# sort the list by expression result
directive @sortBy(comparator: String!, reversed: Boolean = false) on FIELD

# transform the field value by expression
directive @map(mapper:String!, dependencySources:[String!]) on FIELD

# hold the fetched value which can be acquired by calculation directives, the name is unique in query.
directive @fetchSource(name: String!, sourceConvert:String) on FIELD

# transform the argument by expression
directive @argumentTransform(argumentName:String!, operateType:ParamTransformType, expression:String, dependencySources:[String!]) repeatable on FIELD
enum ParamTransformType{
    MAP
    FILTER
    LIST_MAP
}
```


Assume we have type definition as in [examples.graphql](/src/test/resources/schema.graphql), 
Here are some examples on how to use GraphQL Calculator on graphql query.

```graphql
query basicMapValue($userIds:[Int]){
    userInfoList(userIds:$userIds)
    {
        id
        age
        firstName
        lastName
        fullName: stringHolder @map(mapper: "firstName + lastName")
    }
}

query filterUserByAge($userId:[Int]){
    userInfoList(userIds: $userId)
    @filter(predicate: "age>=18")
    {
        userId
        age
        firstName
        lastName
    }
}

query parseFetchedValueToAnotherFieldArgumentMap($itemIds:[Int]){
    commodity{
        itemList(itemIds: $itemIds){
            # save sellerId as List<Long> with unique name "sellerIdList"
            sellerId @fetchSource(name: "sellerIdList")
            name
            saleAmount
            salePrice
        }
    }

    consumer{
        userInfoList(userIds: 1)
        # transform the argument of "userInfoList" named "userIds" according to expression "sellerIdList" and expression argument, 
        # which mean replace userIds value by source named "sellerIdList"
        @argumentTransform(argumentName: "userIds", 
            operateType: MAP, 
            expression: "sellerIdList", 
            dependencySources: ["sellerIdList"]
        ){
            userId
            name
            age
        }
    }
}
```

## License

This project is released under version 2.0 of the [Apache License](https://www.apache.org/licenses/LICENSE-2.0).
