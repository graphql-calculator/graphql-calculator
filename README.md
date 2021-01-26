# graphql-java-calculator

![Build and Publish](https://github.com/dugenkui03/graphql-java-calculator/workflows/Build%20and%20Publish/badge.svg)

基于[指令系统](https://spec.graphql.org/draft/#sec-Language.Directives)，`graphql-java-calculator`为`graphql`查询提供了动态计算和依赖编排的能力。


`graphql-java-calculator`基于[`graphql-java`](https://github.com/graphql-java/graphql-java)和[`aviatorscript`](https://github.com/killme2008/aviatorscript)开发，需要`java1.8`或更高版本。


# 特性介绍

1. `@map(mapper:String)` 可将同源数据作为参数、计算返回结果；
2. `@skipBy(if:String)`拓展了[`@skip`](https://spec.graphql.org/draft/#sec--skip)指令的能力，使用`aviator`表达式判断是否跳过注解元素的解析，可用来实现灰度、ab等逻辑；
3. `@sortBy(key:String)` 和 `@skipBy(exp)` 用于对集合进行排序和过滤；
4. 支持全局范围的依赖编排，使用`@node(name:String)`将指定元素注册为全局可获取的数据，可作为其他字段查询、计算的参数；
5. 轻量级，使用简单，基于[`graphql-java`](https://github.com/graphql-java/graphql-java)的经验即可轻松上手




# 快速开始
#### 1、引入依赖
```
<dependency>
  <groupId>com.graphql-java-calculator</groupId>
  <artifactId>graphql-java-calculator</artifactId>
  <version>1.0-beta-1</version>
</dependency>
```

#### 2、包装执行引擎

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

#### 3、示例查询

以下查询基于[电子商务schema](https://github.com/dugenkui03/graphql-java-calculator/blob/main/src/test/resources/eCommerce.graphqls)。

 **`@skipBy(exp:String)`**
使用表达式判断是否跳过对注解信息的查询。

- 过滤非法参数。
```
# 当userId小于0的时候跳过对用户信息的查询
query($userId: Int) { 
    userInfo(id: $userId) @skipBy(exp:"id < 0"){ 
        age
        name
    }
}
```

- AB实验：假设AB实验下有三个分组、唯一标识分别为1、2、3，对应三个数据源。
```
query($itemId: Int, $couponId: Int) { 
    itemInfo: itemInfo_X(id: $itemId) @skipBy(exp:"abMethod(itemId)!=1"){ 
        size
        color 
    }
    
    itemInfo: itemInfo_Y(id: $itemId) @skipBy(exp:"abMethod(itemId)!=2"){ 
        size
        color 
    }
    
    itemInfo: itemInfo_Z(id: $itemId) @skipBy(exp:"abMethod(itemId)!=3"){ 
        size
        color 
    }
}
```


**`@map(exp:String)`**

使用同源数据作为参数，计算所注解元素的值。

- 所谓同源参数是指 `id`、`name`、和`salePrice`均为item对应的`数据解析器(DataFetcher)`获取；
- 参数书写方式为绝对路径。

```

query($itemId:Int) {
    item(id: $itemId){
        id
        name
        salePrice
        # 结果：
        priceText: name @map(mapper:"name+'售价'+str(salePrice/100)+'元'")
    }
}
```

**`@skipBy(predicate:String)`**

只能用在列表上。
```
# 过滤掉满额大于2元的优惠券
query {
    couponList(ids:[1,2,3,4]) @filter(predicate:"limitation>200"){
        id
        price
        limitation 
    }  
}
```

**`@node(name:String)`和`@link(node:String,argument:String)`**

- 使用`@node`可将指定节点注册为全局可获取的数据，**依赖该节点的操作会阻塞指导该节点解析完成**；
- `@link`可将指定`@node`链接到请求参数上；
- 此外，还可以通过`node(nodeName: String)` 获取指定名称的节点数据；
- ⚠️：连接后的图仍然必须是`DAG`。


获取指定用户的个人信息和收藏的商品详情：
```
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

# 其他信息

- `graphql-java`社区论坛：https://spectrum.chat/graphql-java
- `graphql`规范：https://spec.graphql.org/draft/
- `aviator`语法：https://www.yuque.com/boyan-avfmj/aviatorscript/cpow90
- 作者邮箱：dugk@foxmail.com