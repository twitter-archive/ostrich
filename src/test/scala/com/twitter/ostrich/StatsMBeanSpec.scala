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

import java.lang.management.ManagementFactory
import javax.{management => jmx}
import scala.collection.{immutable, jcl}
import scala.util.Sorting
import net.lag.extensions._
import org.specs._


object StatsMBeanSpec extends Specification {
  "StatsMBean" should {
    val mbeanServer = ManagementFactory.getPlatformMBeanServer()

    doBefore {
      Stats.clearAll()
      StatsMBean("com.example.foo")
      Stats.incr("emo_tears")
      Stats.incr("clown_tears", 2)
    }

    "report stats" in {
      val mbeans = jcl.Set(mbeanServer.queryMBeans(new jmx.ObjectName("com.example.foo:*"), null))
      mbeans.size mustEqual 1
      val mbean = mbeans.toList.first
      val mbeanInfo = mbeanServer.getMBeanInfo(mbean.getObjectName())
      Sorting.stableSort(mbeanInfo.getAttributes().map { _.getName() }.toList).toList mustEqual
        List("counter_clown_tears", "counter_emo_tears")

      mbeanServer.getAttribute(mbean.getObjectName(), "counter_clown_tears") mustEqual 2
      mbeanServer.getAttribute(mbean.getObjectName(), "counter_emo_tears") mustEqual 1
    }
  }
}
