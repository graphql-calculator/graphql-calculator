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

##### 继承`calculator.graphql.AsyncDataFetcherInterface`

如果项目中使用了异步化`DataFetcher`，则使其则继承`AsyncDataFetcherInterface`，
并在方法实现中返回异步化`DataFetcher`包装的取数`DataFetcher`和使用的线程池。

##### 通过配置包装schema类、创建`GraphQL`对象

#### 3. 校验执行

对每个使用了计算指令的查询，都必须使用`Validator`进行校验语法是否合法，**建议通过 PreparedDocument 缓存校验结果**。

完整示例如下：
```java
public class Example {

    static class DocumentParseAndValidationCache implements PreparsedDocumentProvider {

        private final Map<String, PreparsedDocumentEntry> cache = new LinkedHashMap<>();

        private final Config wrapperConfig;
        private final GraphQLSchema wrappedSchema;


        public DocumentParseAndValidationCache(Config wrapperConfig, GraphQLSchema wrappedSchema) {
            this.wrapperConfig = wrapperConfig;
            this.wrappedSchema = wrappedSchema;
        }


        @Override
        public PreparsedDocumentEntry getDocument(ExecutionInput executionInput,
                                                  Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
            if (cache.get(executionInput.getQuery()) != null) {
                return cache.get(executionInput.getQuery());
            }

            ParseAndValidateResult validateResult = Validator.validateQuery(
                    executionInput.getQuery(), wrappedSchema, wrapperConfig
            );

            PreparsedDocumentEntry preparsedDocumentEntry;
            if (validateResult.isFailure()) {
                preparsedDocumentEntry = new PreparsedDocumentEntry(validateResult.getErrors());
            } else {
                preparsedDocumentEntry = new PreparsedDocumentEntry(validateResult.getDocument());
            }
            cache.put(executionInput.getQuery(), preparsedDocumentEntry);
            return preparsedDocumentEntry;
        }
    }

    public static void main(String[] args) {

        /**
         * step 1
         *
         * make async dataFetcher implements xxx
         */
        GraphQLSchema schema = SchemaHolder.getSchema();


        /**
         * step 2
         *
         * create Config, and get wrapped schema with the ability of
         * orchestrate and dynamic calculator and control flow, powered by directives,
         * and create GraphQL with wrapped schema and ExecutionEngine.
         *
         * It is recommend to create `PreparsedDocumentProvider` to cache the result of parse and validate.
         */
        Config config = ConfigImpl.newConfig()
                .scriptEvaluator(AviatorScriptEvaluator.getDefaultInstance())
                .build();

        GraphQLSchema wrappedSchema = SchemaWrapper.wrap(config, schema);

        GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema)
                .instrumentation(ExecutionEngine.newInstance(config))
                .preparsedDocumentProvider(new DocumentParseAndValidationCache(config, wrappedSchema))
                .build();

        /**
         * step 3:
         *
         * validate the query: ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema).
         *
         * It is recommend to create `PreparsedDocumentProvider` to cache the result of parse and validate.
         * Reference {@link DocumentParseAndValidationCache}
         */
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

        ExecutionResult result = graphQL.execute(input);
        // consumer result
    }

}
```

# 详情文档

以[测试用例schema](https://github.com/dugenkui03/graphql-java-calculator/blob/refactorForSchedule/src/test/resources/schema.graphql)为例，
对`graphql-java-calculator`的数据编排、控制流、结果处理和计算转换等进行说明。

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

####  数据转换、过滤、补全

- 查找券信息和列表商品信息；
- 使用 @mapper 拼接券的描述文案；
- 如果商品绑定了券则返回券后价和是否绑定的标识
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