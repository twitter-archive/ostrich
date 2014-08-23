/*
 * Copyright 2009 Twitter, Inc.
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

import java.net.{Socket, SocketException, URL}
import scala.io.Source
import com.twitter.json.Json
import com.twitter.logging.{Level, Logger}
import org.junit.runner.RunWith
import org.mockito.Mockito.{verify, times}
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import stats.Stats

@RunWith(classOf[JUnitRunner])
class ServiceTrackerTest extends FunSuite with BeforeAndAfter with MockitoSugar {

  val service = mock[Service]

  before {
    ServiceTracker.clearForTests()
  }

  after {
    ServiceTracker.clearForTests()
  }

  test("shutdown") {
    ServiceTracker.register(service)
    ServiceTracker.shutdown()
    verify(service, times(1)).shutdown()
  }

  test("quiesce") {
    ServiceTracker.register(service)
    ServiceTracker.quiesce()
    verify(service, times(1)).quiesce()
  }

  test("reload") {
    ServiceTracker.register(service)
    ServiceTracker.reload()
    verify(service, times(1)).reload()
  }

}
