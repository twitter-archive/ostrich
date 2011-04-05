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

package com.twitter.ostrich
package admin

import org.specs.Specification
import stats.Histogram

class RuntimeEnvironmentSpec extends Specification {
  "RuntimeEnvironment" should {
    "find executable jar path" in {
      val runtime = new RuntimeEnvironment(classOf[Histogram])
      runtime.findCandidateJar(List("./dist/flockdb/flockdb-1.4.1.jar"), "flockdb", "1.4.1") mustEqual
        Some("./dist/flockdb/flockdb-1.4.1.jar")
      runtime.findCandidateJar(List("./dist/flockdb/flockdb_2.7.7-1.4.1.jar"), "flockdb", "1.4.1") mustEqual
        Some("./dist/flockdb/flockdb_2.7.7-1.4.1.jar")
      runtime.findCandidateJar(List("./dist/flockdb/wrong-1.4.1.jar"), "flockdb", "1.4.1") mustEqual
        None
      runtime.findCandidateJar(List("./dist/flockdb/flockdb-1.4.1-SNAPSHOT.jar"), "flockdb", "1.4.1-SNAPSHOT") mustEqual
        Some("./dist/flockdb/flockdb-1.4.1-SNAPSHOT.jar")
    }

    "parse custom args" in {
      val runtime = new RuntimeEnvironment(classOf[Object])
      runtime.parseArgs(List("-D", "foo", "bar"))
      runtime.arguments.get("foo") mustEqual Some("bar")
    }
  }
}
