/*
 * Copyright 2010 Twitter, Inc.
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

package com.twitter.ostrich.admin

import com.twitter.io.TempFile
import com.twitter.ostrich.stats.Histogram
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite

@RunWith(classOf[JUnitRunner])
class RuntimeEnvironmentTest extends FunSuite {

  test("find executable jar path") {
    val runtime = new RuntimeEnvironment(classOf[Histogram])
    assert(runtime.findCandidateJar(List("./dist/flockdb/flockdb-1.4.1.jar"), "flockdb", "1.4.1") ===
    Some("./dist/flockdb/flockdb-1.4.1.jar"))
    assert(runtime.findCandidateJar(List("./dist/flockdb/flockdb_2.7.7-1.4.1.jar"), "flockdb", "1.4.1") ===
    Some("./dist/flockdb/flockdb_2.7.7-1.4.1.jar"))
    assert(runtime.findCandidateJar(List("./dist/flockdb/wrong-1.4.1.jar"), "flockdb", "1.4.1") ===
    None)
    assert(runtime.findCandidateJar(List("./dist/flockdb/flockdb-1.4.1-SNAPSHOT.jar"), "flockdb", "1.4.1-SNAPSHOT") ===
    Some("./dist/flockdb/flockdb-1.4.1-SNAPSHOT.jar"))
  }

  test("parse custom args") {
    val runtime = new RuntimeEnvironment(classOf[Object])
    assert(System.getProperty("foo") === null)
    runtime.parseArgs(List("-D", "foo=bar"))
    assert(runtime.arguments.get("foo") === Some("bar"))
    assert(System.getProperty("foo") === "bar")
    System.clearProperty("foo")  // allow this test to be run multiple times
  }

  test("load a config") {
    val config = TempFile.fromResourcePath("/config.scala").getAbsolutePath
    val runtime = new RuntimeEnvironment(classOf[Object])
    runtime.parseArgs(List("-f", config))
    val res: String = runtime.loadConfig()
    assert(res === "foo")
  }

}
