package graphql.normalized

import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.MergedField
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import spock.lang.Specification

import static graphql.language.AstPrinter.printAst
import static graphql.parser.Parser.parseValue
import static graphql.schema.FieldCoordinates.coordinates

class ExecutableNormalizedOperationFactoryTest extends Specification {


    def "test"() {
        String schema = """
type Query{ 
    animal: Animal
}
interface Animal {
    name: String
    friends: [Friend]
}

union Pet = Dog | Cat

type Friend {
    name: String
    isBirdOwner: Boolean
    isCatOwner: Boolean
    pets: [Pet] 
}

type Bird implements Animal {
   name: String 
   friends: [Friend]
}

type Cat implements Animal{
   name: String 
   friends: [Friend]
   breed: String 
}

type Dog implements Animal{
   name: String 
   breed: String
   friends: [Friend]
}
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            animal{
                name
                otherName: name
                ... on Animal {
                    name
                }
               ... on Cat {
                    name
                    friends {
                        ... on Friend {
                            isCatOwner
                            pets {
                               ... on Dog {
                                name
                               } 
                            }
                        }
                   } 
               }
               ... on Bird {
                    friends {
                        isBirdOwner
                    }
                    friends {
                        name
                        pets {
                           ... on Cat {
                            breed
                           } 
                        }
                    }
               }
               ... on Dog {
                  name   
               }
        }}
        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.animal',
                        '[Bird, Cat, Dog].name',
                        'otherName: [Bird, Cat, Dog].name',
                        '[Cat, Bird].friends',
                        'Friend.isCatOwner',
                        'Friend.pets',
                        'Dog.name',
                        'Cat.breed',
                        'Friend.isBirdOwner',
                        'Friend.name']

    }

    def "test2"() {
        String schema = """
        type Query{ 
            a: A
        }
        interface A {
           b: B  
        }
        type A1 implements A {
           b: B 
        }
        type A2 implements A{
            b: B
        }
        interface B {
            leaf: String
        }
        type B1 implements B {
            leaf: String
        } 
        type B2 implements B {
            leaf: String
        } 
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            a {
            ... on A {
                myAlias: b { leaf }
            }
                ... on A1 {
                   b { 
                     ... on B1 {
                        leaf
                        }
                     ... on B2 {
                        leaf
                        }
                   }
                }
                ... on A1 {
                   b { 
                     ... on B1 {
                        leaf
                        }
                   }
                }
                ... on A2 {
                    b {
                       ... on B2 {
                            leaf
                       } 
                    }
                }
            }
        }
        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.a',
                        'myAlias: [A1, A2].b',
                        '[B1, B2].leaf',
                        '[A1, A2].b',
                        '[B1, B2].leaf']
    }

    def "test3"() {
        String schema = """
        type Query{ 
            a: [A]
            object: Object
        }
        type Object {
            someValue: String
        }
        interface A {
           b: B  
        }
        type A1 implements A {
           b: B 
        }
        type A2 implements A{
            b: B
        }
        interface B {
            leaf: String
        }
        type B1 implements B {
            leaf: String
        } 
        type B2 implements B {
            leaf: String
        } 
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
          object{someValue}
          a {
            ... on A1 {
              b {
                ... on B {
                  leaf
                }
                ... on B1 {
                  leaf
                }
                ... on B2 {
                  ... on B {
                    leaf
                  }
                  leaf
                  leaf
                  ... on B2 {
                    leaf
                  }
                }
              }
            }
          }
        }
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.object',
                        'Object.someValue',
                        'Query.a',
                        'A1.b',
                        '[B1, B2].leaf']

    }

    def "test impossible type condition"() {

        String schema = """
        type Query{ 
            pets: [Pet]
        }
        interface Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        type Dog implements Pet{
            name: String
        }
        union CatOrDog = Cat | Dog
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                ... on Dog {
                    ... on CatOrDog {
                    ... on Cat{
                            name
                            }
                    }
                }
            }
        }
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets']

    }

    def "query with unions and __typename"() {

        String schema = """
        type Query{ 
            pets: [CatOrDog]
        }
        type Cat {
            catName: String
        }
        type Dog {
            dogName: String
        }
        union CatOrDog = Cat | Dog
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                __typename
                ... on Cat {
                    catName 
                }  
                ... on Dog {
                    dogName
                }
            }
        }
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets',
                        '[Cat, Dog].__typename',
                        'Cat.catName',
                        'Dog.dogName']

    }

    def "query with interface"() {

        String schema = """
        type Query{ 
            pets: [Pet]
        }
        interface Pet {
            id: ID
        }
        type Cat implements Pet{
            id: ID
            catName: String
        }
        type Dog implements Pet{
            id: ID
            dogName: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                id
                ... on Cat {
                    catName 
                }  
                ... on Dog {
                    dogName
                }
            }
        }
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets',
                        '[Cat, Dog].id',
                        'Cat.catName',
                        'Dog.dogName']

    }

    def "test5"() {
        String schema = """
        type Query{ 
            a: [A]
        }
        interface A {
           b: String
        }
        type A1 implements A {
           b: String 
        }
        type A2 implements A{
            b: String
            otherField: A
        }
        type A3  implements A {
            b: String
        }
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)


        def query = """
        {
            a {
                b
                ... on A1 {
                   b 
                }
                ... on A2 {
                    b 
                    otherField {
                    ... on A2 {
                            b
                        }
                        ... on A3 {
                            b
                        }
                    }
                    
                }
            }
        }
        
        """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.a',
                        '[A1, A2, A3].b',
                        'A2.otherField',
                        '[A2, A3].b']

    }

    def "test6"() {
        String schema = """
        type Query {
            issues: [Issue]
        }

        type Issue {
            id: ID
            author: User
        }
        type User {
            name: String
            createdIssues: [Issue] 
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        def query = """{ issues {
                    author {
                        name
                        ... on User {
                            createdIssues {
                                id
                            }
                        }
                    }
                }}
                """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.issues',
                        'Issue.author',
                        'User.name',
                        'User.createdIssues',
                        'Issue.id']

    }

    def "test7"() {
        String schema = """
        type Query {
            issues: [Issue]
        }

        type Issue {
            authors: [User]
        }
        type User {
            name: String
            friends: [User]
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        def query = """{ issues {
                    authors {
                       friends {
                            friends {
                                name
                            }
                       } 
                   }
                }}
                """

        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.issues',
                        'Issue.authors',
                        'User.friends',
                        'User.friends',
                        'User.name']

    }

    def "query with fragment definition"() {
        def graphQLSchema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
                moreFoos: Foo
            }
        """)
        def query = """
            {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.foo',
                        'Foo.subFoo',
                        'Foo.moreFoos',
                        'Foo.subFoo']
    }

    def "query with interface in between"() {
        def graphQLSchema = TestUtil.schema("""
        type Query {
            pets: [Pet]
        }
        interface Pet {
            name: String
            friends: [Human]
        }
        type Human {
            name: String
        }
        type Cat implements Pet {
            name: String
            friends: [Human]
        }
        type Dog implements Pet {
            name: String
            friends: [Human]
        }
        """)
        def query = """
            { pets { friends {name} } }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pets',
                        '[Cat, Dog].friends',
                        'Human.name']
    }


    List<String> printTree(ExecutableNormalizedOperation queryExecutionTree) {
        def result = []
        Traverser<ExecutableNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() });
        traverser.traverse(queryExecutionTree.getTopLevelFields(), new TraverserVisitorStub<ExecutableNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<ExecutableNormalizedField> context) {
                ExecutableNormalizedField queryExecutionField = context.thisNode();
                result << queryExecutionField.printDetails()
                return TraversalControl.CONTINUE;
            }
        });
        result
    }

    def "field to normalized field is build"() {
        def graphQLSchema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
                moreFoos: Foo
            }
        """)
        def query = """
            {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)
        def subFooField = (document.getDefinitions()[1] as FragmentDefinition).getSelectionSet().getSelections()[0] as Field

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def fieldToNormalizedField = tree.getFieldToNormalizedField()

        expect:
        fieldToNormalizedField.keys().size() == 4
        fieldToNormalizedField.get(subFooField).size() == 2
        fieldToNormalizedField.get(subFooField)[0].level == 2
        fieldToNormalizedField.get(subFooField)[1].level == 3
    }

    def "normalized fields map with interfaces "() {

        String schema = """
        type Query{ 
            pets: [Pet]
        }
        interface Pet {
            id: ID
        }
        type Cat implements Pet{
            id: ID
        }
        type Dog implements Pet{
            id: ID
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                id
            }
        }
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)
        def petsField = (document.getDefinitions()[0] as OperationDefinition).getSelectionSet().getSelections()[0] as Field
        def idField = petsField.getSelectionSet().getSelections()[0] as Field

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def fieldToNormalizedField = tree.getFieldToNormalizedField()


        expect:
        fieldToNormalizedField.size() == 2
        fieldToNormalizedField.get(petsField).size() == 1
        fieldToNormalizedField.get(petsField)[0].printDetails() == "Query.pets"
        fieldToNormalizedField.get(idField).size() == 1
        fieldToNormalizedField.get(idField)[0].printDetails() == "[Cat, Dog].id"


    }

    def "query with introspection fields"() {
        String schema = """
        type Query{ 
            foo: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            __typename
            alias: __typename
            __schema {  queryType { name } }
            __type(name: "Query") {name}
            ...F
        }
        fragment F on Query {
            __typename
            alias: __typename
            __schema {  queryType { name } }
            __type(name: "Query") {name}
        }
        
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)
        def selections = (document.getDefinitions()[0] as OperationDefinition).getSelectionSet().getSelections()
        def typeNameField = selections[0] as Field
        def aliasedTypeName = selections[1] as Field
        def schemaField = selections[2] as Field
        def typeField = selections[3] as Field

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def fieldToNormalizedField = tree.getFieldToNormalizedField()

        expect:
        fieldToNormalizedField.size() == 14
        fieldToNormalizedField.get(typeNameField)[0].objectTypeNamesToString() == "Query"
        fieldToNormalizedField.get(typeNameField)[0].getFieldDefinitions(graphQLSchema) == [graphQLSchema.getIntrospectionTypenameFieldDefinition()]
        fieldToNormalizedField.get(aliasedTypeName)[0].alias == "alias"
        fieldToNormalizedField.get(aliasedTypeName)[0].getFieldDefinitions(graphQLSchema) == [graphQLSchema.getIntrospectionTypenameFieldDefinition()]

        fieldToNormalizedField.get(schemaField)[0].objectTypeNamesToString() == "Query"
        fieldToNormalizedField.get(schemaField)[0].getFieldDefinitions(graphQLSchema) == [graphQLSchema.getIntrospectionSchemaFieldDefinition()]

        fieldToNormalizedField.get(typeField)[0].objectTypeNamesToString() == "Query"
        fieldToNormalizedField.get(typeField)[0].getFieldDefinitions(graphQLSchema) == [graphQLSchema.getIntrospectionTypeFieldDefinition()]

    }

    def "fragment is used multiple times with different parents"() {
        String schema = """
        type Query{ 
            pet: Pet
        }
        interface Pet {
            name: String
        }
        type Dog implements Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pet {
                ... on Dog {
                    ...F
                }
                ... on Cat {
                    ...F
                }
            }
        }
        fragment F on Pet {
            name
        }
        
        
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pet',
                        '[Dog, Cat].name'];
    }

    def "same result key but different field"() {
        String schema = """
        type Query{ 
            pet: Pet
        }
        interface Pet {
            name: String
        }
        type Dog implements Pet {
            name: String
            otherField: String
        }
        type Cat implements Pet {
            name: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pet {
                ... on Dog {
                    name: otherField
                }
                ... on Cat {
                    name
                }
            }
        }
        """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Query.pet',
                        'name: Dog.otherField',
                        'Cat.name'];
    }

    def "normalized field to MergedField is build"() {
        given:
        def graphQLSchema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
                moreFoos: Foo
            }
        """)
        def query = """
            {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def normalizedFieldToMergedField = tree.getNormalizedFieldToMergedField();
        Traverser<ExecutableNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() });
        List<MergedField> result = new ArrayList<>()
        when:
        traverser.traverse(tree.getTopLevelFields(), new TraverserVisitorStub<ExecutableNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<ExecutableNormalizedField> context) {
                ExecutableNormalizedField normalizedField = context.thisNode();
                result.add(normalizedFieldToMergedField[normalizedField])
                return TraversalControl.CONTINUE;
            }
        });

        then:
        result.size() == 4
        result.collect { it.getResultKey() } == ['foo', 'subFoo', 'moreFoos', 'subFoo']
    }

    def "coordinates to NormalizedField is build"() {
        given:
        def graphQLSchema = TestUtil.schema("""
            type Query{
                foo: Foo
            }
            type Foo {
                subFoo: String  
                moreFoos: Foo
            }
        """)
        def query = """
            {foo { ...fooData moreFoos { ...fooData }}} fragment fooData on Foo { subFoo }
            """
        assertValidQuery(graphQLSchema, query)

        Document document = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();

        when:
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def coordinatesToNormalizedFields = tree.coordinatesToNormalizedFields

        then:
        coordinatesToNormalizedFields.size() == 4
        coordinatesToNormalizedFields.get(coordinates("Query", "foo")).size() == 1
        coordinatesToNormalizedFields.get(coordinates("Foo", "moreFoos")).size() == 1
        coordinatesToNormalizedFields.get(coordinates("Foo", "subFoo")).size() == 2
    }

    def "handles mutations"() {
        String schema = """
