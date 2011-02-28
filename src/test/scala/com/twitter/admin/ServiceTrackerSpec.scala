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

package com.twitter.admin

import java.net.{Socket, SocketException, URL}
import scala.io.Source
import com.twitter.json.Json
import com.twitter.logging.{Level, Logger}
import com.twitter.stats.Stats
import org.specs.Specification
import org.specs.mock.JMocker

object ServiceTrackerSpec extends Specification with JMocker {
  "ServiceTracker" should {
    val service = mock[Service]

    doBefore {
      ServiceTracker.clearForTests()
    }

    doAfter {
      ServiceTracker.clearForTests()
    }

    "shutdown" in {
      ServiceTracker.register(service)
      expect { one(service).shutdown() }
      ServiceTracker.shutdown()
    }

    "quiesce" in {
      ServiceTracker.register(service)
      expect { one(service).quiesce() }
      ServiceTracker.quiesce()
    }

    "reload" in {
      ServiceTracker.register(service)
      expect { one(service).reload() }
      ServiceTracker.reload()
    }
  }
}
