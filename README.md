准备正式版本中...

# graphql-java-calculator

基于[指令系统](https://spec.graphql.org/draft/#sec-Language.Directives)，`graphql-java-calculator`为`graphql`查询提供了数据编排、动态计算和控制流的能力。

![Build and Publish](https://github.com/dugenkui03/graphql-java-calculator/workflows/Build%20and%20Publish/badge.svg)

# 特性

详情参考**详情文档**小结。

- 数据编排：将指定字段的获取结果设为全局上下文，作为参数请求其他字段，补全其他字段数据；
- 控制流：将指定字段的获取结果设为全局上下文，作为控制流判断条件的参数变量，判断是否请求解析当前类型数据；
- 对结果进行补全、过滤、排序、计算转换等，其他字段数据可作为这些操作的参数变量。


# 快速开始

#### 1. 引入依赖

```
<dependency>
    <groupId>com.graphql-java-calculator</groupId>
    <artifactId>graphql-java-calculator</artifactId>
    <version></version>
</dependency>
```

#### 2. 包装执行引擎

##### 2.1 继承`calculator.graphql.AsyncDataFetcherInterface`

如果项目中使用了异步化`DataFetcher`，则使其则继承`AsyncDataFetcherInterface`，
并在方法实现中返回该`DataFetcher`包装的取数`DataFetcher`和使用的线程池。

##### 2.2 创建 `GraphQLSource`

通过指定的配置创建`GraphQLSource`对象，该对象`GraphQLSchema`和`GraphQL`，
配置类`Config`包含脚本执行引擎、对象转换工具和调度引擎使用的线程池。

#### 2.3 执行前校验

对使用了计算指令的查询使用`Validator`进行语法校验是否合法，建议实现`CalculatorDocumentCachedProvider`缓存校验结果，该类包含语法校验逻辑。

完整示例如下：
```java
public class Example {

    static class DocumentParseAndValidationCache extends CalculatorDocumentCachedProvider {

        private final Map<String, PreparsedDocumentEntry> cache = new LinkedHashMap<>();

        @Override
        public PreparsedDocumentEntry getDocumentFromCache(ExecutionInput executionInput,
                                                           Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
            return cache.get(executionInput.getQuery());
        }

        @Override
        public void setDocumentCache(ExecutionInput executionInput,
                                     PreparsedDocumentEntry cachedValue) {
            cache.put(executionInput.getQuery(), cachedValue);
        }
    }

    public static void main(String[] args) {

        /**
         * step 1
         * Make async dataFetcher implements {@link AsyncDataFetcherInterface}
         *
         * step 2
         * Create {@link GraphQLSource} by {@link Config}: including wrapped graphql schema and GraphQL object.
         * create Config, and get wrapped schema with the ability of
         * orchestrate and dynamic calculator and control flow, powered by directives,
         * and create GraphQL with wrapped schema and ExecutionEngine.
         *
         * step 3:
         * validate the query: {@code ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema).}
         * It is recommend to create `PreparsedDocumentProvider` by implementing {@link DocumentParseAndValidationCache}.
         */

        GraphQLSchema schema = SchemaHolder.getSchema();


        Config wrapperConfig = ConfigImpl.newConfig()
                .scriptEvaluator(AviatorScriptEvaluator.getDefaultInstance())
                .objectMapper(new DefaultObjectMapper())
                .threadPool(Executors.newCachedThreadPool())
                .build();


        DefaultGraphQLSourceBuilder graphqlSourceBuilder = new DefaultGraphQLSourceBuilder();
        GraphQLSource graphqlSource = graphqlSourceBuilder
                .wrapperConfig(wrapperConfig)
                .originalSchema(schema)
                .preparsedDocumentProvider(new DocumentParseAndValidationCache()).build();

        String query = ""
                + "query mapListArgument($itemIds: [Int]){ \n" +
                "    commodity{\n" +
                "        itemList(itemIds: $itemIds)\n" +
                "        @argumentTransform(argumentName: \"itemIds\",operateType: LIST_MAP,expression: \"ele*10\")\n" +
                "        {\n" +
                "            itemId\n" +
                "            name\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ExecutionInput input = ExecutionInput
                .newExecutionInput(query)
                .variables(Collections.singletonMap("itemIds", Arrays.asList(1, 2, 3)))
                .build();

        ExecutionResult result = graphqlSource.graphQL().execute(input);
        // consumer result
    }
}
```

# 详情文档

以[测试schema](https://github.com/dugenkui03/graphql-java-calculator/blob/refactorForSchedule/src/test/resources/schema.graphql)为例，
对`graphql-java-calculator`的数据编排、控制流、结果处理和计算转换等进行说明。

表达式语法使用了[`aviatorscript`](https://github.com/killme2008/aviatorscript)，`aviator`当前是`graphql-java-calculator`的默认表达行引擎，
可通过`ScriptEvaluator`和`Config`自定义表达式引擎。

#### 数据编排

数据编排的主要形式为请求A类型数据时其输入参数为B类型的结果、或者需要B类型结果对A类型输入参数进行过滤、转换处理。示例如下。

- 变量只有红包id、查询该红包绑定的商品详情。
```graphql
query getItemListBindingCouponIdAndFilterUnSaleItems ( $couponId: Int) {
    commodity{
        itemList(itemIds: 1)
        @argumentTransform(argumentName: "itemIds", operateType: MAP,dependencySource: "itemIdList",expression: "itemIdList")
        {
            itemId
            name
            salePrice
            onSale
            # sellerId
        }
    }

    marketing{
        coupon(couponId: $couponId){
            bindingItemIds
            @fetchSource(name: "itemIdList")
        }
    }
}
```

#### 结果过滤

- 查询逻辑同`数据编排`，但过滤掉没有在售的商品。
```graphql
query getItemListBindingCouponIdAndFilterUnSaleItems ( $couponId: Int) {
    commodity{
        itemList(itemIds: 1)
        @argumentTransform(argumentName: "itemIds", operateType: MAP,dependencySource: "itemIdList",expression: "itemIdList")
        @filter(predicate: "onSale")
        {
            itemId
            name
            salePrice
            onSale
            # sellerId
        }
    }

    marketing{
        coupon(couponId: $couponId){
            bindingItemIds
            @fetchSource(name: "itemIdList")
        }
    }
}
```


#### 参数过滤

- 请求商品信息前，过滤掉没有指定绑定红包id的商品id，红包绑定的商品id来自其他字段数据。
```graphql
query filterItemListByBindingCouponIdAndFilterUnSaleItems ( $couponId: Int,$itemIds: [Int]) {
    commodity{
        itemList(itemIds: $itemIds)
        @argumentTransform(argumentName: "itemIds", operateType: FILTER,dependencySource: "itemIdList",expression: "listContain(itemIdList,ele)")
        {
            itemId
            name
            salePrice
            onSale
        }
    }

    marketing{
        coupon(couponId: $couponId){
            bindingItemIds
            @fetchSource(name: "itemIdList")
        }
    }
}
```

#### 控制流

控制流主要为根据条件，判断是否请求某个类型数据、或者请求哪个类型数据。

控制流通过 **@skipBy**进行控制 `directive @skipBy(expression: String!, dependencySource: String) on FIELD`。

通过 **@skipBy** 可实现类似 `switch-case`的控制流，
```
// @fetchSource
switch(value):
    // @skipBy(value,judgeFunction)
    case(judgeFunction_1(value)): opration_1;
    case(judgeFunction_2(value)): opration_2;
    case(judgeFunction_2(value)): opration_2;

``` 
和if控制流 `if(conditioin){...add return value...}`。

```graphql
# 如果用户不在 ab实验实验组区间[0,3]内，则对其查看的页面不展示优惠券、即不请求券数据
query abUserForCouponAcquire($userId: Int, $couponId: Int,$abKey:String){

    marketing
    @skipBy(expression: "abValue <= 3",dependencySource: "abValue")
    {
        coupon(couponId: $couponId){
            couponId
            couponText
            price
        }
    }

    toolInfo{
        abInfo(userId: $userId,abKey:$abKey) @fetchSource(name: "abValue")
    }
}
```

####  数据补全、动态计算

- 分别查找券信息和列表商品信息；
- 如果商品绑定了券则返回券后价和是否绑定的标识
- 对券数据拼接描述文案；
```graphql
query calculateCouponPrice_Case01 ($couponId: Int, $itemIds: [Int]){

    marketing{
        coupon(couponId: $couponId)
        @fetchSource(name: "itemCouponInfo",sourceConvert: "list2MapWithAssignedValue('bindingItemIds','price')")
        {
            base
            price
            bindingItemIds
            desc: couponText @mapper(expression: "'满' + base + '减' + price")
        }
    }

    commodity{
        itemList(itemIds: $itemIds){
            itemId
            name
            salePrice
            isUsedCoupon: onSale @mapper(dependencySource: "itemCouponInfo",expression: "seq.get(itemCouponInfo,itemId)!=nil")
            # 券后价
            couponPrice: salePrice @mapper(dependencySource: "itemCouponInfo",expression: "salePrice - (seq.get(itemCouponInfo,itemId) == nil? 0:seq.get(itemCouponInfo,itemId)) ")
        }
    }
}
```