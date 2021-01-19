# graphql-java-calculate

基于`graphql`指令机制，为graphql-java提供了丰富的动态计算和依赖调度能力。


# 特性介绍

1. 丰富的动态计算能力：通过注解在元素上的指令，对元素数据进行转换、过滤、排序、跨类型计算等；
2. 实现了数据之间的依赖关联：使用@node注解的元素，可以作为获取其他类型元素的参数；
3. 工具：生成查询对应的java代码；数据血缘。




# 使用举例
### 引入依赖
```
<dependency>
  <groupId>org.graphql-java-calculator</groupId>
  <artifactId>graphql-java-calculator</artifactId>
  <version>0.1-snapshot</version>
</dependency>
```

### 示例代码

```
      // step_1：创建配置类
        ConfigImpl scheduleConfig = ConfigImpl.newConfig()
                // 是否需要支持依赖调度
                .isScheduleEnable(true)
                 添加查询计算支持的函数
                .functionList(functions)
                // 指定计算引擎实例
                .evaluatorInstance(instance)
            .build();


        // step_2：使用Wrapper对业务schema进行包装；
        GraphQLSchema wrappedSchema = Wrapper.wrap(scheduleConfig, getCalSchema());

        // step_3：将 CalculateInstrumentation 和 ScheduleInstrument 作为ChainedInstrumentation元素创建实体，
        //         如果不需要支持依赖调度，则可省去ScheduleInstrument。
        ChainedInstrumentation chainedInstrumentation = new ChainedInstrumentation(
                Arrays.asList(CalculateInstrumentation.getCalInstance(), ScheduleInstrument.getScheduleInstrument())
        );

        // step_4：使用wrappedSchema和chainedInstrumentation创建GraphQL，运行跨类型调度的且带有计算的查询
        GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema)
                .instrumentation(chainedInstrumentation).build();
        String query = "query(...){ ... }";

        System.out.println(query);
        ExecutionInput input = ExecutionInput.newExecutionInput(query)
                .variables(Collections.singletonMap("userId", 1))
                .build();
        ExecutionResult result = graphQL.execute(input);
```

### 示例查询

```
// 当userId小于0的时候跳过对用户信息的查询
query($userId:Int) { 
    userInfo(id: $userId) @skipBy(exp:"id < 0"){ 
        id        
        name   
    }
}

// 将价格字段转换为文案字段
query($itemId:Int) {
    item(id: $itemId){
        id
        name
        priceText: name @map(mapper:"'售价'+str(salePrice/100)+'元'")
    }
}

// 过滤掉满额大于2元的优惠券
query {
    couponList(ids:[1,2,3,4]) @filter(predicate:"limitation>200"){
        id
        price
        limitation 
    }  
}

// 获取用户的年龄、姓名还有喜欢的商品详情，
// @link(node:"itemIds", argument:"ids") 表示将名称为'itemIds'的node值，替换参数'ids'，解析itemList。
query($userId:Int){
    userInfo(id:$userId){
        age
        name
        preferredItemIdList @node(name:"itemIds")
    }

    itemList(ids:1) @link(node:"itemIds", argument:"ids"){
        name
        salePrice
    }
}
```



