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

package com.twitter

package object stats {
  // Counter.scala
  type Counter = com.twitter.ostrich.stats.Counter

  // Distribution.scala
  type Distribution = com.twitter.ostrich.stats.Distribution

  // Histogram.scala
  val Histogram = com.twitter.ostrich.stats.Histogram
  type Histogram = com.twitter.ostrich.stats.Histogram

  // JsonStatsLogger.scala
  type JsonStatsLogger = com.twitter.ostrich.stats.JsonStatsLogger

  // Metric.scala
  type Metric = com.twitter.ostrich.stats.Metric
  type FanoutMetric = com.twitter.ostrich.stats.FanoutMetric

  // Stats.scala
  val Stats = com.twitter.ostrich.stats.Stats

  // StatsCollection.scala
  type StatsCollection = com.twitter.ostrich.stats.StatsCollection
  val ThreadLocalStatsCollection = com.twitter.ostrich.stats.ThreadLocalStatsCollection
  type TransactionalStatsCollection = com.twitter.ostrich.stats.TransactionalStatsCollection

  // StatsListener.scala
  type StatsListener = com.twitter.ostrich.stats.StatsListener

  // StatsProvider.scala
  type StatsSummary = com.twitter.ostrich.stats.StatsSummary
  type StatsProvider = com.twitter.ostrich.stats.StatsProvider
  val DevNullStats = com.twitter.ostrich.stats.DevNullStats

  // W3CStats.scala
  type W3CStats = com.twitter.ostrich.stats.W3CStats

  // W3CStatsLogger.scala
  type W3CStatsLogger = com.twitter.ostrich.stats.W3CStatsLogger
}
