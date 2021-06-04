
- 执行引擎应该做层封装、屏蔽用户对其他组建的感知，提高其易用性、后续如果要作替换的话降低其成本。
- 更新 README.md.
- 当前最大的问题是预期所有流转的数据都是Map类型的；
- 兼容片段；
- `@require`：即使不是非空字段，在这个查询里边也必须出现，否则所在实体为空；
- 是否是用到了某个函数、其参数是什么——校验；

TODO：参数转换指令的参数来源应该包括 source、这也复合大部分场景，必须支持。

- todo：
- 环校验：是否都在一个list里边 / 是否有相同的list父节点；也不能依赖父亲节点；TODO：多看几个sql
        // todo @node节点的完成不依赖使用到node的节点，所以只有上述两种情况：父亲节点和同一个顶层任务节点下的节点、包括顶层任务节点本身。

#  如果 field_2 执行失败了，则field会永远阻塞下去
#  所以最好的方式是也分析出 field_2，从顶层任务节点开始分析结束情况，但是在之上的任务节点失败则中断等待。
query{
     field_1 @link(node:"arg"")
     field_2{
            listField{
                # 依赖的任务节点[listField, listField.innerField]
                innerField @node(name:"arg")
            }
     }

}


# 注意

- 注意没有指定线程池的地方大概率为调度线程；

- 功能：https://spectrum.chat/graphql-java?tab=posts；

- 如果依赖的节点任务异常，则获取的数据为null——一个节点有问题不应该影响另一个节点。





