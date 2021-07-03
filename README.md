# graphql-java-calculator


`graphql`提供了便捷的数据查询能力，但实际的业务开发还包括数据的编排、过滤、计算转换和控制流等。

基于[指令系统](https://spec.graphql.org/draft/#sec-Language.Directives)，`graphql-java-calculator`为`graphql`查询提供了数据编排、动态计算和控制流的能力。



# 特性

1. 数据编排：将指定类型数据数据作为请求上下文，去请求其他类型数据、补全其他类型字段数据、作为其他类型数据控制流实现的参数数据；
2. 结果处理：包括对结果进行补全、过滤、排序等，其他类型数据也可以作为这些操作的参数数据；
3. 控制流：通过请求参数、其他类型数据判断是否解析当前类型数据；
4. 计算转换：通过表达式、实体数据计算出新的实体数据。


# 快速开始

#### 1. 引入依赖

#### 2. 包装执行引擎

### 1. AsyncDataFetcher 替换

如果项目中使用了`AsyncDataFetcher`，则将其替换为`calculator.graphql.AsyncDataFetcher`，
`calculator.graphql.AsyncDataFetcher`中包含线程池和被包装的`DataFetcher`的`getter`方法，方便`graphql-java-calculator`执行引擎的调度优化，
[`graphql-java`最新版本](https://github.com/graphql-java/graphql-java/pull/2243)release该特性后将进行替换。

### 2. 通过配置创建schema和`GraphQL`对象

```
    private static final GraphQLSchema wrappedSchema = SchemaWrapper.wrap(
            ConfigImpl.newConfig().function(new ListContain()).build(), getCalSchema()
    );

    private static final GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema)
            .instrumentation(newInstance(ConfigImpl.newConfig().build()))
            .build();
```


### 2. 校验请求，建议通过 PreparedDocument 缓存校验结果

### 3. 执行

# 详情文档

以[测试用例schema](https://github.com/dugenkui03/graphql-java-calculator/blob/refactorForSchedule/src/test/resources/schema.graphql)为例，
对`graphql-java-calculator`进行数据编排、控制流、结果处理和计算转换等进行说明。

### 1. 数据编排/参数转换

数据编排的主要形式为请求A类型数据时其输入参数为B类型的结果、或者需要B类型结果对A类型输入参数进行过滤、转换处理。示例如下。

- 变量只有红包id、查询该红包绑定的商品详情，并过滤掉没有在售的商品。
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

- 请求商品信息前，过滤掉没有指定绑定红包id的商品id。
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



### 2. 控制流

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

### 3. 数据转换、过滤、补全

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


TODO 参数过滤、结果过滤




