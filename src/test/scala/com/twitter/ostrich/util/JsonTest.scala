/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich.util

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite

@RunWith(classOf[JUnitRunner])
class JsonTest extends FunSuite {

  test("a map") {
    val map = Map(
      "keyA" -> "valueA",
      "keyB" -> "valueB"
    )

    val json = Json.build(map)
    val resultMap = Json.parse(json)
    val expectedJson = """{"keyA":"valueA","keyB":"valueB"}"""

    assert(map === resultMap)
    assert(json === expectedJson)
  }

  test("a object") {
    class Person(val name: String, val age: Int)
    val person = new Person("Daenerys Targaryen", 15)

    val json = Json.build(person)
    val resultMap = Json.parse(json)
    val expectedJson = """{"name":"Daenerys Targaryen","age":15}"""

    assert(json === expectedJson)
    assert(resultMap("name") === person.name)
    assert(resultMap("age") === person.age)
  }

}
