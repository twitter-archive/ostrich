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

import com.twitter.ostrich.w3c.W3CLogFormat
import net.lag.logging.Logger

/**
 * Deprecated. Instantiate a LogReporter directly instead.
 */
class W3CReporter(logger: Logger, printCrc: Boolean) extends LogReporter(logger, new W3CLogFormat(printCrc)) {
  def this(logger: Logger) = this(logger, false)
}
