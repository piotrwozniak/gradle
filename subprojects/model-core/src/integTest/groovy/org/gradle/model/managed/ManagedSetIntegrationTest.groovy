/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl

class ManagedSetIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    def "rule can create a managed collection of interface backed managed model elements"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            @RuleSource
            class Rules {
              @Model
              void people(ManagedSet<Person> people) {}

              @Mutate void addPeople(ManagedSet<Person> people) {
                people.create { it.name = "p1" }
                people.create { it.name = "p2" }
              }
            }

            apply type: Rules

            model {
              people {
                create { it.name = "p3" }
              }

              tasks {
                create("printPeople") {
                  it.doLast {
                    def names = $("people")*.name.sort().join(", ")
                    println "people: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains 'people: p1, p2, p3'
    }

    def "rule can create a managed collection of abstract class backed managed model elements"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            abstract class Person {
              abstract String getName()
              abstract void setName(String string)
            }

            @RuleSource
            class Rules {
              @Model
              void people(ManagedSet<Person> people) {
                people.create { it.name = "p1" }
                people.create { it.name = "p2" }
              }
            }

            apply type: Rules

            model {
              tasks {
                create("printPeople") {
                  it.doLast {
                    def names = $("people")*.name.sort().join(", ")
                    println "people: $names"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printPeople"

        and:
        output.contains 'people: p1, p2'
    }

    def "managed model type has property of collection of managed types"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            @Managed
            interface Group {
              String getName()
              void setName(String string)
              ManagedSet<Person> getMembers()
            }

            @RuleSource
            class Rules {
              @Model
              void group(Group group) {
                group.name = "Women in computing"
                group.members.create { name = "Ada Lovelace" }
                group.members.create { name = "Grace Hooper" }
              }
            }

            apply type: Rules

            model {
              tasks {
                create("printGroup") {
                  it.doLast {
                    def members = $("group").members*.name.sort().join(", ")
                    def name = $("group").name
                    println "$name: $members"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printGroup"

        and:
        output.contains 'Women in computing: Ada Lovelace, Grace Hooper'
    }

    def "managed model type can reference a collection of managed types"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
              String getName()
              void setName(String string)
            }

            @Managed
            interface Group {
              String getName()
              void setName(String string)
              ManagedSet<Person> getMembers()
              void setMembers(ManagedSet<Person> members)
            }

            @RuleSource
            class Rules {
              @Model
              void people(ManagedSet<Person> people) {
                people.create { it.name = "Ada Lovelace" }
                people.create { it.name = "Grace Hooper" }
              }

              @Model
              void group(Group group, @Path("people") ManagedSet<Person> people) {
                group.name = "Women in computing"
                group.members = people
              }
            }

            apply type: Rules

            model {
              tasks {
                create("printGroup") {
                  it.doLast {
                    def members = $("group").members*.name.sort().join(", ")
                    def name = $("group").name
                    println "$name: $members"
                  }
                }
              }
            }
        '''

        then:
        succeeds "printGroup"

        and:
        output.contains 'Women in computing: Ada Lovelace, Grace Hooper'
    }

    def "read methods of ManagedSet throw exceptions when used in a creation rule"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
            }

            @RuleSource
            class RulePlugin {
                @Model
                void people(ManagedSet<Person> people) {
                    people.size()
                }

                @Mutate
                void addDependencyOnPeople(CollectionBuilder<Task> tasks, ManagedSet<Person> people) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#people")
        failure.assertHasCause("Attempt to read a write only view of model of type 'org.gradle.model.collection.ManagedSet<Person>' given to rule 'RulePlugin#people(org.gradle.model.collection.ManagedSet<Person>)'")
    }

    def "read methods of ManagedSet throw exceptions when used in a mutation rule"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
            }

            @RuleSource
            class RulePlugin {
                @Model
                void people(ManagedSet<Person> people) {
                }

                @Mutate
                void readPeople(ManagedSet<Person> people) {
                    people.toList()
                }

                @Mutate
                void addDependencyOnPeople(CollectionBuilder<Task> tasks, ManagedSet<Person> people) {
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#readPeople")
        failure.assertHasCause("Attempt to read a write only view of model of type 'org.gradle.model.collection.ManagedSet<Person>' given to rule 'RulePlugin#readPeople(org.gradle.model.collection.ManagedSet<Person>)'")
    }

    def "mutating a managed set that is an input of a rule is not allowed"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
            }

            @RuleSource
            class RulePlugin {
                @Model
                void people(ManagedSet<Person> people) {}

                @Mutate
                void tryToMutateInputManagedSet(CollectionBuilder<Task> tasks, ManagedSet<Person> people) {
                    people.create {}
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToMutateInputManagedSet")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.collection.ManagedSet<Person>' given to rule 'RulePlugin#tryToMutateInputManagedSet(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>, org.gradle.model.collection.ManagedSet<Person>)'")
    }

    def "mutating a managed set outside of a creation rule is not allowed"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
            }

            class Holder {
                static ManagedSet<Person> people
            }

            @RuleSource
            class RulePlugin {
                @Model
                void people(ManagedSet<Person> people) {
                    Holder.people = people
                }

                @Mutate
                void tryToMutateManagedSetOutsideOfCreationRule(CollectionBuilder<Task> tasks, ManagedSet<Person> people) {
                    Holder.people.create {}
                }
            }

            apply type: RulePlugin
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: RulePlugin#tryToMutateManagedSetOutsideOfCreationRule")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.collection.ManagedSet<Person>' given to rule 'RulePlugin#people(org.gradle.model.collection.ManagedSet<Person>)'")
    }

    def "mutating managed set which is an input of a dsl rule is not allowed"() {
        when:
        buildScript '''
            import org.gradle.model.*
            import org.gradle.model.collection.*

            @Managed
            interface Person {
            }

            @RuleSource
            class RulePlugin {
                @Model
                void people(ManagedSet<Person> people) {
                }
            }

            apply type: RulePlugin

            model {
                tasks {
                    $("people").create {}
                }
            }
        '''

        then:
        fails "tasks"

        and:
        failure.assertHasCause("Exception thrown while executing model rule: model.tasks")
        failure.assertHasCause("Attempt to mutate closed view of model of type 'org.gradle.model.collection.ManagedSet<Person>' given to rule 'model.tasks @ build file")
    }
}
