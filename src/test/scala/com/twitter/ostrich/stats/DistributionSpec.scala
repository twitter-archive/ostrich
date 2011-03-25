/*
 * Copyright 2011 Twitter, Inc.
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

package com.twitter.ostrich.stats

import org.specs.Specification

object DistributionSpec extends Specification {
  "Distribution" should {
    val histogram = Histogram(10, 20)

    "equals" in {
      Distribution(1, 10, 10, None, 10.0) mustEqual Distribution(1, 10, 10, None, 10.0)
      Distribution(1, 10, 10, None, 10.0) must not(beEqual(Distribution(2, 20, 10, None, 15.0)))
      Distribution(1, 10, 10, None, 10.0) mustEqual Distribution(1, 10, 10, Some(histogram), 10.0)
    }

    "toMap" in {
      Distribution(2, 20, 10, None, 15.0).toMap mustEqual
        Map("count" -> 2, "maximum" -> 20, "minimum" -> 10, "average" -> 15)
      Distribution(2, 20, 10, Some(histogram), 15.0).toMap mustEqual
        Map("count" -> 2, "maximum" -> 20, "minimum" -> 10, "average" -> 15,
            "p25" -> 10, "p50" -> 10, "p75" -> 20, "p90" -> 20, "p99" -> 20,
            "p999" -> 20, "p9999" -> 20)
    }

    "toJson" in {
      Distribution(2, 20, 10, None, 15.0).toJson mustEqual
        "{\"count\":2,\"maximum\":20,\"minimum\":10,\"average\":15}"
    }
  }
}