type Query{ 
    animal: Animal
}

type Mutation {
    createAnimal: Query
}

type Subscription {
    subscribeToAnimal: Query
}

interface Animal {
    name: String
    friends: [Friend]
}

union Pet = Dog | Cat

type Friend {
    name: String
    isBirdOwner: Boolean
    isCatOwner: Boolean
    pets: [Pet] 
}

type Bird implements Animal {
   name: String 
   friends: [Friend]
}

type Cat implements Animal{
   name: String 
   friends: [Friend]
   breed: String 
}

type Dog implements Animal{
   name: String 
   breed: String
   friends: [Friend]
}

schema {
  query: Query
  mutation: Mutation
  subscription: Subscription
}
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String mutation = """
        mutation TestMutation{
            createAnimal {
                animal {
                   name
                   otherName: name
                   ... on Cat {
                        name
                        friends {
                            ... on Friend {
                                isCatOwner
                            }
                       } 
                   }
                   ... on Bird {
                        friends {
                            isBirdOwner
                        }
                        friends {
                            name
                        }
                   }
                   ... on Dog {
                      name   
                   }
                }
            }
        }
        """

        assertValidQuery(graphQLSchema, mutation)

        Document document = TestUtil.parseQuery(mutation)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def tree = dependencyGraph.createExecutableNormalizedOperation(graphQLSchema, document, null, [:])
        def printedTree = printTree(tree)

        expect:
        printedTree == ['Mutation.createAnimal',
                        'Query.animal',
                        '[Bird, Cat, Dog].name',
                        'otherName: [Bird, Cat, Dog].name',
                        '[Cat, Bird].friends',
                        'Friend.isCatOwner',
                        'Friend.isBirdOwner',
                        'Friend.name']
    }

    private void assertValidQuery(GraphQLSchema graphQLSchema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build()
        assert graphQL.execute(query, null, variables).errors.size() == 0
    }

    def "normalized arguments"() {
        given:
        String schema = """
        type Query{ 
            dog(id:ID): Dog 
        }
        type Dog {
            name: String
            search(arg1: Input1, arg2: Input1, arg3: Input1): Boolean
        }
        input Input1 {
            foo: String
            input2: Input2
        }
        input Input2 {
            bar: Int
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
            query(\$var1: Input2, \$var2: Input1){dog(id: "123"){
                search(arg1: {foo: "foo", input2: {bar: 123}}, arg2: {foo: "foo", input2: \$var1}, arg3: \$var2) 
            }}
        """

        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        def variables = [
                var1: [bar: 123],
                var2: [foo: "foo", input2: [bar: 123]]
        ]
        // the normalized arg value should be the same regardless of how the value was provided
        def expectedNormalizedArgValue = [foo: new NormalizedInputValue("String", parseValue('"foo"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("123"))])]
        when:
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, variables)
        def topLevelField = tree.getTopLevelFields().get(0)
        def secondField = topLevelField.getChildren().get(0)
        def arg1 = secondField.getNormalizedArgument("arg1")
        def arg2 = secondField.getNormalizedArgument("arg2")
        def arg3 = secondField.getNormalizedArgument("arg3")

        then:
        topLevelField.getNormalizedArgument("id").getTypeName() == "ID"
        printAst(topLevelField.getNormalizedArgument("id").getValue()) == '"123"'

        arg1.getTypeName() == "Input1"
        arg1.getValue() == expectedNormalizedArgValue
        arg2.getTypeName() == "Input1"
        arg2.value == expectedNormalizedArgValue
        arg3.getTypeName() == "Input1"
        arg3.value == expectedNormalizedArgValue
    }

    def "arguments with absent variable values inside input objects"() {
        given:
        def schema = """
        type Query {
            hello(arg: Arg, otherArg: String = "otherValue"): String
        }
        input Arg {
            ids: [ID] = ["defaultId"]
        }
        """
        def graphQLSchema = TestUtil.schema(schema)

        def query = """
        query myQuery(\$varIds: [ID], \$otherVar: String) {
            hello(arg: {ids: \$varIds}, otherArg: \$otherVar)
        }
        """

        assertValidQuery(graphQLSchema, query)
        def document = TestUtil.parseQuery(query)
        def dependencyGraph = new ExecutableNormalizedOperationFactory()
        def variables = [:]
        when:
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, variables)

        then:
        def topLevelField = tree.getTopLevelFields().get(0)

        def arg = topLevelField.getNormalizedArgument("arg")
        arg == new NormalizedInputValue("Arg", [:])
        !topLevelField.normalizedArguments.containsKey("otherArg")

        topLevelField.resolvedArguments.get("arg") == [ids: ["defaultId"]]
        topLevelField.resolvedArguments.get("otherArg") == "otherValue"
    }

    def "arguments with null variable values"() {
        given:
        def schema = """
        type Query {
            hello(arg: Arg, otherArg: String = "otherValue"): String
        }
        input Arg {
            ids: [ID] = ["defaultId"]
        }
        """
        def graphQLSchema = TestUtil.schema(schema)

        def query = """
            query nadel_2_MyService_myQuery(\$varIds: [ID], \$otherVar: String) {
               hello(arg: {ids: \$varIds}, otherArg: \$otherVar)
            }
        """

        assertValidQuery(graphQLSchema, query)
        def document = TestUtil.parseQuery(query)
        def dependencyGraph = new ExecutableNormalizedOperationFactory()
        def variables = [
                varIds  : null,
                otherVar: null,
        ]
        when:
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, variables)

        then:
        def topLevelField = tree.getTopLevelFields().get(0)
        def arg = topLevelField.getNormalizedArgument("arg")
        def otherArg = topLevelField.getNormalizedArgument("otherArg")

        arg == new NormalizedInputValue(
                "Arg",
                [
                        ids: new NormalizedInputValue(
                                "[ID]",
                                null,
                        ),
                ]
        )
        otherArg == new NormalizedInputValue("String", null)

        topLevelField.resolvedArguments.get("arg") == [ids: null]
        topLevelField.resolvedArguments.get("otherArg") == null
    }

    def "normalized arguments with lists"() {
        given:
        String schema = """
        type Query{ 
            search(arg1:[ID!], arg2:[[Input1]], arg3: [Input1]): Boolean
        }
        input Input1 {
            foo: String
            input2: Input2
        }
        input Input2 {
            bar: Int
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
            query($var1: [Input1], $var2: ID!){
                search(arg1:["1",$var2], arg2: [[{foo: "foo1", input2: {bar: 123}},{foo: "foo2", input2: {bar: 456}}]], arg3: $var1) 
            }
        '''

        def variables = [
                var1: [[foo: "foo3", input2: [bar: 789]]],
                var2: "2",
        ]
        assertValidQuery(graphQLSchema, query, variables)
        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        when:
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, variables)
        def topLevelField = tree.getTopLevelFields().get(0)
        def arg1 = topLevelField.getNormalizedArgument("arg1")
        def arg2 = topLevelField.getNormalizedArgument("arg2")
        def arg3 = topLevelField.getNormalizedArgument("arg3")

        then:
        arg1.typeName == "[ID!]"
        arg1.value.collect { printAst(it) } == ['"1"', '"2"']
        arg2.typeName == "[[Input1]]"
        arg2.value == [[
                               [foo: new NormalizedInputValue("String", parseValue('"foo1"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("123"))])],
                               [foo: new NormalizedInputValue("String", parseValue('"foo2"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("456"))])]
                       ]]

        arg3.getTypeName() == "[Input1]"
        arg3.value == [
                [foo: new NormalizedInputValue("String", parseValue('"foo3"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("789"))])],
        ]


    }

    def "normalized arguments with lists 2"() {
        given:
        String schema = """
        type Query{ 
            search(arg1:[[Input1]] ,arg2:[[ID!]!]): Boolean
        }
        input Input1 {
            foo: String
            input2: Input2
        }
        input Input2 {
            bar: Int
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
            query($var1: [Input1], $var2: [ID!]!){
                search(arg1: [$var1],arg2:[["1"],$var2] ) 
            }
        '''

        def variables = [
                var1: [[foo: "foo1", input2: [bar: 123]]],
                var2: "2"
        ]
        assertValidQuery(graphQLSchema, query, variables)
        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        when:
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, variables)
        def topLevelField = tree.getTopLevelFields().get(0)
        def arg1 = topLevelField.getNormalizedArgument("arg1")
        def arg2 = topLevelField.getNormalizedArgument("arg2")

        then:
        arg1.typeName == "[[Input1]]"
        arg1.value == [[
                               [foo: new NormalizedInputValue("String", parseValue('"foo1"')), input2: new NormalizedInputValue("Input2", [bar: new NormalizedInputValue("Int", parseValue("123"))])],
                       ]]
        arg2.typeName == "[[ID!]!]"
        arg2.value.collect { outer -> outer.collect { printAst(it) } } == [['"1"'], ['"2"']]
    }


    def "recursive schema with a lot of objects"() {
        given:
        String schema = """
        type Query{ 
            foo: Foo 
        }
        interface Foo {
            field: Foo
            id: ID
        }
        type O1 implements Foo {
            field: Foo
            id: ID
        }
        type O2 implements Foo {
            field: Foo
            id: ID
        }
        type O3 implements Foo {
            field: Foo
            id: ID
        }
        type O4 implements Foo {
            field: Foo
            id: ID
        }
        type O5 implements Foo {
            field: Foo
            id: ID
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
            {foo{field{id}}foo{field{id}}}
        '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        when:
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, [:])

        then:
        tree.normalizedFieldToMergedField.size() == 3
        tree.fieldToNormalizedField.size() == 6
        println String.join("\n", printTree(tree))
        /**
         * NF{Query.foo} -> NF{"O1...O5".field,} -> NF{O1...O5.id}*/
    }

    def "diverged fields"() {
        given:
        String schema = """
        type Query {
          pets: Pet
        }
        interface Pet {
          name: String
        }
        type Cat implements Pet {
            name: String
            catValue: Int
            catFriend(arg: String): CatFriend
        }
        type CatFriend {
          catFriendName: String
        }
        type Dog implements Pet {
             name: String
             dogValue: Float
             dogFriend: DogFriend
        }
        type DogFriend {
           dogFriendName: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
          {pets {
                ... on Cat {
                  friend: catFriend(arg: "hello") {
                    catFriendName
              }}
                ... on Cat {
                  friend: catFriend(arg: "hello") {
                    catFriendName
              }}
                ... on Dog {
                  friend: dogFriend {
                    dogFriendName
              }}
          }}
        '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        when:
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, [:])
        println String.join("\n", printTree(tree))

        then:
        tree.normalizedFieldToMergedField.size() == 5
        tree.fieldToNormalizedField.size() == 7
    }

    def "diverged fields 2"() {
        given:
        String schema = """
        type Query {
          pets: Pet
        }
        interface Pet {
          name(arg:String): String
        }
        type Cat implements Pet {
            name(arg: String): String
        }
        
        type Dog implements Pet {
             name(arg: String): String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
          {pets {
                ... on Cat {
                    name(arg: "foo")
              }
                ... on Dog {
                    name(arg: "foo")
              }
          }}
        '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        when:
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, [:])
        println String.join("\n", printTree(tree))

        then:
        tree.normalizedFieldToMergedField.size() == 2
        tree.fieldToNormalizedField.size() == 3
    }

    def "skip/include is respected"() {
        given:
        String schema = """
        type Query {
          pets: Pet
        }
        interface Pet {
          name: String
        }
        type Cat implements Pet {
          name: String
        }
        type Dog implements Pet {
            name: String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''
          query($true: Boolean!,$false: Boolean!){pets {
                ... on Cat {
                    cat_not: name @skip(if:true)
                    cat_not: name @skip(if:$true)
                    cat_yes_1: name @include(if:true)
                    cat_yes_2: name @skip(if:$false)
              }
                ... on Dog @include(if:$true) {
                    dog_no: name @include(if:false)
                    dog_no: name @include(if:$false)
                    dog_yes_1: name @include(if:$true)
                    dog_yes_2: name @skip(if:$false)
              }
              ... on Pet @skip(if:$true) {
                    not: name
              }
              ... on Pet @skip(if:$false) {
                    pet_name: name
              }
          }}
        '''
        def variables = ["true": Boolean.TRUE, "false": Boolean.FALSE]
        assertValidQuery(graphQLSchema, query, variables)
        Document document = TestUtil.parseQuery(query)
        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory();
        when:
        def tree = dependencyGraph.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, variables)
        println String.join("\n", printTree(tree))
        def printedTree = printTree(tree)


        then:
        printedTree == ['Query.pets',
                        'cat_yes_1: Cat.name',
                        'cat_yes_2: Cat.name',
                        'dog_yes_1: Dog.name',
                        'dog_yes_2: Dog.name',
                        'pet_name: [Cat, Dog].name',
        ]
    }

    def "missing argument"() {
        given:
        String schema = """
        type Query {
            hello(arg: String): String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = '''{hello} '''
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def tree = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(graphQLSchema, document, null, [:])
        println String.join("\n", printTree(tree))
        def printedTree = printTree(tree)


        then:
        printedTree == ['Query.hello']
        tree.getTopLevelFields().get(0).getNormalizedArguments().isEmpty()
    }
}
