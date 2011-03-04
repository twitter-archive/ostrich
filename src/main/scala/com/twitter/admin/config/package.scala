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

package com.twitter.admin

package object config {
  // AdminServiceConfig.scala
  type StatsReporterConfig = com.twitter.ostrich.admin.config.StatsReporterConfig
  type JsonStatsLoggerConfig = com.twitter.ostrich.admin.config.JsonStatsLoggerConfig
  type W3CStatsLoggerConfig = com.twitter.ostrich.admin.config.W3CStatsLoggerConfig
  type TimeSeriesCollectorConfig = com.twitter.ostrich.admin.config.TimeSeriesCollectorConfig
  type StatsConfig = com.twitter.ostrich.admin.config.StatsConfig
  type AdminServiceConfig = com.twitter.ostrich.admin.config.AdminServiceConfig

  // ServerConfig.scala
  type ServerConfig[T <: Service] = com.twitter.ostrich.admin.config.ServerConfig[T]
}
